# Security Policy

## Supported versions

| Version | Supported |
| --- | --- |
| 0.1.x | Yes |

Earlier releases, if any exist when you read this, should be upgraded to the latest 0.1.x before reporting issues.

## Reporting a vulnerability

Please do **not** open a public issue for anything security-sensitive.

- **Preferred:** use GitHub's [private vulnerability reporting](https://github.com/Invokeil/Shinigami-Core/security/advisories/new) on this repository. This reaches the maintainer directly and stays confidential.
- **Alternatively:** open a discussion or issue asking for a private contact channel, and we will take it from there.

Include: affected version (the release APK or commit), device/Android version, steps or a proof of concept, and your assessment of impact. You will get an acknowledgment within a few days and a follow-up as the report is triaged. Fixes land as patch releases; coordinated disclosure is the default and credit is given if you want it.

## Security model in one paragraph

**The AI is untrusted; the policy engine is the boundary.** The model never controls Android directly. It returns a structured JSON envelope (`{"reply", "actions"}`) whose requests must resolve to tools declared in the `ToolRegistry` (27 typed tools), pass argument validation, clear the `ActionPolicyEngine` (permission mode + runtime permissions + special accesses + risk level), and survive any required confirmation before the `ActionEngine` executes them. Every attempt is written to a local audit log. There are no raw shell commands, no free-form execution surface, and the agent loop is bounded (3 provider rounds, 8 actions per request). Details in [ARCHITECTURE.md](ARCHITECTURE.md) and [AUTOMATION.md](AUTOMATION.md).

## High-risk areas

The following are called out explicitly, including what is **not** present, so reports and audits can be scoped correctly.

### Provider credentials (present — treat carefully)
API keys and basic-auth passwords are the most valuable secret in the app. They are encrypted with an Android Keystore AES-GCM key (`SecretVault`), stored only as encrypted blobs with boolean markers in the database, excluded from backups and device transfers (`backup_rules.xml`, `data_extraction_rules.xml`), and redacted from logs (`Redactor`). Residual risk: on a rooted or compromised device, anything the app can decrypt, an attacker with the same privileges can reach. Your keys remain your responsibility — rotate them if a device is lost or compromised.

### Notification access (present, opt-in)
Notification Access is one of the broadest grants Android offers. Shinigami uses it only when you enable the notification tools (Full Control mode) and stores notification payloads **in memory only** — never on disk. Without this grant, notification tools are denied at the policy engine.

### Default Assistant role (present, opt-in)
Becoming the system Default Assistant activates a `VoiceInteractionService`. Shinigami uses it for the summon gesture; it does not intercept or re-route other assistants' data.

### Wake phrase and VoicePrint (present, off by default, experimental)
"Hey Shini" runs a foreground microphone service. Speech recognition prefers the on-device engine. The optional same-voice check (`VoicePrint`) is a **pitch/energy heuristic and explicitly NOT biometric authentication** — it reduces accidental triggers, provides no identity guarantee, and stores only a coarse local profile. Do not rely on it for access control.

### Accessibility automation (roadmap — not present)
Accessibility-service automation is planned but **not in v1**. If you see a Shinigami accessibility service today, it is not ours — please report it.

### Shizuku / elevated execution (not present)
No Shizuku, no root, no ADB-based elevation in v1, and no shell executors at all. Any report of shell execution in Shinigami would be a critical finding.

### Screen context (roadmap — not present)
Reading what is on your screen is a roadmap idea, documented as such; v1 cannot see your screen.

## Hardening recommendations for users

- Stay in **Standard** mode unless you need Enhanced/Full Control; Full Control requires an explicit warning acknowledgment for a reason.
- Prefer the **Sensitive** or **Always** confirmation policy; enable biometric confirmation for high-risk tools.
- Grant permissions just-in-time and revoke what you do not use (Settings → Permission Dashboard).
- Treat provider API keys like passwords; rotate them periodically.

## Scope notes

- The action pipeline, tool registry, and executors are the core attack surface; audits and patches prioritise them.
- Reports about provider-side behaviour (e.g. a provider mishandling your data) are out of scope here but document them if a provider's settings in Shinigami make misuse likely — configuration may be adjusted.
- Roadmap features (skills SDK, accessibility, Shizuku) will not be merged without security review of their permission declarations and user-approval flow.
