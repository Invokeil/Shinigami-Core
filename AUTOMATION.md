# Automation and the Action Engine

Automation in Shinigami Core means one thing: **structured, validated, policy-gated tool calls.** Whether a command came from the offline parser, your AI provider, or a custom phrase, it becomes the same object — a `ToolCall` — and flows through the same pipeline before Android does anything.

```
request → ToolRegistry (validate) → ActionPolicyEngine (mode/permissions/risk)
        → confirmation (if required) → ActionEngine executor → audit log → result
```

## The tool schema

The AI never sends commands; it returns a JSON envelope at the end of a reply:

```json
{
  "reply": "Done — alarm set.",
  "actions": [
    { "tool": "set_alarm", "arguments": { "hour": 7, "minute": 0, "label": "Work" } }
  ]
}
```

Every `tool` must exist in the registry and every argument is validated and coerced against a typed schema (string / int / float / boolean, with enums and optional defaults):

- Unknown tool → rejected. Unknown parameters → dropped. Missing required parameter or wrong type → rejected, and the reason is fed back to the model as a failed tool result.
- Nothing is ever "guessed and run": malformed calls are **never executed**.
- Offline and custom commands produce the same validated calls — there is no unguarded path.

## Risk levels

| Level | Meaning | Examples (v1) |
| --- | --- | --- |
| **LOW** | Cosmetic or read-only; nothing you would regret | open app, web search, alarms, timers, media, volume, flashlight, battery/device info, settings screens |
| **MEDIUM** | Touches personal data or system state | brightness, contact lookup, dialer, compose SMS, DND, read/dismiss notifications |
| **HIGH** | Real-world consequence (calls, sends) | call contact, reply to a notification |
| **CRITICAL** | Reserved for future capabilities with irreversible effect | *no CRITICAL tools exist in v1* |

Risk is declared per tool in the registry and drives confirmation policy, biometric gating, and the Full Control mode requirement.

## Confirmation policies

Three global policies in Settings:

- **Always** — confirm every MEDIUM-and-above action.
- **Sensitive** (default) — confirm HIGH-and-above actions.
- **Minimal** — auto-run what the mode and permissions already allow.

Two additional layers stack on top:

- **Per-tool `alwaysConfirm`** — some tools always ask, regardless of policy (v1: Compose SMS, Reply to notification).
- **BiometricPrompt for high-risk** — optional; HIGH-risk tools require your fingerprint/device credential.

A confirmation sheet shows a plain-language summary ("Call Mom", "Set brightness to 60%") before anything executes. Denials are shown to you and fed back to the model.

## Audit log

Every action attempt — from any source (offline parser, AI agent, user UI) — is written locally to `audit_events`: timestamp, action, source, result (`SUCCESS / FAILED / DENIED / CONFIRMED / CANCELLED`), detail, target package, and risk level. Browse it in the Audit screen. It stays on your device.

## Emergency stop

The agent loop is fully cancellable. Tap the stop control and everything halts immediately — pending confirmations drop, queued actions do not run, and cancellations are recorded in the audit log. The loop is also **bounded: at most 3 provider rounds and 8 tool executions per user request**, so a runaway model cannot grind through your device.

## Offline commands

The `OfflineCommandParser` handles the everyday phrases with zero network and zero tokens. Wake-phrase prefixes ("hey shini …") are stripped automatically. Examples it understands:

- **Time and date:** "what's the time", "what's the date"
- **Battery and device:** "battery", "charge level", "device info", "phone info"
- **Flashlight:** "flashlight on", "torch off"
- **Alarms:** "set an alarm for 7", "wake me at 7:30", "alarm 19.45", "remind me at 8 pm", with an optional label: "…called Work"
- **Timers:** "set a timer for 20 minutes", "timer for 1 hour"
- **Volume:** "mute", "silence", "unmute", "volume up", "volume down", "volume 50%"
- **Media:** "play", "pause", "next track", "previous song"
- **Brightness:** "brightness to 60%"
- **Do Not Disturb:** "do not disturb on", "dnd off", "silent mode on"
- **Calls:** "call Mom" · **Dial:** "dial 555 0123" · **SMS draft:** "text John saying I'm on my way"
- **Web search:** "search for cheapest flights", "google tangram", "look up rust vs go"
- **YouTube/Google:** "open youtube and search for lo-fi"
- **Navigation:** "navigate to Central Park", "directions to the airport", "route to home"
- **Settings screens:** "wifi settings", "bluetooth settings", "display settings", "sound settings", "battery settings", "app settings", "open settings"
- **Apps and links:** "open spotify", "open youtube.com"

## Custom commands

Define your own phrase and bind it to any tool with fixed arguments — e.g. "night mode" → DND on + brightness 20%. Custom phrases take priority over built-in patterns and are matched exactly (after normalisation). Manage them in the Commands screen; they are stored locally in Room.

## What automation is NOT possible (and why)

Honesty matters more than marketing. Shinigami deliberately does **not** do the following, and cannot be talked into it:

- **No raw shell commands, ever.** There is no code path from model output to a shell, and no Shizuku/root elevation in v1.
- **No operating other apps' UI.** Accessibility-based automation (tapping, typing, reading screens) is a roadmap feature and is off the table for v1 — so the assistant cannot click through your banking app.
- **No silent calls or silent SMS.** `dial_number` opens the dialer prefilled and never places a call; Compose SMS opens your messaging app with a draft. Sending requires you.
- **No reading or writing arbitrary app data.** Android's sandbox prevents it, and Shinigami does not attempt workarounds.
- **No app install/uninstall, no changing other apps' settings**, no unlocking the device, no disabling security.
- **No free-form "do whatever" execution.** The registry is a fixed list; the AI cannot invent tools. Unknown requests are refused and reported.

These limits are architectural, not configuration. Future roadmap items (accessibility automation, Shizuku) will arrive as additional *policy-gated* tools with explicit user opt-in — not as a bypass of this pipeline.
