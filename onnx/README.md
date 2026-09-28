# `:onnx` — running Laya (a decision model) on ONNX Runtime

This module runs **`convaiinnovations/laya-multilingual`** on the phone.

Laya is not a chat model. A normal LLM would be asked "AはBか？" and would
*generate* `Y` / `N` / `C` as text, which is then parsed — and that is exactly
the wrong shape for it. Laya is a **non-autoregressive decision model**: it
tokenizes a structured prompt, reads the hidden state at one `[MASK]` **marker**
per answer option, and returns logits over those options. There is no text to
generate and no prose to parse; the app only maps the resulting probability to
Y / N / C.

It runs on its own ONNX Runtime. It is **never** sent through llama.cpp.

## The graph contract

An exported Laya graph has five inputs and returns the decision logits:

| input | type | meaning |
|---|---|---|
| `input_ids` | int64 `[batch, sequence]` | the prompt sequence |
| `attention_mask` | int64 `[batch, sequence]` | 1 for real tokens, 0 for padding |
| `marker_pos` | int64 `[batch, markers]` | index of each option's `[MASK]` |
| `marker_mask` | bool `[batch, markers]` | which markers are real |
| `qtype` | int64 `[batch]` | `0` choice, `1` score, `2` noul |

| output | type | meaning |
|---|---|---|
| `logits` | float32 `[batch, markers]` | one raw logit per option (pre-softmax) |
| `act_logits` | float32 `[batch, actions]` | the act/escalate head (unused here) |

`marker_pos` / `marker_mask` are the heart of it: Laya is *not* run as an
ordinary text classifier. Feeding only `input_ids` (a normal sequence
classification) produces the wrong answer.

## The prompt

`laya.common.build_sequence` builds:

```
[CLS] <type> question: <instructions> [SEP] [MASK] opt0 [MASK] opt1 … [SEP] <state> [SEP]
```

- `<type>` is `noul` for a Yes/No question; `question:` is literal.
- Each option is prefixed by its own `[MASK]`; the index of each `[MASK]` is
  recorded in `marker_pos`.
- `noul` always renders exactly two options, false then true:
  `false: no, the statement does not hold` and `true: yes, the statement holds`.
- The question head and its options share a fixed `head_max_len` budget; the
  state fills the rest up to `max_len`.

## Calibration

Do **not** treat raw logits as probabilities, and do **not** pin the temperature
to 1.0. The checkpoint's `rl_agent_config.json` carries a temperature per
`(question type, option count)` bucket. The logits are divided by it, then
softmaxed. The temperature is clamped to `[0.5, 5.0]` (a value below 1 would
sharpen the logits ~10× and turn a coin flip into a false certainty). For
`laya-multilingual` the shipped values are `[1.0, 1.0, 1.0]` — the model is
**uncalibrated**, so treat the probabilities with care.

For `noul`, `P(true)` is the **second** softmax entry (`p[1]`); the app reports
it and cuts the verdict:

```
P(true) >= 0.51  -> Y
P(true) <= 0.49  -> N
otherwise        -> C   (the app's "not clear", never model output)
```

## The Kotlin pieces

| file | role |
|---|---|
| `laya/JsonStream.kt` | single-pass JSON reader — the 34 MB `tokenizer.json` is never turned into a tree |
| `laya/LayaTokenizer.kt` | mmBERT BPE in pure Kotlin: normalizer, `Metaspace`, merges by rank, byte fallback |
| `laya/LayaConfig.kt` | parses `rl_agent_config.json`, applies the temperature buckets |
| `laya/LayaPrompt.kt` | builds the sequence and the `marker_pos` / `marker_mask` tensors |
| `laya/LayaOnnxRuntime.kt` | pads a batch and runs the five-input graph |
| `LayaDecisionModel.kt` | ties it together and returns `Answered` / `Failed` outcomes |

`LayaTokenizer` reads the real `tokenizer.json`: special token ids come from
`added_tokens` and the names in `tokenizer_config.json` (`<bos>` = `[CLS]`,
`<eos>` = `[SEP]`, `<mask>` = `[MASK]`). No token id is hard-coded.

## Files on the device

The downloader stores everything flat in `files/models/`. Laya needs:

```
model.onnx               the exported graph (main file)
tokenizer.json           the mmBERT tokenizer
tokenizer_config.json    special-token names
rl_agent_config.json     max_len / head_max_len / temperature
```

The upstream [`convaiinnovations/laya-multilingual`](https://huggingface.co/convaiinnovations/laya-multilingual)
repository ships **safetensors** rather than an executable graph, so the
catalogue entry points at a compatible ONNX export of the same Apache-2.0
weights. Any export with the five-input contract above works; drop those files
into the model directory and the app picks up `model.onnx`.

To produce one yourself, export the checkpoint with the reference runtime
(`laya-mlx export-onnx --dtype float16`) or use an existing mirror, then copy
`model.onnx`, `tokenizer.json`, `tokenizer_config.json` and `rl_agent_config.json`
together.

## Tests

`src/test` covers the streaming JSON reader, the calibration arithmetic, the BPE
tokenizer and the prompt builder.

The tokenizer and prompt tests compare against **golden vectors produced by the
reference `tokenizers` library on the real `tokenizer.json`**. Point
`LAYA_TOKENIZER` at the file (or place it at `/tmp/laya-tok.json`) to run them:

```bash
LAYA_TOKENIZER=/path/to/tokenizer.json ./gradlew :onnx:testDebugUnitTest
```

Without it they are skipped rather than failed, so the suite still runs in CI.

## Dependencies

| artifact | why |
|----------|-----|
| `com.microsoft.onnxruntime:onnxruntime-android:1.20.0` | the runtime |

No `onnxruntime-extensions` is needed: tokenization happens in Kotlin and the
graph is standard operators only.
