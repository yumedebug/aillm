# AILLM — local-first on-device AI (release v1.5.0)

**Everything runs on your phone. Nothing leaves the device.**

## Install the APK
1. Open **Releases** and pick the latest `AILLM v*` release.
2. Download the attached **APK**.
3. On Android, allow **Install from unknown sources** for this app.
4. Open **AILLM**. The setup wizard reads your device and recommends the chat
   models that actually fit, then installs the one you choose in-app (no browser).

## What's new in this build
- **On-device image generation.** The image role is now real, not a placeholder:
  **Absolute Reality 1.81** (Lykon's photoreal SD 1.5 merge) runs through
  **stable-diffusion.cpp**, compiled from source by the new `:diffusion` module.
  It is the single image model — no menu, one honest choice — downloaded as a
  single `.safetensors` and loaded lazily on the first generation, quantized to
  Q8_0 so it fits a phone. Open it from **Models → Images → Generate**.
- **A second, much faster image model.** **DreamShaper 8 LCM** is an
  LCM-distilled SD 1.5 that lands a picture in **4-8 steps** instead of 25. The
  image screen switches between it and Absolute Reality, and each model brings
  its own sampler, guidance and step counts.
- **Generated pictures can leave the app.** Every result now has **Save**
  (into `Pictures/AILLM` via MediaStore — no storage permission on Android 10+)
  and **Share** (into any app through a FileProvider).
- **A gallery of past generations.** The image screen links to a **Gallery**
  where every finished generation is kept in the app's database (Room), so
  results survive restarts and can be reopened at any time.
- **Lighter and smarter chat models.** **LFM2 1.2B** (0.7 GB) gives strong
  replies at the lowest memory of anything in the library, and **Qwen3 1.7B**
  brings a reasoning-trained assistant into the light tier with thinking
  suppressed, so replies stay immediate.
- **LAYA is the main screen.** The app opens on the Decision AI: **State** as
  multiple lines (each line is its own state), **Question** as one line, and a
  **Question Type** picker (`Noul / Yes-No`). 判定する runs every state through
  **Laya Multilingual** — `東京都 → 日本のものか？` — in a single batched pass,
  filling the result list in (`Checking…` → `Y 98.7%`). The multilingual
  checkpoint covers 100+ languages, so Japanese is judged on the model's own
  terms.
- **Y / N / C verdict scale:** P(true) ≥ 51% → Y, ≤ 49% → N, in between →
  C (Not Clear). The probability is Laya's own; C is the app's band, not model
  output.
- **Laya loads at startup** (`Loading Laya…` → `Laya Ready`), fully on-device
  via ONNX Runtime; nothing is sent anywhere. 判定する stays disabled until
  Ready, and a load failure is explained in words.
- **Laya loads alone, never combined:** loading Laya unloads any resident chat
  model, and loading a chat model unloads Laya. No chat LLM (Qwen etc.) is ever
  co-loaded with Laya.
- **Rebuilt UI/UX and design system** — dark-first, minimal, a single restrained
  accent, and shared components instead of default Material cards everywhere.
  The surface layer is now a modern translucent panel (soft elevation + hairline
  edge over the blurred backdrop) rather than the earlier liquid-glass treatment.
- **Bottom navigation: Chat / History / Models / Settings.** Memory, Files and
  advanced settings are intentionally one level deeper.
- **Models are not bundled.** A fresh install with no models is a normal state;
  the chat screen invites you to choose one instead of failing.
- **Model lifecycle is explicit:** Not installed → Downloading → Verifying →
  Installed → Loading → Ready (or Error). "Installed" and "Ready" are distinct.
- **User-driven downloads** across three independent roles: Chat, Vision and
  Image generation. Resumable, cancellable, and verified before use.
- **Chat:** streaming replies, Markdown with copyable code blocks, message copy,
  regenerate, stop, image and document attachments, automatic conversation titles.
- **Decision AI (convaiinnovations/laya-multilingual):** a downloadable typed
  decision model, separate from chat, and not run through llama.cpp. It builds
  Laya's real prompt, tokenizes it in Kotlin, reads the logits at one `[MASK]`
  marker per option, applies the checkpoint's temperature calibration, and
  softmaxes to `P(true)` — never text generation and never a heuristic fallback.
  When a row cannot be judged, it shows `判定できませんでした` with **Retry**
  instead of a guess, and never `??`.
- **Web search through your browser:** ask for something current and the chat
  model turns the request into a query, which is handed to the device's browser.
  No search API, no key, no backend, and results are never fetched by the app —
  if the browser cannot be opened, the URL or the query is shown to tap or copy.
- **Memory:** a live, searchable, editable view of everything the assistant
  remembers.
- **Online sources** are **off by default** and hidden under
  Settings → Advanced. Users are never asked for API keys.

## Notes
- Chat runs on llama.cpp, vision on its libmtmd projector, decisions on ONNX
  Runtime, and image generation on stable-diffusion.cpp — all compiled from
  source by GitHub Actions and all on-device.
- Online lookup is not configured in this build and therefore stays inactive
  even when enabled.

## Requirements
- Android 8.0+ (API 26+)
- Storage for whichever models you choose (the smallest chat model is ~1 GB)

## Built & published by GitHub Actions
Tag `v*` → `release.yml` builds `assembleRelease` (and a debug fallback) and
attaches the APK to the GitHub Release. Debug builds land in the
`aillm-debug-apk` Action artifact.

Every push to `main` → `main-release.yml` builds the signed release APK and
republishes the rolling **`main-latest`** pre-release with that APK attached —
the tip of `main` is always installable from Releases without cutting a tag
(and also available as the `aillm-main-release-apk` artifact).
Without signing secrets the main-channel APK falls back to debug signing.
