# Changelog

All notable changes to Shinigami Core are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
