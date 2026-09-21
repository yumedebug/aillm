# AILLM — local-first on-device AI (release v1.0.1.0.2)

**Everything runs on your phone. Nothing leaves the device. No browser. No accounts.**

## Install the APK
1. Open **Releases** → **AILLM v1.0.1.0.2**
2. Download the attached **APK** (Assets below the release notes)
3. On Android: allow **Install from unknown sources** for this one app
4. Open **AILLM** → pick a category (Chat / Coding / Vision) → the **3 models best for THIS device** are shown → **one tap installs the model in-app** — no browser opens, downloads come straight from Hugging Face.

## What's in this build
- Dark-first design system (Material 3, device-tuned typography & shapes)
- Bottom navigation: **Chat / History / Models / Settings**
- First-run onboarding gate (local-only, zero network)
- **Device-aware model recommender**: reads your phone's real RAM, free storage and CPU cores, then recommends **Gemma / Qwen (HF GGUF) chat, coding and vision models sized for exactly your hardware** — one-click in-app install
- Everything stays on-device (GGUF via HF; installs inside the app)

## Requirements
- Android 8.0+ (API 26+)
- ~1.2 GB free storage for the small chat tier

## Built & published automatically by GitHub Actions
Tag `v1.0.1.0.2` → `release.yml` builds `assembleRelease` and attaches the signed APK to this Release. Debug builds land in the `aillm-debug-apk` Action artifact.
