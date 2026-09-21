# AILLM — local-first on-device AI (release v1.1.0)

**Everything runs on your phone. Nothing leaves the device.**

## Install the APK
1. Open **Releases** and pick the latest `AILLM v*` release.
2. Download the attached **APK**.
3. On Android, allow **Install from unknown sources** for this app.
4. Open **AILLM**. The setup wizard reads your device and recommends the chat
   models that actually fit, then installs the one you choose in-app (no browser).

## What's new in this build
- **Rebuilt UI/UX and design system** — dark-first, minimal, a single restrained
  accent, and shared components instead of default Material cards everywhere.
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
- **Memory:** a live, searchable, editable view of everything the assistant
  remembers.
- **Online sources** are **off by default** and hidden under
  Settings → Advanced. Users are never asked for API keys.

## Notes
- Inference currently runs through the stub engine; model management, state,
  storage and UI are fully implemented and the real engine drops in behind the
  same interfaces.
- Online lookup is not configured in this build and therefore stays inactive
  even when enabled.

## Requirements
- Android 8.0+ (API 26+)
- Storage for whichever models you choose (the smallest chat model is ~1 GB)

## Built & published by GitHub Actions
Tag `v*` → `release.yml` builds `assembleRelease` (and a debug fallback) and
attaches the APK to the GitHub Release. Debug builds land in the
`aillm-debug-apk` Action artifact.
