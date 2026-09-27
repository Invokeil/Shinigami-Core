# Architecture

Shinigami Core is a single-module Android application (`app`, package `com.invokeil.shinigami`) written in Kotlin 2.0 with Jetpack Compose, Material 3, Hilt, Room, DataStore, OkHttp + kotlinx.serialization, and WorkManager. minSdk 29, targetSdk 34, compileSdk 35. Build uses Gradle Kotlin DSL with a version catalog (Gradle 8.10.2, AGP 8.7.3).

The architecture is organised around one principle: **the model proposes, the device disposes.** Every capability the AI can use is declared in a typed registry, validated, gated by policy, optionally confirmed, executed, and audited. There is no code path anywhere that turns model output into a shell command or a raw system call.

## Security pipeline

The same pipeline serves both input paths. Offline-matched commands skip the AI provider but still pass through the ToolRegistry, the policy engine, and (if required) confirmation before any executor runs.

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

## Module map

All packages live under `app/src/main/java/com/invokeil/shinigami/`.

| Package | Contents | Responsibility |
| --- | --- | --- |
| `core.util` | `Redactor`, `AppResult` | Secret redaction for logs; typed success/error wrapper |
| `core.security` | `SecretVault` | Android Keystore AES-GCM storage for API keys and passwords |
| `core.data` | `PrefsRepository`, `ChatRepository` | DataStore-backed settings, conversation and history management |
| `core.data.db` | `Entities`, `Daos`, `ShinigamiDatabase` | Room database (8 entities, see schema below) |
| `core.network` | `HttpClientFactory` | Shared OkHttp client configuration |
| `core.provider` | `AiProvider`, `OpenAiCompatibleProvider`, `GeminiProvider`, `ProviderFactory`, `ProviderTemplates`, `ProviderHttp`, `ProviderRepository` | The BYOK layer: provider profiles, templates, auth schemes, streaming chat |
| `core.ai` | `AgentOrchestrator`, `ToolCallParser`, `ReplyStreamExtractor`, `SystemPrompts`, `ContextBudgeter`, `AgentConfirmationBridge` | The agent loop: prompting, envelope parsing, bounded rounds, confirmation bridging to UI |
| `core.actions` | `ToolRegistry`, `ActionPolicyEngine`, `ActionEngine`, `Executors`, `AppCatalog` | The trusted boundary: tool schemas, validation, policy decisions, executors, app lookup |
| `core.offline` | `OfflineCommandParser` | Deterministic, network-free command understanding |
| `core.voice` | `SpeechInputManager`, `TtsManager`, `VoicePrint` | Speech recognition, text-to-speech, experimental same-voice heuristic |
| `core.permissions` | `PermissionCatalog` | Permission Dashboard card model and live state |
| `core.ui` | `theme/` (`Theme`, `Color`, `Type`), `root/ShiniRoot`, `components/ShiniComponents` | Compose theming (Dark Reaper + light + dynamic color, Nunito), root navigation, shared UI |
| `feature.home` | `AssistantScreen`, `AssistantViewModel` | Main assistant surface, streaming transcript, action feed |
| `feature.onboarding` | `OnboardingScreen`, `OnboardingViewModel` | First-run provider and permission setup |
| `feature.providers` | `ProvidersScreen`, `ProviderEditorScreen` | Provider profile list and editor |
| `feature.permissions` | `PermissionsScreen` | Permission Dashboard |
| `feature.settings` | `SettingsScreen`, `SettingsViewModel` | Modes, confirmation policy, voice, memory, retention, themes |
| `feature.history` | `HistoryScreen` | Conversation history and retention controls |
| `feature.audit` | `AuditScreen` | The audit log viewer |
| `feature.commands` | `CommandsScreen` | Custom phrase command editor |
| `service` | `WakeWordService`, `VoiceInteraction`, `ShinigamiTileService`, `ShinigamiNotificationListener` | Foreground wake-phrase service, Default Assistant hook, Quick Settings tile, notification access |
| `di` | `DatabaseModule` | Hilt wiring |
| root | `MainActivity`, `ShinigamiApp`, `ThemeViewModel` | Application entry points |

## Key interfaces

### `AiProvider` (`core.provider`)

The abstraction every backend implements. Implementations: `OpenAiCompatibleProvider` (covers OpenAI, Mistral, GLM/Z.AI, Ollama, and any custom `/chat/completions` server) and `GeminiProvider` (Google's native REST shape). Created by `ProviderFactory` from a `ProviderProfileEntity`. Streams `AiStreamEvent`s (deltas, tool-request payloads, errors) and carries profile settings — base URL, auth type, headers, temperature, max tokens, streaming support, cleartext opt-in.

### `ToolRegistry` (`core.actions`)

The single source of truth for what the assistant *can* do: 27 `ToolDefinition`s, each with an id, display name, description, typed parameter schema (STRING / INT / FLOAT / BOOLEAN, optional enums, optional defaults), a `RiskLevel`, a minimum `PermissionMode`, and an optional `alwaysConfirm` flag. The registry serialises itself into the system-prompt tool manifest and **validates and coerces** every incoming call — unknown tools, unknown parameters, and malformed types are rejected before anything else happens. Malformed calls are never executed; the failure is fed back to the model as a failed tool result.

### `ActionPolicyEngine` (`core.actions`)

The trusted boundary between "the AI asked" and "Android will do it". Decision order:

1. **Tool known and validated** (registry did this).
2. **Mode unlocked** — the tool's minimum mode must be ≤ the user's current mode (Standard / Enhanced / Full Control), otherwise `Deny` with a human-readable reason.
3. **Runtime permissions held** — the tool's manifest permissions must be granted, otherwise `Deny`.
4. **Special access present** — e.g. Modify System Settings for brightness, DND access, notification listener binding.
5. **Confirmation required?** Based on the global policy (always / sensitive / minimal), the tool's `alwaysConfirm` flag, and the risk level; optional BiometricPrompt for high-risk tools.

Result is one of `Allow`, `Confirm(summary, reason)`, or `Deny(reason)`. Denials are shown to the user *and* fed back to the model.

### `ActionEngine` (`core.actions`)

Executes a validated, policy-approved call. Maps tool ids to concrete `Executors` (intents, clock, audio, camera torch, notification listener, …), writes every attempt to the audit log (`SUCCESS / FAILED / DENIED / CONFIRMED / CANCELLED`), and propagates cancellation so the emergency stop works mid-run. Results — success or failure — are returned to the orchestrator as tool-result messages.

### `ConfirmationRequester` (`core.actions`)

The seam between the action pipeline and the UI. The engine asks "may I do *X* (summarised)?" and the answer comes back from a confirmation sheet rendered by Compose, bridged through `AgentConfirmationBridge`. This keeps `core.actions` UI-free while guaranteeing that no confirmation-dependent action ever executes without a real user decision.

## Data flow

### Offline path

1. User text (voice transcript or typed) is normalised; any wake-phrase variant ("hey shini …") is stripped.
2. `OfflineCommandParser` matches against its curated patterns and user-defined custom commands (custom commands win first).
3. A match produces the *same* `ToolCall` objects the AI path would produce — there is no second, unguarded execution path.
4. The call goes through registry validation → policy engine → confirmation → `ActionEngine`, exactly like AI requests.
5. The parser also answers pure questions locally (time, date) with a direct reply and no tool call.

### AI path

1. Offline parsing runs first; if it matches, the AI provider may never be called.
2. Otherwise `AgentOrchestrator` assembles the request: system prompt (`SystemPrompts`, including the `ToolRegistry.manifestJson()` tool manifest), budgeted history (`ContextBudgeter`), and the new user message.
3. The active provider streams tokens. `ReplyStreamExtractor` surfaces reply deltas to the UI as they arrive; `ToolCallParser` extracts the structured JSON envelope `{"reply": "...", "actions": [{"tool": "...", "arguments": {...}}]}` from the stream.
4. Each action is validated, policy-checked, confirmed (the UI shows a summary; the stream pauses), and executed. Results are appended as tool messages.
5. The loop repeats — **bounded at 3 provider rounds and 8 tool executions per user request** — until the model produces a final reply or a limit is hit.
6. Usage (tokens, latency) is recorded per provider profile; every executed action is in the audit log.

## Threading model

- **Coroutines everywhere.** UI-facing work runs in ViewModel scopes via `viewModelScope`; the agent loop runs as a cancellable suspend chain so the emergency stop cancels instantly.
- **Cold streams:** Room DAOs expose `Flow` for lists (history, audit, providers); DataStore preferences are `Flow`s collected by `PrefsRepository`.
- **Hot streams:** provider streaming is bridged with `callbackFlow` into `AiStreamEvent`s; `AgentOrchestrator` exposes `StateFlow`s for status, reply deltas, action feed, and pending confirmations.
- **Services:** `WakeWordService` is a foreground service with its own coroutine scope for the recognition loop; the notification listener is callback-driven and hands events to in-memory state.
- **No blocking on the main thread:** OkHttp calls execute on Dispatchers configured in `HttpClientFactory`; Room and DataStore are suspending.

## Storage schema (Room, 8 entities)

| Table | Purpose |
| --- | --- |
| `provider_profiles` | Provider configurations: type, base URL, model, auth type, header name, username, extra headers, temperature, max tokens, custom instructions, default flag, cleartext opt-in, streaming flag. **Only boolean markers** (`hasApiKey`, `hasPassword`) — never credential values |
| `conversations` | Chat threads: title, timestamps, pinned, temporary (incognito) |
| `messages` | Messages per conversation: role (user/assistant/system/tool), content, provider id, model, token counts, latency |
| `audit_events` | Every action attempt: timestamp, action, source (offline parser / AI agent / routine / user UI), result, detail, target package, risk level |
| `usage_records` | Per-call token usage and latency per provider/model |
| `custom_commands` | User phrase → tool id + fixed arguments (unique phrase index) |
| `memory_entries` | Optional assistant memory (preference / frequent command / alias / fact). Feature is off by default |
| `protected_apps` | Reserved for the Protected Apps roadmap feature (user-defined shield list) |

## Encryption design

- `SecretVault` generates and holds an AES-GCM key in the **Android Keystore** (hardware-backed where the device supports it). API keys and basic-auth passwords are encrypted before storage and decrypted only in memory when a request is built.
- The Room database stores **only markers** that a secret exists — an attacker with the database alone learns nothing usable.
- Backup rules (`backup_rules.xml`, `data_extraction_rules.xml`) **exclude secrets** from cloud backups and device transfers.
- `Redactor` scrubs anything that looks like a key, token, or password out of every log line before it leaves the process.
- Notification payloads processed by the listener live in a bounded **in-memory ring only** — they are never written to disk.

## Extension points

- **New tool:** declare a `ToolDefinition` (id, schema, risk, minimum mode, summariser) in `ToolRegistry`, then implement its executor in `Executors`. The manifest, validation, policy checks, confirmation, audit, and UI feed pick it up automatically.
- **New provider:** add a `ProviderType`, a `ProviderTemplate` (defaults the editor pre-fills), and either reuse `OpenAiCompatibleProvider` (most OpenAI-shaped APIs) or implement `AiProvider` for a different wire format in `ProviderFactory`.
- **New offline pattern:** add a matcher to `OfflineCommandParser`; unit tests run on the JVM without a device.
- **Confirmation UX:** any implementation of `ConfirmationRequester` can drive confirmations — the engine never depends on the UI.
- **Future (roadmap, not in v1):** a skills/plugins SDK will use the same registry/policy machinery with declared permissions and explicit user approval; Shizuku-backed elevated actions and accessibility automation would enter as additional policy-gated executors, never as a bypass.
