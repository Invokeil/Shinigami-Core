<div align="center">

# SHINIGAMI — by Invokeil

**A privacy-first AI assistant for Android. You choose the brain.**

[![Build](https://github.com/Invokeil/Shinigami-Core/actions/workflows/build.yml/badge.svg)](https://github.com/Invokeil/Shinigami-Core/actions/workflows/build.yml)
[![Release](https://img.shields.io/badge/release-0.1.0-blue)](https://github.com/Invokeil/Shinigami-Core/releases)
[![License](https://img.shields.io/badge/license-Apache_2.0-green)](LICENSE)
[![Min Android](https://img.shields.io/badge/Android-10%2B-3ddc84)](https://developer.android.com/about/versions/10)

</div>

---

Shinigami Core is a Google-Assistant-style assistant for Android with one crucial difference: **you decide which AI powers it.** Bring your own key from OpenAI, Google Gemini, Mistral, GLM/Z.AI, run models fully offline with Ollama, or point the app at any OpenAI-compatible endpoint. There is no account, no telemetry, no mandatory cloud, and no vendor lock-in.

Everyday device commands — opening apps, setting alarms, timers, flashlight, volume, media playback, web searches, navigation — are handled **fully offline** by a deterministic command parser. When you do send something to your AI provider, every action the model requests passes through a hardened, policy-driven action pipeline before Android does anything. The AI never touches Android directly.

```
                 ┌───────────────────────────────────────┐
                 │              User input               │
                 │    voice / text / assistant / tile    │
                 └──────────────────┬────────────────────┘
                                    │
                 ┌──────────────────▼────────────────────┐
                 │          OfflineCommandParser         │  deterministic, no network
                 │   "open spotify", "set an alarm ..."  │
                 └──────────────────┬────────────────────┘
                                    │ no offline match
                 ┌──────────────────▼────────────────────┐
                 │        Your AI provider (BYOK)        │  OpenAI, Gemini, Mistral,
                 │                                       │  GLM, Ollama, any endpoint
                 └──────────────────┬────────────────────┘
                                    │  JSON envelope:
                                    │  {"reply": "...", "actions": [...]}
                 ┌──────────────────▼────────────────────┐
                 │            ToolRegistry               │  typed schemas, validation
                 └──────────────────┬────────────────────┘
                                    │
                 ┌──────────────────▼────────────────────┐
                 │          ActionPolicyEngine           │  mode, permissions, risk
                 └──────────────────┬────────────────────┘
                                    │
                 ┌──────────────────▼────────────────────┐
                 │     Confirmation (when required)      │  policies, per-tool, biometric
                 └──────────────────┬────────────────────┘
                                    │
                 ┌──────────────────▼────────────────────┐
                 │            ActionEngine               │  executors perform the action
                 └──────────────────┬────────────────────┘
                                    │
                        result → audit log → fed back to the model → final reply
```

**The key idea:** the AI is treated as untrusted. It can only *request* tools from a fixed, typed registry of 27 capabilities. A policy engine checks your permission mode, runtime permissions, and the tool's risk level, and asks you to confirm when the stakes warrant it. There are no raw shell commands anywhere in the codebase, and there never will be.

---

## Features

### Bring your own intelligence
- **Choose your provider:** OpenAI, Google Gemini, Mistral, GLM/Z.AI, Ollama (local), or any OpenAI-compatible server — custom Base URL, custom auth, extra headers.
- **Bring your own key:** API keys are encrypted with the Android Keystore (AES-GCM) and never leave your device except to the provider you chose.
- **Multiple profiles:** keep several provider configurations and switch the active one at any time. Test Connection and Fetch Models are built in.
- **Streaming replies** with a visible stop control and barge-in voice interrupt.

### Works offline for the everyday stuff
- Deterministic **OfflineCommandParser** handles open app, alarms, timers, flashlight, volume, media transport, battery/device status, settings screens, web search, navigation, call/dial, and more — zero network, zero tokens, unit tested.
- **Custom phrase commands:** map any phrase you like to any tool with fixed arguments.

### Voice
- Optional wake phrase **"Hey Shini"** (off by default) via a foreground microphone service.
- On-device speech recognition (prefers on-device engine), text-to-speech with adjustable speed and pitch.
- Optional, **experimental** same-voice check (VoicePrint pitch/energy heuristic). This is explicitly *not* biometric authentication — it is a coarse filter that reduces accidental triggers.
- Can be set as the **system Default Assistant** (long-press home, power-press gesture), plus a Quick Settings tile.

### Control and safety
- Three **permission modes** — Standard, Enhanced, Full Control — plus a confirmation policy (always / sensitive / minimal), per-tool always-confirm, and an optional BiometricPrompt for high-risk actions.
- **Full Control mode** requires a dedicated warning screen with an "I understand the risks" checkbox.
- Every action is written to a local **audit log**. An emergency stop cancels the agent loop immediately.

### Privacy
- No account. No analytics. No crash-reporting service. No telemetry of any kind.
- Secrets encrypted with Android Keystore and excluded from backups; **redactor** scrubs credentials from all logs.
- Notification text lives in memory only, never on disk.
- Incognito/private sessions; optional assistant memory is **off by default**; history retention is your choice.

### Interface
- Modern **Jetpack Compose / Material 3** UI, dark "Dark Reaper" theme, light theme, system, and dynamic color (Material You).
- Bundled **Nunito** font (SIL Open Font License 1.1), crisp SVG vector icons, reduce-animations setting.

---

## Screenshots

> Screenshots will be added here as the UI stabilises. The app ships with a dark "Dark Reaper" theme and an optional light/Material You theme.

| Home / Assistant | Providers | Permission Dashboard |
| ---------------- | --------- | -------------------- |
| *placeholder*    | *placeholder* | *placeholder*    |

---

## Installation

1. Go to the [Releases page](https://github.com/Invokeil/Shinigami-Core/releases).
2. Download the latest signed APK (`app-release.apk` attached to the release).
3. Install it. Android will ask you to allow installs from your browser or file manager once.
4. On first launch the onboarding walks you through: pick a provider, enter a key (or set up local Ollama), and choose which permissions to grant.

**Requirements:** Android 10 (API 29) or newer. Nothing else — no account, no Google sign-in.

<details>
<summary><strong>Building from source</strong></summary>

You need JDK 17+ and the Android SDK (API 35). See [BUILDING.md](BUILDING.md) for the full guide.

```bash
git clone https://github.com/Invokeil/Shinigami-Core.git
cd Shinigami-Core
./gradlew assembleDebug        # debug APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit tests
./gradlew lint                 # Android lint
```

</details>

---

## Choosing your AI provider

Shinigami talks to whichever provider you configure. Pick one, grab a key, paste it in Settings → Providers → Add provider.

| Provider | Get a key | Default Base URL | Notes |
| --- | --- | --- | --- |
| **OpenAI** | [platform.openai.com](https://platform.openai.com/api-keys) | `https://api.openai.com/v1` | An OpenAI **API** key, not a ChatGPT login |
| **Google Gemini** | [aistudio.google.com](https://aistudio.google.com/apikey) | `https://generativelanguage.googleapis.com/v1beta` | Free tier available |
| **Mistral** | [console.mistral.ai](https://console.mistral.ai/api-keys/) | `https://api.mistral.ai/v1` | OpenAI-compatible |
| **GLM / Z.AI** | [z.ai](https://z.ai) / open.bigmodel.cn | `https://api.z.ai/api/paas/v4` | GLM family, OpenAI-compatible |
| **Ollama (local)** | no key needed | `http://localhost:11434/v1` | Fully local, fully offline |
| **Custom** | depends on the service | your Base URL | Any server exposing `/chat/completions` |

Detailed per-provider walkthroughs, authentication options (none / bearer / API-key header / basic), header overrides, model naming, and a troubleshooting table live in [PROVIDERS.md](PROVIDERS.md).

**Fully local with Ollama:** install [Ollama](https://ollama.com), pull a model (`ollama pull llama3.2`), add an Ollama provider in Shinigami, and you have an assistant that never sends a byte to the internet for its thinking. If Ollama runs on another machine on your LAN, note that HTTP traffic on a LAN is cleartext — Shinigami will ask you to explicitly allow cleartext for that profile.

---

## Permissions and the assistant role

Shinigami uses a **Permission Dashboard** with a card for every capability: what it is for, whether it is on, and exactly where to grant it. Permissions are requested **just-in-time**, when a feature needs them — never all at once up front. Without a given permission the related features degrade gracefully (for example, without Phone, "call Mom" opens the dialer prefilled instead of calling). See [PERMISSIONS.md](PERMISSIONS.md) for the complete card-by-card reference.

Setting Shinigami as the **Default Assistant** is optional and enables system integration: long-press home (or the power button, depending on vendor) to summon it.

---

## Security: what you should know

This section is short on purpose. The full model is documented in [SECURITY.md](SECURITY.md), the architecture in [ARCHITECTURE.md](ARCHITECTURE.md), and the data story in [PRIVACY.md](PRIVACY.md).

- **Grant only what you need.** Every permission is optional. Standard mode keeps the riskiest tools locked; you opt in to more.
- **Providers receive what is needed to process your request.** Your message, relevant conversation context, and the tool manifest (tool names, descriptions, parameter schemas). The AI provider never receives your API keys, your contacts database, or your notifications — only what the model must see to respond. If a model decides it needs a contact's number to place a call, the lookup happens **on your device** and only the result enters the conversation.
- **The AI cannot control Android directly.** It requests tools; the ActionPolicyEngine and your confirmations decide. Risky tools are gated behind mode switches and confirmation sheets; no raw shell commands exist.
- **Full Control mode is powerful and riskier.** It unlocks notification reading, replying to notifications, and Do Not Disturb control. It requires an explicit warning acknowledgment. If you do not need it, do not enable it.
- **Your credentials are your responsibility.** Keys are stored encrypted on-device, but anyone who obtains your provider key can use your quota. Treat API keys like passwords.

---

## Roadmap

Shinigami Core 0.1.0 is the first release. The following are **planned, not present in v1** — tracked in the issue tracker, documented honestly:

- Accessibility-based UI automation and screen context awareness
- Shizuku support for elevated (opt-in) operations
- Routines editor and a skills/plugins SDK (declared tools, explicit permissions, user approval)
- Smart home integration (Home Assistant)
- File assistant, vision/camera input
- Web search skill API and native provider function-calling
- Advanced model routing and a usage/cost dashboard
- Protected Apps (a user-defined shield list for sensitive apps) and Provider Data Permissions

## Contributing

Contributions are welcome — code, docs, translations, provider templates, bug reports. Start with [CONTRIBUTING.md](CONTRIBUTING.md). Security-sensitive findings go through [SECURITY.md](SECURITY.md), not public issues.

## License

Shinigami Core is licensed under the [Apache License 2.0](LICENSE). Copyright 2026 Invokeil. The bundled Nunito typeface is licensed under the SIL Open Font License 1.1.

---

<div align="center">

*Shinigami Core — your assistant, your keys, your rules.*

</div>
