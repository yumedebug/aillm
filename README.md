# AILLM
*常に最新版を入れましょう*

A local-first Android assistant with long-term memory that lives on your phone.
No model ships inside the APK: you choose what to download, and everything —
chat, memory, images, inference — stays on-device. Online sources are off
unless you explicitly turn them on.

## Product principles

1. **Chat is the hero.** The main screen is a conversation, not a settings panel.
2. **No model is assumed.** A fresh install with zero models is a normal, working
   state. The app never crashes or blocks because a model is missing.
3. **The user chooses models.** The setup wizard reads the real device (RAM, CPU,
   ABI, storage, GPU hints) and *recommends* three models for each of chat,
   coding and images — but nothing downloads without a tap.
4. **Installed ≠ Ready.** Install state (file on disk, verified) is tracked
   separately from load state (engine loaded, inference possible).
5. **One finished AI app**, not a bag of features. Settings are grouped and
   anything niche (performance, online sources, diagnostics) lives deep.

## Information architecture

Bottom navigation has exactly four destinations: **Chat · History · Models ·
Settings**. Memory, Files, Storage, Online sources and About are reached from
Settings (or contextually from Chat).

## Models

Four independent roles, loaded on demand and never all at once:

| Role | Purpose |
|------|---------|
| Chat | Conversation and reasoning |
| Coding | Writing, reviewing and explaining code |
| Vision | Image understanding (describe photos, screenshots, documents) |
| Image generation | Text-to-image |

Every model is fetched directly from a **public Hugging Face repository** that
has been checked to exist and to need no login. Chat and Coding share one
runtime, so only one of the two is resident at a time.

Models live in `ai/model/`: a catalogue (`ModelSpec`), a lifecycle state machine
(`ModelStatus`), a recommender (`ModelRecommender`) that ranks the library for
the actual device, and a `ModelRepository` that owns download, verification and
loading. Downloads are resumable and cancellable, and vision models pull their
`mmproj` projector in the same operation — only a fully verified set counts as
installed.

The chat library spans 0.5B to 7B, so a budget phone has something it can
truly run: **Qwen2.5 0.5B Instruct** (~0.5 GB) and **Gemma 3 1B IT** (~0.8 GB)
are the lightest entries, alongside Qwen2.5 1.5B/3B/7B, Gemma 2 2B, Llama 3.2
3B and Phi-3.5 Mini.

A model does not have to be re-loaded after the app is closed. The id of the
last model that finished loading is kept in `AppSettings`, and on the next
launch `ModelRepositoryImpl.restore()` loads it again — assuming it is still
installed. Deleting a model forgets it, so nothing is ever restored that is not
on disk.

The setup wizard shows three device-aware shortlists — **Chat, Coding and
Images** — three models each, and installs only what the user picks.

## Architecture

```
app/        Navigation, setup wizard, models & history screens, DI wiring
core/       Design system, preferences, device probe, Room database
ai/         Model catalogue/repository/downloader, prompts, engine interfaces
llm/        llama.cpp JNI bridge (C++ via NDK/CMake) + chat engine
memory/     Memory engine, extraction, semantic search, memory UI
search/     WebSearchClient abstraction + default-OFF online sources flag
chat/       Chat UI, streaming view model, message history
settings/   Grouped settings screens
files/      File import and text extraction
onnx/       String-in / score-out ONNX sequence classifiers
```

Tech: Kotlin, Jetpack Compose, Material 3 (with a custom design system), Hilt,
Room, DataStore, OkHttp, Coil, llama.cpp, ONNX Runtime.

## Design language

The app is one frosted-glass surface floating on a heavily blurred gradient.
Screens keep a **transparent** Scaffold container, so the ambient layer is what
every panel sits on; panels are separated from it by a hairline highlight rather
than a drop shadow.

On top of that sits a **liquid-glass** layer (`core/design/LiquidGlass.kt`) with
the two things that make glass read as glass rather than as a flat translucent
card:

- a specular sheen that travels across a panel, so the surface behaves like it
  is refracting a moving light source, and
- a springy, slightly overshooting press response instead of a Material ripple,
  so a touch displaces the surface.

`LiquidGlassSurface` (panels, history rows, model cards, settings groups),
`LiquidGlassFab` (the **+** on History and the **+** on Memory) and
`LiquidAppear` (fade-and-settle on entry) are the building blocks; the bottom
navigation is a single floating pill with four destinations whose selection
pill springs in. Everything is dependency-free: `Modifier.border` with a
gradient brush, springs from `animateFloatAsState`, and one `InfiniteTransition`
for the sheen — opt-in per surface, because an endless animation on every row
would be a waste of frames.

## Memory

Memory is separate from the model. Facts are extracted from the conversation,
stored in Room, and only the relevant ones are put in front of the model each
turn — the weights themselves are never fine-tuned.

- **Extraction** (`MemoryExtractorImpl`) picks up things people state about
themselves: OS, language, device, preferences (English and Japanese). Latin
keywords match on word boundaries, so "arch" no longer fires on "search"; the
stored value is the clause around the keyword, not a bare word.
- **Retrieval** (`SemanticSearch`) ranks memories against the current message by
  term overlap — word hits count for more than n-grams, and CJK terms also
  contribute character n-grams because a Japanese sentence has no spaces to
  split on. A few of the most important memories are always included, so the
  assistant keeps knowing who it is talking to. Retrieved memories get their
  `lastAccessedAt` bumped, which is what makes "recently used" ordering real.
- **Consolidation** (`MemoryConsolidator`) is what keeps memory from turning
  into a pile. A fact is never just inserted: saying the same thing again
  reinforces the existing memory (confidence rises, `updatedAt` moves) instead
  of adding a near-copy, matched on the same overlap measure retrieval uses. On
  top of that each category keeps only its few most recent statements
  (3 for OS/device/profile, 4 for languages, 8 for preferences). Sweeping
  happens automatically per write, and “Tidy up” in Settings → Memory merges
  what has already piled up. **Nothing is deleted by consolidation** — rows are
  archived (`isActive = 0`), the Archived filter shows them, and a memory can be
  restored with one tap.
- **Embeddings** are planned, not present: the embedding implementation refuses
  to load rather than returning random vectors, which would have made similarity
  search look like it worked.
- Memories are inspectable and editable in Settings → Memory, and from the
  conversation itself: the memory button in the chat bar opens a sheet with what
  the assistant currently knows, where a memory can be searched, corrected or
  forgotten without leaving the chat. The next reply already sees the change,
  because memories are read fresh for every turn.

## Inference

Chat runs on **llama.cpp**, compiled from source by the `:llm` module:

- `llm/src/main/cpp/CMakeLists.txt` fetches llama.cpp at a pinned release tag via
  `FetchContent` (nothing is vendored).
- `llama_bridge.cpp` exposes a small pull-based JNI API — `beginGeneration` /
  `nextToken` / `endGeneration` — which is what makes **token-by-token
  streaming** possible. A prebuilt AAR would have meant faking it.
- Kotlin formats each family's chat template (ChatML, Llama 3, Gemma, Phi-3)
  before handing the prompt over, so small instruct models get what they expect.
- ABIs are limited to `arm64-v8a` and `x86_64`; 32-bit devices are not supported.
- If the native library cannot load, the app degrades to a clear message instead
  of crashing.

### Vision

Image understanding runs on **libmtmd**, built as a library-only target
(`LLAMA_BUILD_MTMD=ON` with `LLAMA_BUILD_TOOLS=OFF`) so the whole tools tree is
skipped.

A multimodal model in llama.cpp is a text model plus a vision projector, and it
is modelled that way here: loading a vision model loads its GGUF as the resident
chat model and bolts the `mmproj` onto the same handle. So a loaded vision model
is also your chat model, and no second copy of the weights is kept in memory.
The image bytes go straight from memory into mtmd, the marker-aware prompt is
built with the family's chat template, and decoding streams token by token like
any other reply.

Image generation is still catalogued and downloadable but has no runtime yet;
the Models screen says so rather than offering a button that cannot work.

### Sequence classifiers (`:onnx`)

`llm/` covers generative models. A different job — "is this text a refund
request, a complaint, a complaint about a refund?" — is a *sequence
classification* model, and those run on ONNX Runtime instead.

Implementing a Hugging Face tokenizer in Kotlin is the part that makes this
painful, so it is not done at all: `onnx/export/export_tokenizer_model.py`
converts the tokenizer to ONNX custom operators with
`onnxruntime-extensions` and merges it into the model graph, producing a single
file whose input is `tensor(string)` and whose output is Float logits.
`StringOnnxClassifier` then feeds it text and returns scores, registering the
extensions library so the tokenizer node resolves. See [`onnx/README.md`](onnx/README.md)
for the export command and the Kotlin usage.

### Attachments stay in the conversation

A shared image or document stays part of the conversation. The picker only hands
out a transient `content://` URI, so the file is copied into app storage and the
message row keeps its name; later turns read the same file again *alongside the
current question*. That is what makes follow-ups ("what colour is it?", "what
did the second section say?") work without re-attaching anything, and the
message bubble shows the attachment back so it is clear it is still in context.

Documents are passed whole when short. When long, the model gets the opening
plus the passages that mention the current question, so a follow-up about
something far into a file is still answerable. Re-reading stops once an
attachment falls outside the recent window, and deleting a conversation deletes
its files.

## Build & release

Local builds are intentionally avoided. Everything is built by GitHub Actions:

- **CI** (`.github/workflows/android-ci.yml`) builds a debug APK on pushes to
  `main`.
- **Release** (`.github/workflows/release.yml`) is triggered by a `v*` tag and
  publishes the APK to GitHub Releases.

Both workflows install NDK `27.2.12479018` and CMake `3.22.1` and cache
`llm/.cxx`. Compiling llama.cpp for two ABIs is the slow part of the job, so
expect roughly 10–25 minutes on a cold cache.

## Privacy

Conversations, memories, images, files and inference are all on-device. Online
sources are disabled by default and, when enabled, results are fetched through a
developer-controlled backend — end users are never asked for an API key, and no
secret is stored in the APK.

## License

MIT — see [LICENSE](LICENSE).
