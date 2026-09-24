# `:onnx` — string → score ONNX models

A small module for running a Hugging Face **sequence-classification** model on
the phone without implementing its tokenizer in Kotlin.

The trick is on the export side: the tokenizer is converted to ONNX custom
operators with `onnxruntime-extensions` and merged into the model graph. What
ships to the device is a single `.onnx` file that takes a `tensor(string)` and
returns Float scores. `StringOnnxClassifier` then only has to feed it text.

```
   Python (Fedora)                          Android
   ─────────────────                        ───────
   HF model ─┐
             ├─ optimum ──> model.onnx ─┐
   tokenizer ┘                          ├─ onnx.compose ──> classifier.onnx
             └─ ort-extensions ────────┘        │
                                                 ▼
                                     StringOnnxClassifier(text) -> Float scores
```

## 1. Export on your PC

```bash
cd onnx/export
python -m venv .venv && . .venv/bin/activate
pip install "optimum[onnxruntime]" onnx onnxruntime onnxruntime-extensions transformers

python export_tokenizer_model.py \
  --model convaiinnovations/laya \
  --output build/laya.onnx \
  --labels build/laya.labels.json \
  --int8 \
  --verify
```

| flag | meaning |
|------|---------|
| `--model` | any public HF sequence-classification repo (`convaiinnovations/laya`, `convaiinnovations/laya-multilingual`, …) |
| `--output` | where the merged model is written |
| `--labels` | writes the class names as a JSON array, next to the model |
| `--int8` | also emits a dynamically quantised copy (`*.int8.onnx`) — much smaller, slightly less accurate |
| `--verify` | runs the exported file and diffs it against the `transformers` pipeline |
| `--opset` | ONNX opset for the transformer export (default 17) |
| `--trust-remote-code` | for repositories with custom modelling code |

The script prints the merged graph's input/output names, which should be exactly
one string input and one Float output:

```
merged graph input='text' output='logits'
```

Files larger than ~1.8 GB are written with an external `.data` companion —
**both files must be copied to the device together**. Exporting with `--int8`
avoids that entirely and is what a phone should use.

### Checking the merge without a full export

`verify_merge.py` exercises the part of the export that is easy to get wrong —
aligning the tokenizer graph's IR version and opset ids with the transformer's,
merging, pruning the extra tokenizer outputs and filling inputs the tokenizer
does not provide. It emulates both graphs by hand, so it needs neither torch nor
`onnxruntime-extensions`:

```bash
pip install onnx
python verify_merge.py
```

## 2. Use it from Kotlin

```kotlin
val scorer = StringOnnxClassifier
    .fromFiles(modelPath = "/data/.../laya.int8.onnx", labelsPath = "/data/.../laya.labels.json")
    .getOrThrow()

scorer.best("I would like a refund for my last invoice.")
// Prediction(label = "refund", score = 0.913f, index = 1)

scorer.ranked("...")   // every class, highest first
scorer.logits("...")   // raw logits, no softmax
scorer.close()
```

`onnxruntime-extensions-android` is registered automatically inside `open()`,
so the `BertTokenizer` node in the graph resolves without any extra setup. The
session is created lazily and reused; a session is expensive to build, so keep
one `StringOnnxClassifier` alive rather than creating it per call.

## 3. The Decision AI: `wfzyx/von`

[`wfzyx/von`](https://huggingface.co/wfzyx/von) is a ModernBERT-large decision
model: it reads "A against B" and answers with a probability in one pass (the
app's main screen turns that probability into Y / N / C). It is this module's
canonical model in the app (`VonDecisionModel`), because the chat runtime in
`:llm` cannot run an encoder with a classification head.

The Hugging Face repository ships **safetensors**, not a runnable graph, so the
graph has to come from the same export used for any other classifier:

```bash
python export_tokenizer_model.py \
  --model wfzyx/von \
  --output build/von-classifier.onnx \
  --labels build/von-classifier.labels.json \
  --int8 \
  --verify
```

Copy the result next to the app's downloaded Von files — the app looks for
`von-classifier.onnx`, `von-classifier.int8.onnx` or `model.onnx` in the same
directory as the weights (`/data/data/com.goldmedal.aillm/files/models/`), with
the optional `von-classifier.labels.json` beside it.

How the probability is read (`VonDecisionModel`):

| | |
|---|---|
| input | `"A [SEP] B"` — the screen's A line judged against B |
| head | `id2label` from `config.json` — 0 `entailment`, 1 `neutral`, 2 `contradiction` |
| probability | entailment ÷ (entailment + contradiction) — a strong neutral pulls it toward the C band |
| verdict | `≥ 51%` → Y, `≤ 49%` → N, between → C (Not Clear) |
| logits | scaled by `temperature` from the authors' `calibration.json` (1.1692) |

Without that ONNX file the app still answers, from `VonDecisionRules`
(negation / affirmation patterns) — and it labels the result as a fallback
instead of pretending the weights ran.

## Dependencies

| artifact | why |
|----------|-----|
| `com.microsoft.onnxruntime:onnxruntime-android:1.20.0` | the runtime |
| `com.microsoft.onnxruntime:onnxruntime-extensions-android:0.13.0` | the tokenizer custom operators |

Natives are limited to `arm64-v8a` and `x86_64`, matching `:llm`.

## Notes

- The merged graph deliberately stops at the logits: `scores()` applies the
  softmax, so nothing about the model's post-processing is baked in.
- If the exported model is unusable (missing file, wrong graph), `open()`
  returns a failed `Result` instead of throwing, so a caller can degrade the way
  the rest of the app does.
