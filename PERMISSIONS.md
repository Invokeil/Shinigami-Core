# Permissions

Shinigami Core asks for as little as possible, as late as possible. Every capability has a card in the in-app **Permission Dashboard** (Settings → Permissions) showing what it is for, whether it is currently granted, and the exact place to grant it — you are never dumped into a random settings screen. Permissions are requested **just-in-time**, when a feature first needs them, and everything works with fewer grants; affected features simply degrade gracefully.

## The cards

### Microphone
- **Why:** voice input and the optional "Hey Shini" wake phrase. Nothing is recorded or stored.
- **Requested:** when you first use voice input or enable the wake word (which also starts a foreground service with a visible indicator).
- **Without it:** the app is fully usable by keyboard; voice and wake word are unavailable.

### Notifications (Android 13+)
- **Why:** alarms, confirmations, action results, and the wake-word indicator need to reach you.
- **Requested:** when the app first posts a notification.
- **Without it:** you will miss confirmations and feedback while the screen is off.

### Default Assistant
- **Why:** lets the assistant gesture (long-press home, or power-press on many devices) and other system entry points open Shini.
- **Requested:** only if you tap the card; a system picker opens and you choose Shinigami.
- **Without it:** the app works fine from its icon, the Quick Settings tile, and in-app; you just lose the system-wide gesture.

### Notification Access
- **Why:** reading, summarising, dismissing, and replying to notifications. This is the broadest grant in the app, so it is deliberately deep in Full Control mode: the three notification tools (`read_notifications`, `dismiss_notification`, `reply_notification`) are gated behind Full Control, and replying always asks for confirmation.
- **Requested:** only when you enable notification features.
- **Without it:** all other features are unaffected. Notification payloads are held in memory only — never written to disk (see [PRIVACY.md](PRIVACY.md)).

### Contacts
- **Why:** finding a phone number when you say "call Mom". Lookup happens on the device; only the result enters the conversation.
- **Requested:** when you first use contact-related commands.
- **Without it:** `call_contact` and `contact_lookup` are denied by the policy engine; you can still dial numbers directly.

### Phone
- **Why:** placing calls directly.
- **Requested:** when you first ask Shinigami to call someone.
- **Without it:** "call …" falls back to opening the dialer with the number prefilled — it never places the call silently either way.

### Calendar
- **Why:** planned for schedule awareness. **Not used yet** — the tool does not exist in v1, and the dashboard marks the card accordingly.
- **Requested:** never in v1. Granting it today does nothing.

### Modify System Settings
- **Why:** screen brightness control. Android requires this special opt-in (it is not a normal runtime permission).
- **Requested:** only if you try to set brightness.
- **Without it:** `brightness_set` is denied with a message pointing at the dashboard.

### Do Not Disturb Access
- **Why:** toggling Do Not Disturb by voice or command. Another special-access grant, kept behind **Full Control** mode.
- **Requested:** only if you use `dnd_set` while in Full Control.
- **Without it:** DND commands are denied; nothing else changes.

### Battery Optimisation (exemption)
- **Why:** exempting Shini from battery optimisation keeps the wake-phrase service and assistant reliability from being killed by aggressive OEM battery managers.
- **Requested:** only if you enable the wake word or notice the service being killed. Strictly optional.
- **Without it:** everything works; on some devices the background wake service may be stopped sooner. Do not grant it if you prefer maximum battery discipline.

### Alarms & Reminders (exact alarms, Android 12+)
- **Why:** creating exact alarms and timers in the system clock.
- **Requested:** when you first set an alarm or timer on a device that requires the special access.
- **Without it:** alarm/timer commands fall back to inexact scheduling or prompt for the grant.

## Permission modes

Permissions are one layer; **modes** are the other. Modes decide which *tools* the assistant may even attempt, independent of Android's grants:

| Mode | Behaviour |
| --- | --- |
| **Standard** (default) | Everyday device control: apps, web, alarms, timers, media, volume, flashlight, status, settings screens |
| **Enhanced** | Adds personal-data and system-state tools: contacts, dialer, call contact, SMS drafting, brightness |
| **Full Control** | Adds the notification tools and Do Not Disturb |

A tool whose minimum mode exceeds your current mode is **denied by the policy engine** with a human-readable message, even if the underlying Android permission is granted. Changing modes happens in Settings → Permissions.

## Full Control warning

Selecting Full Control shows a dedicated warning screen describing exactly what it unlocks — reading your notifications, sending replies from your messaging apps, and toggling system-wide DND — and requires you to tick **"I understand the risks"** before it can be enabled. If you do not need those capabilities, stay on Standard or Enhanced.

## Confirmation layer

On top of modes and Android permissions sits the confirmation policy (always / sensitive / minimal), per-tool always-confirm flags (Compose SMS, Reply to notification), and an optional biometric prompt for high-risk tools. See [AUTOMATION.md](AUTOMATION.md) for the full gating pipeline and the audit trail every action leaves behind.

## Protected Apps (roadmap)

Planned for a future release: a user-defined shield list of apps the assistant must not interact with — an extra, explicit boundary on top of everything above. Not present in v1.
