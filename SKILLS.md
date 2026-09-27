# Skills and Capabilities

"Skills" in Shinigami Core are the capabilities the assistant can request. In v1, every skill is a **built-in tool** declared in the `ToolRegistry` — there are no third-party skills yet, and the Skill SDK is a roadmap item described honestly at the bottom of this page.

## Built-in capability registry (v1)

Every entry below is a typed tool: the AI can only request these, arguments are validated, and the ActionPolicyEngine enforces the minimum mode, runtime permissions, risk level, and confirmation policy before anything runs.

| Tool id | Display name | Risk | Minimum mode | Extra guardrails |
| --- | --- | --- | --- | --- |
| `open_app` | Open app | LOW | Standard | — |
| `app_info` | App details | LOW | Standard | — |
| `open_url` | Open link | LOW | Standard | — |
| `web_search` | Web search | LOW | Standard | — |
| `navigate` | Navigate | LOW | Standard | — |
| `set_alarm` | Set alarm | LOW | Standard | — |
| `set_timer` | Set timer | LOW | Standard | — |
| `media_play` | Play media | LOW | Standard | — |
| `media_pause` | Pause media | LOW | Standard | — |
| `media_next` | Next track | LOW | Standard | — |
| `media_previous` | Previous track | LOW | Standard | — |
| `volume_set` | Set volume | LOW | Standard | — |
| `volume_mute` | Mute | LOW | Standard | — |
| `volume_unmute` | Unmute | LOW | Standard | — |
| `flashlight` | Flashlight | LOW | Standard | — |
| `battery_status` | Battery status | LOW | Standard | — |
| `device_info` | Device info | LOW | Standard | — |
| `open_settings` | Open settings | LOW | Standard | Ten fixed screens (wifi, bluetooth, display, …) |
| `dnd_set` | Do Not Disturb | MEDIUM | Full Control | Requires DND access |
| `brightness_set` | Set brightness | MEDIUM | Enhanced | Requires Modify System Settings |
| `contact_lookup` | Find contact | MEDIUM | Enhanced | Requires Contacts |
| `dial_number` | Dial number | MEDIUM | Enhanced | Opens dialer prefilled; never auto-calls |
| `call_contact` | Call contact | HIGH | Enhanced | Requires Phone; biometric-eligible |
| `sms_compose` | Compose SMS | MEDIUM | Enhanced | Always confirms; opens draft, never sends |
| `read_notifications` | Read notifications | MEDIUM | Full Control | Requires Notification Access |
| `dismiss_notification` | Dismiss notification | MEDIUM | Full Control | Requires Notification Access |
| `reply_notification` | Reply to notification | HIGH | Full Control | Requires Notification Access; always confirms |

That is the complete list — 27 tools. If a request does not map to one of them, the assistant says so. Read [AUTOMATION.md](AUTOMATION.md) for how these are gated in practice, and [PERMISSIONS.md](PERMISSIONS.md) for the permissions behind them.

## Skills SDK — roadmap (not in v1)

A skills/plugins SDK is planned so the community can extend Shinigami without weakening the security model. The design commitments below are the contract future work must keep:

- **Declared tools.** A skill declares its tools with the same typed parameter schemas used by built-ins. No free-form command surfaces.
- **Declared permissions.** A skill states exactly which Android permissions and special accesses it needs, and the minimum permission mode its tools require.
- **No automatic privilege inheritance.** Installing a skill grants nothing. Its tools sit in the same registry and are subject to the same mode gating, risk levels, and confirmation policies as anything else.
- **User approval at install and at runtime.** Enabling a skill shows you its full capability list for explicit approval; individual executions still pass through confirmation when policy requires it.
- **Brokered by the Action Engine.** Skills never execute on their own. Every skill call flows through the same validate → policy → confirm → execute → audit pipeline, and the same bounded agent loop (3 rounds / 8 actions) applies.
- **Local first.** Skills are expected to run on-device. If a skill calls a network service, it must disclose it, and its traffic is visible in the provider configuration you control.

Related roadmap items that build on the same machinery: routines editor (chains of tool calls with user-defined triggers), web search skill API, Protected Apps, and a skills marketplace UI. None of these exist in v1 — the issue tracker is the source of truth for status.
