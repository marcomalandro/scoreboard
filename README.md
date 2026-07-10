# Scoreboard

<p align="center">
  <img src="pwa/icon-512.png" alt="Scoreboard icon" width="160" />
</p>

Two-player scoreboard with tap/swipe scoring, voice control ("tally blue point"), and a customizable win-target celebration. Ships in two forms:

- **`pwa/`** — a static single-page web app that installs on iOS/Android home screens via "Add to Home Screen." Deployed to GitHub Pages.
- **`android/`** — a native Android APK wrapper around the same HTML, plus a Java bridge to Android's `SpeechRecognizer` (more reliable than the browser's Web Speech API) and a MediaStore-backed voice log at `Documents/PingPong/pingpong-voice.log`.

## PWA

Live at `https://marcomalandro.github.io/scoreboard/pwa/` (once GitHub Pages is enabled on the `main` branch).

To iterate locally, just open `pwa/index.html` in any browser — no build step.

## Android

Built manually in Termux with `aapt2`, `d8`, `zipalign`, and `apksigner` (no Gradle). To rebuild:

1. Grab `android.jar` (API 33) — e.g. from `https://github.com/Sable/android-platforms/raw/master/android-33/android.jar` — and drop it in `android/`.
2. Generate icons if you changed the design: `cd android && python3 geniocon.py`.
3. Build steps: compile resources with `aapt2 compile`/`link`, `javac -source 1.8 -target 1.8`, `d8`, `zipalign`, sign with `apksigner` using a debug keystore.

## Author

[@marcomalandro](https://github.com/marcomalandro)
