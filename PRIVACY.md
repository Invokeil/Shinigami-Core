# Privacy

Privacy is not a feature toggle in Shinigami Core; it is the design constraint the rest of the app is built around. This page states plainly what the app collects, what leaves your device, what stays, and how to delete everything.

## What the app collects

**Nothing.** No account, no sign-up, no analytics, no crash-reporting service, no telemetry, no advertising identifiers, no fingerprinting. There is no backend operated by Invokeil — the app talks directly to the AI provider you configure. If you never add a provider (or only use Ollama locally), no network request carrying your data leaves the device at all.

## What leaves the device

Exactly two things can cross the network, both initiated by you:

1. **Requests to the AI provider you chose.** A request contains: your message, the conversation context the model needs (bounded by the context budget), the system prompt with the tool manifest (tool names, descriptions, parameter schemas — no personal data), and your per-profile settings (temperature, max tokens). Nothing else. Your API keys are not part of the message payload beyond the authentication header to that provider; your contacts, notifications, and other app data are never uploaded — lookups such as "call Mom" resolve **on the device**, and only the result enters the conversation.
2. **The tool executions themselves**, in the sense that opening a link or searching the web obviously talks to the corresponding service (your browser, YouTube, maps). Those services see you, not Shinigami's servers — there are none.

If you want the network path to stay empty, use the offline commands and an Ollama (local) provider.

## Local storage

| Data | Where | Notes |
| --- | --- | --- |
| Conversations and messages | Room database (app-private) | Retention is your choice; temporary/incognito conversations are excluded |
| Provider profiles | Room database | Stores **only boolean markers** that a secret exists — never the secret itself |
| API keys / passwords | Encrypted (Android Keystore AES-GCM) via `SecretVault` | Excluded from backups and device transfers; decrypted only in memory for requests |
| Settings and preferences | DataStore (app-private) | Permission mode, confirmation policy, voice options, themes, retention |
| Audit log | Room database (app-private) | Every action attempt: timestamp, source, result, risk |
| Usage records | Room database (app-private) | Token counts and latency per call, for your own review |
| Custom commands, memory entries | Room database (app-private) | Memory is off by default |
| Notifications | **In-memory ring only** | Notification payloads read for the notification tools are never written to disk |

None of this is synced anywhere, because there is nothing to sync it to.

## Notification handling

The optional Notification Access feature (Full Control mode) reads recent notifications so the assistant can read, summarise, dismiss, or reply to them on your request. Payloads are kept in a bounded in-memory ring for the current session and evicted with it. They are not logged, not written to the database, and not sent anywhere unless you explicitly ask the assistant to do something that involves the chosen provider.

## Wake word

"Hey Shini" is optional and off by default. Recognition runs through Android's `SpeechRecognizer`, preferring the on-device engine, and the service does **phrase matching only** — audio and transcripts are processed to decide whether you said the phrase, and are not stored, streamed, or uploaded by Shinigami. The experimental same-voice check (`VoicePrint`) is an on-device pitch/energy heuristic — explicitly **not** biometric authentication — whose coarse profile stays local.

## Telemetry

None. To repeat the important part: no analytics SDKs, no crash reporting service, no usage pings, no remote config, no update checks that phone home. The app cannot send a metric because it does not contain the code to do so. You can verify this on the published source and reproducible builds.

## How to wipe your data

Everything lives in the app's private storage, so removal is complete and simple:

- **Selective:** Settings and History provide per-area controls — delete conversations (or let retention trim them), clear the audit log, delete custom commands, delete provider profiles (which removes their encrypted secrets), and disable memory (off by default).
- **Everything:** **Android Settings → Apps → Shinigami → Clear storage**, or simply uninstall the app. Both remove the Room database, DataStore preferences, encrypted secrets, and any cached state. Because secrets are excluded from backups and device transfers, nothing survives on a new device.

Uninstalling does not, of course, delete anything already sent to a third-party provider while you were using it — that is governed by that provider's data policy, which is exactly why bring-your-own-key lets you pick one you trust.

## Roadmap privacy features

- **Protected Apps:** a user-defined shield list of apps the assistant will not interact with.
- **Provider Data Permissions:** per-provider rules constraining what context may be sent to which provider.

Both are planned, not in v1; the absence of these toggles today does not indicate data is being shared — v1 sends nothing beyond what is described above.
