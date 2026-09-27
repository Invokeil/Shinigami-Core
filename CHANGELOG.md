# Changelog

All notable changes to Shinigami Core are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] - 2026-09-27

The "Hey Shini" release — the assistant becomes a transient system layer.

### Fixed

- **INTERNET permission added** — v0.1.0 shipped without it, so adding a provider failed with `SecurityException: Permission Denied (missing INTERNET permission)`. Test Connection, Fetch Models and streaming now work.
- **"Set alarm at 7 AM" now sets the alarm directly** — the manifest now declares `com.android.alarm.permission.SET_ALARM`, so the system clock honors `EXTRA_SKIP_UI` instead of falling back to opening the clock app. Regression-tested with the exact phrasing.

### Added

- **Assistant overlay ("Hey Shini" experience)** — a Google Assistant/Gemini-style transient overlay that appears above the current app and never opens the full application: VoiceInteractionSession hosting a Compose overlay (official assistant API path) plus a transparent trampoline for the software wake-word path. Full state machine (Activating / Listening / Transcribing / Thinking / Responding / Executing / ConfirmationRequired / Error), live partial transcript, mic-amplitude reactive orb (local only — amplitude never leaves the device), follow-up conversation window (5/8/10 s), no-speech timeout, swipe-down/back/X dismiss, emergency STOP during actions, in-overlay confirmations, text input, "Continue Reading" expansion, tablet width caps and bottom-center placement with navigation-bar insets.
- **Shinigami orb animation** — a purpose-built 8 KB Lottie (crimson/violet energy orb, seamless 2 s loop) reused by the overlay, assistant screen and loading states, with quality tiers (Full / Battery-saver static vector) honoring the system reduced-motion setting.
- **Routines & automation** — visual editor chaining tool steps (manual, spoken-phrase or time triggers). Every step still flows through the Tool Registry → Policy → Confirmation pipeline; nested routines are refused.
- **Screen automation (accessibility)** — on-demand tap/scroll/back gestures and screen reading, gated behind Enhanced/Full Control modes, with hard refusal inside Protected Apps.
- **Screen context** — "explain this screen" captures visible text on demand (accessibility tree only, no screenshots, 60 s freshness budget) with an overlay indicator; never captured by merely waking Shini.
- **Shizuku support (optional)** — whitelisted shell operations only (exact media volume; app list), never AI-generated shell strings; explicit permission flow.
- **In-app update checker** — polls GitHub Releases with ETag-cached requests, renders the changelog in a non-blocking bottom sheet, and installs updates through a PackageInstaller session after verifying the APK SHA-256 against GitHub's published digest. Update now / Remind me (48 h) / Skip this version.
- **Adaptive components system** — the app profiles the device (ABI, API level, RAM tier, OEM ROM, form factor, mic/speech availability, power-save) and can fetch optional data-only components (models, voices) matched to that profile from GitHub Releases: size + SHA-256 verified, atomic install into `noBackupFilesDir`, fully offline-safe, never any dynamic code.
- **Play Protect hardening** — PROVENANCE.md publishes the permanent signing-certificate fingerprints and verification steps; targetSdk 35; no QUERY_ALL_PACKAGES; in-app "unknown developer" explanation.

## [0.1.0] - 2026-09-27

First public release. A privacy-first, bring-your-own-key AI assistant for Android.

### Added

- **Bring-your-own-key AI providers** — connect OpenAI, Google Gemini, Mistral, GLM/Z.AI, Ollama (local), or any OpenAI-compatible endpoint with a custom Base URL. Multiple provider profiles with per-profile model, temperature, max tokens, custom instructions, streaming toggle, and cleartext opt-in; auth options: none, bearer, API-key header (custom header name), HTTP Basic; extra headers; Test Connection and Fetch Models; streaming replies with stop control.
- **Deterministic offline command parser** — everyday device commands with zero network: open apps, alarms, timers, flashlight, volume, media transport, battery/device status, settings screens, web search, YouTube/Google search, navigation, call/dial, SMS drafts, time and date answers. Unit tested; no second, unguarded execution path.
- **Custom phrase commands** — bind any user-defined phrase to any tool with fixed arguments.
- **Hardened action pipeline** — the AI returns a structured JSON envelope (`{"reply", "actions"}`) and can only request tools from a typed registry of 27 built-in tools with validated arguments (open_app, set_alarm, set_timer, media controls, volume, flashlight, battery_status, device_info, open_settings, dnd_set, brightness_set, contact_lookup, dial_number, call_contact, sms_compose, read/dismiss/reply_notification, web_search, open_url, navigate, app_info). No raw shell commands; bounded agent loop (max 3 provider rounds, max 8 actions per request); emergency stop.
- **ActionPolicyEngine** — risk levels (LOW/MEDIUM/HIGH/CRITICAL), permission-mode gating (Standard/Enhanced/Full Control), runtime permission and special-access checks, and confirmation layering: global policy (always/sensitive/minimal), per-tool always-confirm (SMS compose, notification reply), and optional BiometricPrompt for high-risk tools. Every action attempt is recorded in a local audit log viewer.
- **Permission modes with explicit consent** — Full Control unlocks notification tools and DND and requires a dedicated warning screen with an "I understand the risks" checkbox.
- **Permission Dashboard** — a card per capability (microphone, notifications, default assistant, notification access, contacts, phone, calendar, modify system settings, DND access, battery optimisation, alarms & reminders) with plain-language purpose, live state, and the exact grant path; just-in-time permission requests.
- **Voice** — optional "Hey Shini" wake phrase (off by default) via a foreground microphone service; Android SpeechRecognizer preferring on-device recognition; TextToSpeech with speed/pitch controls; barge-in interrupt; optional experimental same-voice check via VoicePrint pitch/energy profile — a heuristic, explicitly not biometric authentication.
- **System assistant integration** — can be set as the Android Default Assistant (VoiceInteractionService); Quick Settings tile included.
- **Privacy architecture** — API keys and passwords encrypted with Android Keystore AES-GCM (SecretVault) and excluded from backups; secrets redacted from all logs (Redactor); notification payloads held in an in-memory ring only; incognito/private sessions; optional assistant memory off by default; history retention controls; no account, no analytics, no crash reporting, no telemetry of any kind.
- **Local storage** — Room database (8 entities: provider profiles, conversations, messages, audit events, usage records, custom commands, memory entries, protected apps) and DataStore preferences, all app-private.
- **Interface** — Jetpack Compose + Material 3; dark "Dark Reaper" theme, light theme, system, and dynamic color (Material You); bundled Nunito font (SIL OFL 1.1); SVG vector icons; reduce-animations setting.
- **Platform** — Kotlin 2.0, Hilt dependency injection, OkHttp + kotlinx.serialization, WorkManager; Android 10 (API 29) minimum, targetSdk 34.
- **Project infrastructure** — GitHub Actions CI (build, unit tests, lint on every push/PR to main with debug APK artifact) and signed release workflow on version tags, publishing APKs to GitHub Releases.

[0.1.0]: https://github.com/Invokeil/Shinigami-Core/releases/tag/v0.1.0
