#!/usr/bin/env python3
"""
Checks the merge in `export_tokenizer_model.py` without needing torch or the
full export.

`onnxruntime-extensions` only publishes wheels for some Python versions, and
`optimum`'s export pulls in torch, so neither is available on every machine.
Neither is needed to test the part that is easy to get wrong: aligning the
tokenizer graph's IR version and opset ids with the transformer's, merging them
with `onnx.compose`, pruning the extra tokenizer outputs, and filling any model
input the tokenizer does not provide.

The tokenizer graph is emulated with exactly the shape the real
`gen_processing_models` produces: one `ai.onnx.contrib` node, flat int64
outputs, and the IR/opset stamps `make_onnx_model` writes.

    pip install onnx
    python verify_merge.py
"""

from __future__ import annotations

import importlib.util
import sys
import tempfile
from pathlib import Path

import numpy as np
import onnx
from onnx import TensorProto, helper, numpy_helper

SCRIPT = Path(__file__).with_name("export_tokenizer_model.py")


def load_export_module():
    spec = importlib.util.spec_from_file_location("export_tokenizer_model", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    sys.modules["export_tokenizer_model"] = module
    spec.loader.exec_module(module)
    return module


def tokenizer_model(ort_opset: int = 18, with_token_type_ids: bool = True):
    """The graph `gen_processing_models(BertTokenizer)` builds."""
    names = ["input_ids"]
    if with_token_type_ids:
        names.append("token_type_ids")
    names += ["attention_mask", "offset_mapping"]
    outputs = [
        helper.make_tensor_value_info(
            name, TensorProto.INT64, [None, 2] if name == "offset_mapping" else [None]
        )
        for name in names
    ]
    node = helper.make_node(
        "BertTokenizer",
        inputs=["text"],
        outputs=names,
        name="BertTokenizer_1",
        domain="ai.onnx.contrib",
    )
    graph = helper.make_graph(
        [node],
        "og_BertTokenizer_1",
        [helper.make_tensor_value_info("text", TensorProto.STRING, [None])],
        outputs,
    )
    model = helper.make_model_gen_version(
        graph, opset_imports=[helper.make_operatorsetid("ai.onnx", ort_opset)]
    )
    model.opset_import.extend([helper.make_operatorsetid("ai.onnx.contrib", 1)])
    return model


def transformer_model(opset: int = 17, uses_token_type_ids: bool = True):
    """The graph an optimum text-classification export produces: ids/mask -> logits."""
    inputs = [
        helper.make_tensor_value_info("input_ids", TensorProto.INT64, ["batch", "seq"]),
        helper.make_tensor_value_info("attention_mask", TensorProto.INT64, ["batch", "seq"]),
    ]
    if uses_token_type_ids:
        inputs.append(
            helper.make_tensor_value_info("token_type_ids", TensorProto.INT64, ["batch", "seq"])
        )
    weights = numpy_helper.from_array(
        np.array([[0.5, -0.5]], dtype=np.float32), name="classifier.weight"
    )
    axes = numpy_helper.from_array(np.array([1], dtype=np.int64), name="unsqueeze.axes")
    nodes = [
        helper.make_node("Cast", ["input_ids"], ["ids_float"], to=TensorProto.FLOAT),
        helper.make_node("ReduceMean", ["ids_float"], ["pooled"], axes=[1], keepdims=0),
        helper.make_node("Unsqueeze", ["pooled", "unsqueeze.axes"], ["pooled_2d"]),
        helper.make_node("MatMul", ["pooled_2d", "classifier.weight"], ["logits"]),
    ]
    graph = helper.make_graph(
        nodes,
        "transformer",
        inputs,
        [helper.make_tensor_value_info("logits", TensorProto.FLOAT, ["batch", 2])],
        [weights, axes],
    )
    return helper.make_model_gen_version(
        graph, opset_imports=[helper.make_operatorsetid("ai.onnx", opset)]
    )


def run_case(name, ort_opset, tokenizer_has_types, model_uses_types):
    module = load_export_module()
    pre = tokenizer_model(ort_opset=ort_opset, with_token_type_ids=tokenizer_has_types)
    base = transformer_model(opset=17, uses_token_type_ids=model_uses_types)

    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "model.onnx"
        onnx.save_model(base, str(path))
        merged, text_input, logits = module.merge_graphs(pre, path)

    inputs = [(i.name, i.type.tensor_type.elem_type) for i in merged.graph.input]
    outputs = [(o.name, o.type.tensor_type.elem_type) for o in merged.graph.output]
    print(f"\n[{name}]")
    print("  merged opset :", [(e.domain, e.version) for e in merged.opset_import])
    print("  ir_version   :", merged.ir_version)
    print("  inputs       :", inputs)
    print("  outputs      :", outputs)

    assert len(inputs) == 1, f"expected a single input, got {inputs}"
    assert inputs[0][0] == text_input, f"input name changed: {inputs[0][0]} != {text_input}"
    assert inputs[0][1] == TensorProto.STRING, "the only input must be a string tensor"
    assert len(outputs) == 1, f"expected a single output, got {outputs}"
    assert outputs[0][0] == logits, f"output name changed: {outputs[0][0]} != {logits}"
    assert outputs[0][1] == TensorProto.FLOAT, "the only output must be Float"

    # A duplicated opset id per domain is what raw merge_models leaves behind.
    domains = [entry.domain for entry in merged.opset_import]
    assert len(domains) == len(set(domains)), f"duplicate opset domains: {domains}"

    onnx.checker.check_model(merged, full_check=True)

    produced = {i.name for i in merged.graph.input}
    produced |= {init.name for init in merged.graph.initializer}
    for node in merged.graph.node:
        produced |= set(node.output)
    for node in merged.graph.node:
        for value in node.input:
            assert value in produced, f"dangling node input {value!r} in {node.op_type}"
    print("  checks       : ok")


def main() -> None:
    print("onnx", onnx.__version__, "IR", onnx.IR_VERSION)
    run_case("opset 18 tokenizer vs opset 17 model", 18, True, True)
    run_case("opset 17 tokenizer (no alignment needed)", 17, True, True)
    run_case("zero-fill: tokenizer has no token_type_ids", 18, False, True)
    print("\nALL CASES PASSED")


if __name__ == "__main__":
    main()
