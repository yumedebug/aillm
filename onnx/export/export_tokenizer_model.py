#!/usr/bin/env python3
"""
Export a Hugging Face *sequence-classification* model to ONE ONNX file whose
only input is a UTF-8 string and whose only output is Float scores.

Why do it this way: re-implementing a Hugging Face tokenizer in Kotlin is a
large, error-prone job (normalisation, special tokens, WordPiece/BPE/Unigram).
Instead the tokenizer is turned into ONNX custom operators with
`onnxruntime-extensions` and merged into the model graph, so the Android side
only needs an ONNX Runtime session and never owns a tokenizer.

Pipeline
--------
1. `optimum` exports the transformer itself    -> (input_ids, attention_mask) -> logits
2. `onnxruntime-extensions` builds the pre-processing graph
                                               -> (text, ...) -> input_ids, token_type_ids,
                                                                   attention_mask, offset_mapping
3. `onnx.compose` merges the two               -> (text) -> logits

Tested on Fedora with Python 3.11.

Setup
-----
    python -m venv .venv && . .venv/bin/activate
    pip install "optimum[onnxruntime]" onnx onnxruntime onnxruntime-extensions transformers

Run
---
    python export_tokenizer_model.py \\
        --model convaiinnovations/laya \\
        --output build/laya.onnx \\
        --labels build/laya.labels.json \\
        --int8 \\
        --verify

The result is a single `.onnx` file. Drop it on the device (for example in the
app's files directory) and hand its path to `StringOnnxClassifier` on Android.
"""

from __future__ import annotations

import argparse
import json
import shutil
import sys
import tempfile
from pathlib import Path
from typing import Iterable, List, Sequence

# 1.8 GB: protobuf cannot serialise a single message larger than 2 GB, so above
# this the weights are written as an external data file next to the model.
EXTERNAL_DATA_THRESHOLD = 1_800_000_000

# Names of the tokenizer tensors we know how to route into a transformer.
TOKENIZER_OUTPUT_NAMES = ("input_ids", "attention_mask", "token_type_ids")


def die(message: str) -> None:
    print(f"error: {message}", file=sys.stderr)
    raise SystemExit(1)


# --------------------------------------------------------------------------- #
# 1. Export the transformer with optimum
# --------------------------------------------------------------------------- #
def export_transformer(model_id: str, work_dir: Path, opset: int, trust_remote_code: bool) -> Path:
    from optimum.exporters.onnx import main_export

    work_dir.mkdir(parents=True, exist_ok=True)
    kwargs = {"trust_remote_code": True} if trust_remote_code else {}
    main_export(
        model_name_or_path=model_id,
        output=str(work_dir),
        task="text-classification",
        opset=opset,
        **kwargs,
    )
    candidates = sorted(work_dir.glob("*.onnx"))
    if not candidates:
        die(f"optimum produced no .onnx file in {work_dir}")
    return candidates[0]


# --------------------------------------------------------------------------- #
# 2. Build the tokenizer graph with onnxruntime-extensions
# --------------------------------------------------------------------------- #
def build_tokenizer_graph(model_id: str, trust_remote_code: bool):
    from onnxruntime_extensions import gen_processing_models
    from transformers import AutoTokenizer

    tokenizer = AutoTokenizer.from_pretrained(model_id, trust_remote_code=trust_remote_code)
    # pre_kwargs={} is enough for BERT-family tokenizers: onnxruntime-extensions
    # picks the right custom op (BertTokenizer, GPT2Tokenizer, ...) from the
    # tokenizer class, and the op already emits int64 ids.
    pre, _ = gen_processing_models(tokenizer, pre_kwargs={})
    if pre is None:
        die(
            f"onnxruntime-extensions cannot build a tokenizer graph for "
            f"{type(tokenizer).__name__}"
        )
    print(
        "tokenizer graph: "
        f"inputs={[i.name for i in pre.graph.input]} "
        f"outputs={[o.name for o in pre.graph.output]}"
    )
    return tokenizer, pre


# --------------------------------------------------------------------------- #
# 3. Merge both graphs
# --------------------------------------------------------------------------- #
def merge_graphs(tokenizer_graph, model_path: Path):
    import onnx
    from onnx import TensorProto, compose, helper

    model = onnx.load(str(model_path))

    # The tokenizer op emits flat [seq] tensors for a batch of one; a transformer
    # wants [batch, seq]. expand_out_dim inserts the missing Unsqueeze on every
    # tokenizer output at once.
    pre = compose.expand_out_dim(tokenizer_graph, dim_idx=0)

    text_input = pre.graph.input[0].name
    tokenizer_outputs = [o.name for o in pre.graph.output]
    model_inputs = [i.name for i in model.graph.input]

    io_map = [(name, name) for name in tokenizer_outputs if name in model_inputs]
    if not io_map:
        die(
            "the tokenizer graph and the model share no input names; "
            f"tokenizer={tokenizer_outputs} model={model_inputs}"
        )

    float_outputs = [
        o.name for o in model.graph.output
        if o.type.tensor_type.elem_type == TensorProto.FLOAT
    ]
    logits = "logits" if "logits" in float_outputs else (float_outputs[0] if float_outputs else None)
    if logits is None:
        die(f"the model has no Float output: {[o.name for o in model.graph.output]}")

    # No name prefixes: merge_models renames graph inputs and outputs when a
    # prefix is used, so the logits name the caller passes would stop matching.
    pre = _align_with_model(pre, model)

    try:
        merged = compose.merge_models(pre, model, io_map=io_map, outputs=[logits])
    except ValueError as error:
        die(f"onnx.compose could not merge the two graphs: {error}")

    merged = _resolve_leftover_inputs(merged, pre, io_map, text_input)
    merged = _prune_outputs(merged, logits)
    merged = _finalize_opset_imports(merged)

    # The graph contains ai.onnx.contrib operators that the plain ONNX schema
    # registry does not know, so a checker failure is only a warning here; the
    # real proof is the --verify run below.
    try:
        onnx.checker.check_model(merged)
    except Exception as error:  # noqa: BLE001 - surfacing the reason is the point
        print(f"warning: onnx.checker reported: {error}")

    return merged, text_input, logits


# The default ONNX domain may be spelled either way; ONNX Runtime accepts both,
# but onnx.compose compares the strings.
_DEFAULT_DOMAINS = ("", "ai.onnx")


def _align_with_model(pre, model):
    """
    Make the tokenizer graph mergeable with the exported transformer.

    `merge_models` refuses two models whose IR version or per-domain opset ids
    differ. onnxruntime-extensions stamps the tokenizer graph with the opset of
    the *installed* ONNX Runtime (18 on recent builds), which rarely equals the
    opset optimum exported with, so the tokenizer graph is brought onto the
    model's numbers. Only the declaration changes: that graph holds one
    custom-domain node plus an Unsqueeze, and Unsqueeze is identical in every
    opset from 13 up.
    """

    def key_of(domain: str) -> str:
        return "" if domain in _DEFAULT_DOMAINS else domain

    model_opsets = {key_of(entry.domain): entry.version for entry in model.opset_import}
    default_opset = model_opsets.get("")
    if default_opset is not None and default_opset < 13:
        die(
            f"the exported model declares opset {default_opset}; merging needs an "
            "opset 13 or newer so Unsqueeze can take its axes as an input. "
            "Re-run with --opset 13 (or newer)."
        )

    default_spelling = next(
        (entry.domain for entry in model.opset_import if entry.domain in _DEFAULT_DOMAINS),
        "",
    )
    for entry in pre.opset_import:
        key = key_of(entry.domain)
        if key in model_opsets:
            entry.version = model_opsets[key]
            if key == "":
                entry.domain = default_spelling

    pre.ir_version = model.ir_version
    return pre


def _resolve_leftover_inputs(merged, pre, io_map, text_input):
    """
    A model may want an input the tokenizer does not produce (for example
    `token_type_ids` on a RoBERTa-style graph). Those are filled with zeros
    shaped like `input_ids`, so the merged graph really does take only text.
    """
    from onnx import helper

    produced = {dst for _, dst in io_map}
    tokenizer_outputs = [o.name for o in pre.graph.output]
    source = "input_ids" if "input_ids" in tokenizer_outputs else tokenizer_outputs[0]

    leftovers = [i.name for i in merged.graph.input if i.name != text_input and i.name not in produced]
    for name in leftovers:
        first_consumer = next((i for i, n in enumerate(merged.graph.node) if name in n.input), None)
        if first_consumer is None:
            for info in [i for i in merged.graph.input if i.name == name]:
                merged.graph.input.remove(info)
            continue

        zeros = f"{name}__zeros"
        # Inserted before its first reader so the graph stays in topological order.
        merged.graph.node.insert(
            first_consumer,
            helper.make_node("Sub", [source, source], [zeros], name=f"zeros_{name}"),
        )
        for node in merged.graph.node:
            for index, input_name in enumerate(node.input):
                if input_name == name:
                    node.input[index] = zeros
        for info in [i for i in merged.graph.input if i.name == name]:
            merged.graph.input.remove(info)
        print(f"filled unused model input '{name}' with zeros")

    return merged


def _prune_outputs(merged, keep: str):
    for info in list(merged.graph.output):
        if info.name != keep:
            merged.graph.output.remove(info)
    return merged


def _finalize_opset_imports(merged):
    """Guarantees the custom domain is declared, exactly once per domain."""
    from onnx import helper

    try:
        from onnxruntime_extensions import default_opset_domain

        domain = default_opset_domain()
    except ImportError:  # the extensions package is only needed to build the tokenizer graph
        domain = "ai.onnx.contrib"

    if not any(op.domain == domain for op in merged.opset_import):
        merged.opset_import.append(helper.make_opsetid(domain, 1))

    # merge_models concatenates both models' opset lists, so every domain they
    # had in common appears twice. ONNX Runtime tolerates that, but one entry per
    # domain is what the format intends and what stricter validators expect.
    versions: dict[str, int] = {}
    for entry in merged.opset_import:
        versions[entry.domain] = max(entry.version, versions.get(entry.domain, 0))
    del merged.opset_import[:]
    for domain_name, version in versions.items():
        merged.opset_import.append(helper.make_opsetid(domain_name, version))

    merged.graph.name = "tokenizer_classifier"
    return merged


# --------------------------------------------------------------------------- #
# 4. Save / quantise
# --------------------------------------------------------------------------- #
def save_model(model, path: Path) -> Path:
    import onnx

    path.parent.mkdir(parents=True, exist_ok=True)
    if len(model.SerializeToString()) > EXTERNAL_DATA_THRESHOLD:
        print(
            "model is larger than ~1.8 GB; writing external data next to it "
            "(both files must ship together)"
        )
        onnx.save_model(
            model,
            str(path),
            save_as_external_data=True,
            all_tensors_to_one_file=True,
            location=f"{path.name}.data",
            size_threshold=1024,
        )
    else:
        onnx.save_model(model, str(path))
    return path


def quantize_dynamic_int8(model_path: Path) -> Path:
    from onnxruntime.quantization import QuantType, quantize_dynamic

    quantized = model_path.with_name(f"{model_path.stem}.int8{model_path.suffix}")
    quantize_dynamic(
        model_input=str(model_path),
        model_output=str(quantized),
        weight_type=QuantType.QInt8,
    )
    print(f"quantised weights written to {quantized}")
    return quantized


def write_labels(model_id: str, labels_path: Path, trust_remote_code: bool) -> List[str]:
    from transformers import AutoConfig

    labels: List[str] = []
    try:
        config = AutoConfig.from_pretrained(model_id, trust_remote_code=trust_remote_code)
        id2label = getattr(config, "id2label", None) or {}
        mapping = {int(k): v for k, v in id2label.items()}
        if mapping:
            labels = [mapping[i] for i in sorted(mapping)]
    except Exception as error:  # a missing config must not fail the export
        print(f"warning: could not read labels from the model config ({error})")

    if labels:
        labels_path.parent.mkdir(parents=True, exist_ok=True)
        labels_path.write_text(json.dumps(labels, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"labels written to {labels_path}: {labels}")
    return labels


# --------------------------------------------------------------------------- #
# 5. Verify against the reference model
# --------------------------------------------------------------------------- #
def _softmax(values: Sequence[float]) -> List[float]:
    import math

    if not len(values):
        return []
    largest = max(values)
    exps = [math.exp(v - largest) for v in values]
    total = sum(exps)
    return [e / total for e in exps]


def verify(model_path: Path, model_id: str, texts: Iterable[str], trust_remote_code: bool) -> None:
    import numpy as np
    import onnxruntime as ort
    from onnxruntime_extensions import get_library_path
    from transformers import pipeline

    options = ort.SessionOptions()
    # Same registration the Android session does; without it the BertTokenizer
    # node cannot be resolved.
    options.register_custom_ops_library(get_library_path())

    session = ort.InferenceSession(str(model_path), options, providers=["CPUExecutionProvider"])
    inputs = session.get_inputs()
    if len(inputs) != 1:
        die(f"expected exactly one input, found {[(i.name, i.type) for i in inputs]}")
    if "string" not in inputs[0].type:
        die(f"expected a string input, found {inputs[0].type}")
    print(f"verified graph input: {inputs[0].name} : {inputs[0].type}")

    reference = pipeline(
        "text-classification",
        model=model_id,
        tokenizer=model_id,
        top_k=None,
        trust_remote_code=trust_remote_code,
    )

    for text in texts:
        logits = np.asarray(session.run(None, {inputs[0].name: np.array([text])})[0])[0]
        probabilities = _softmax(logits.tolist())
        onnx_best = int(np.argmax(np.asarray(probabilities)))

        expected = max(reference(text)[0], key=lambda item: item["score"])
        print(
            f"  {text[:48]!r}\n"
            f"    onnx      -> class {onnx_best} ({probabilities[onnx_best]:.4f})\n"
            f"    reference -> {expected['label']} ({expected['score']:.4f})"
        )


# --------------------------------------------------------------------------- #
# main
# --------------------------------------------------------------------------- #
def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--model", default="convaiinnovations/laya", help="Hugging Face model id")
    parser.add_argument("--output", default="build/classifier.onnx", help="where the merged model is written")
    parser.add_argument("--labels", default=None, help="where to write the class names as JSON")
    parser.add_argument("--opset", type=int, default=17, help="ONNX opset for the transformer export")
    parser.add_argument("--int8", action="store_true", help="also emit a dynamically quantised model")
    parser.add_argument("--verify", action="store_true", help="compare the export with the reference model")
    parser.add_argument(
        "--text",
        action="append",
        default=None,
        help="sample text for --verify (repeatable)",
    )
    parser.add_argument(
        "--trust-remote-code",
        action="store_true",
        help="allow custom modelling code from the model repository",
    )
    parser.add_argument(
        "--keep-work-dir",
        action="store_true",
        help="keep the intermediate optimum export instead of deleting it",
    )
    args = parser.parse_args()

    output = Path(args.output)
    work_dir = Path(tempfile.mkdtemp(prefix="onnx-export-"))

    try:
        print(f"1/4 exporting {args.model} with optimum (opset {args.opset})")
        model_path = export_transformer(args.model, work_dir, args.opset, args.trust_remote_code)

        print("2/4 building the tokenizer graph with onnxruntime-extensions")
        _, tokenizer_graph = build_tokenizer_graph(args.model, args.trust_remote_code)

        print("3/4 merging into a single string-in / float-out model")
        merged, text_input, logits = merge_graphs(tokenizer_graph, model_path)
        print(f"    merged graph input={text_input!r} output={logits!r}")

        save_model(merged, output)
        print(f"4/4 wrote {output} ({output.stat().st_size / 1e6:.1f} MB)")
        print("    single input  : tensor(string)")
        print("    single output : Float scores (no softmax applied)")

        if args.labels:
            write_labels(args.model, Path(args.labels), args.trust_remote_code)

        final_model = output
        if args.int8:
            final_model = quantize_dynamic_int8(output)

        if args.verify:
            texts = args.text or [
                "I would like a refund for my last invoice.",
                "The weather in Osaka is lovely this time of year.",
            ]
            verify(final_model, args.model, texts, args.trust_remote_code)
    finally:
        if args.keep_work_dir:
            print(f"intermediate files kept in {work_dir}")
        else:
            shutil.rmtree(work_dir, ignore_errors=True)


if __name__ == "__main__":
    main()
