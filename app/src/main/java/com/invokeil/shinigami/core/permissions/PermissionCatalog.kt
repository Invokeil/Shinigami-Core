package com.invokeil.shinigami.core.permissions

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * The Permission Dashboard's data model (MASTER SPEC §28). Every capability
 * is a card with: state, plain-language "why", and a precise grant action —
 * users are never dumped into random settings screens.
 */
data class PermissionCard(
    val id: String,
    val label: String,
    val why: String,
    val state: State,
    val action: Action = Action.NONE,
) {
    enum class State { ENABLED, DISABLED, UNAVAILABLE }
    enum class Action { NONE, RUNTIME, SETTINGS_SCREEN, ASSISTANT_PICKER }
}

@Suppress("MissingPermission")
fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

object PermissionCatalog {

    /** Builds the live dashboard state on demand. */
    fun cards(context: Context): List<PermissionCard> {
        val cards = mutableListOf<PermissionCard>()

        cards.add(
            PermissionCard(
                id = "microphone",
                label = "Microphone",
                why = "Voice input and the optional wake word. Nothing is recorded or stored.",
                state = if (hasPermission(context, Manifest.permission.RECORD_AUDIO))
                    PermissionCard.State.ENABLED else PermissionCard.State.DISABLED,
                action = PermissionCard.Action.RUNTIME,
            ),
        )
        cards.add(
            PermissionCard(
                id = "notifications",
                label = "Notifications",
                why = "Alarms, confirmations and the wake-word indicator.",
                state = if (Build.VERSION.SDK_INT < 33 || hasPermission(context, Manifest.permission.POST_NOTIFICATIONS))
                    PermissionCard.State.ENABLED else PermissionCard.State.DISABLED,
                action = PermissionCard.Action.RUNTIME,
            ),
        )
        cards.add(
            PermissionCard(
                id = "assistant",
                label = "Default Assistant",
                why = "Lets the assistant gesture and long-press-home open Shini.",
                state = if (isDefaultAssistant(context)) PermissionCard.State.ENABLED else PermissionCard.State.DISABLED,
                action = PermissionCard.Action.ASSISTANT_PICKER,
            ),
        )
        cards.add(
            PermissionCard(
                id = "notification_listener",
                label = "Notification Access",
                why = "Reading, summarising and replying to notifications. Optional; Full Control mode.",
                state = if (isNotificationListenerEnabled(context)) PermissionCard.State.ENABLED else PermissionCard.State.DISABLED,
                action = PermissionCard.Action.SETTINGS_SCREEN,
            ),
        )
        cards.add(
            PermissionCard(
                id = "contacts",
                label = "Contacts",
                why = "Finding phone numbers when you say “call Mom”.",
                state = if (hasPermission(context, Manifest.permission.READ_CONTACTS))
                    PermissionCard.State.ENABLED else PermissionCard.State.DISABLED,
                action = PermissionCard.Action.RUNTIME,
            ),
        )
        cards.add(
            PermissionCard(
                id = "phone",
                label = "Phone",
                why = "Placing calls directly. Without it, Shini opens the dialer instead.",
                state = if (hasPermission(context, Manifest.permission.CALL_PHONE))
                    PermissionCard.State.ENABLED else PermissionCard.State.DISABLED,
                action = PermissionCard.Action.RUNTIME,
            ),
        )
        cards.add(
            PermissionCard(
                id = "calendar",
                label = "Calendar",
                why = "Planned for schedule awareness. Not used yet.",
                state = if (hasPermission(context, Manifest.permission.READ_CALENDAR))
                    PermissionCard.State.ENABLED else PermissionCard.State.UNAVAILABLE,
                action = PermissionCard.Action.RUNTIME,
            ),
        )
        cards.add(
            PermissionCard(
                id = "write_settings",
                label = "Modify System Settings",
                why = "Brightness control. Android requires a special opt-in.",
                state = if (Settings.System.canWrite(context)) PermissionCard.State.ENABLED else PermissionCard.State.DISABLED,
                action = PermissionCard.Action.SETTINGS_SCREEN,
            ),
        )
        cards.add(
            PermissionCard(
                id = "dnd",
                label = "Do Not Disturb Access",
                why = "Toggling Do Not Disturb from routines or voice.",
                state = if (isDndGranted(context)) PermissionCard.State.ENABLED else PermissionCard.State.DISABLED,
                action = PermissionCard.Action.SETTINGS_SCREEN,
            ),
        )
        cards.add(
            PermissionCard(
                id = "battery",
                label = "Battery Optimisation",
                why = "Exempting Shini keeps the wake word and assistant reliable. Optional.",
                state = if (isIgnoringBatteryOptimizations(context)) PermissionCard.State.ENABLED else PermissionCard.State.DISABLED,
                action = PermissionCard.Action.SETTINGS_SCREEN,
            ),
        )
        cards.add(
            PermissionCard(
                id = "exact_alarm",
                label = "Alarms & Reminders",
                why = "Exact alarms/timers on Android 12+.",
                state = exactAlarmState(context),
                action = PermissionCard.Action.SETTINGS_SCREEN,
            ),
        )
        cards.add(
            PermissionCard(
                id = "accessibility",
                label = "Accessibility Automation",
                why = "Planned for advanced UI automation. Not used in this version.",
                state = PermissionCard.State.UNAVAILABLE,
                action = PermissionCard.Action.NONE,
            ),
        )
        cards.add(
            PermissionCard(
                id = "overlay",
                label = "Display Over Apps",
                why = "Planned floating assistant bubble. Not used in this version.",
                state = PermissionCard.State.UNAVAILABLE,
                action = PermissionCard.Action.NONE,
            ),
        )
        return cards
    }

    fun isDefaultAssistant(context: Context): Boolean {
        val setting = Settings.Secure.getString(
            context.contentResolver,
            "voice_interaction_service",
        ) ?: return false
        return setting.startsWith(context.packageName)
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        return flat.split(":").any {
            ComponentName.unflattenFromString(it)?.packageName == context.packageName
        }
    }

    fun isDndGranted(context: Context): Boolean {
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        return nm?.isNotificationPolicyAccessGranted == true
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(android.os.PowerManager::class.java)
        return pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    private fun exactAlarmState(context: Context): PermissionCard.State {
        if (Build.VERSION.SDK_INT < 31) return PermissionCard.State.ENABLED
        val am = context.getSystemService(android.app.AlarmManager::class.java)
        return if (am?.canScheduleExactAlarms() == true) {
            PermissionCard.State.ENABLED
        } else {
            PermissionCard.State.DISABLED
        }
    }

    // ---------------------------------------------------------------- //

    /** Intents for the precise grant actions. */
    fun settingsIntent(context: Context, cardId: String): Intent? = when (cardId) {
        "notification_listener" -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        "write_settings" -> Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        )

        "dnd" -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        "battery" -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        "exact_alarm" -> if (Build.VERSION.SDK_INT >= 31) {
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}"),
            )
        } else {
            null
        }

        else -> null
    }

    fun assistantPickerIntent(): Intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)

    fun appDetailsIntent(context: Context): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    )

    /** Runtime permissions referenced by dashboard cards. */
    val runtimeCards = mapOf(
        "microphone" to arrayOf(Manifest.permission.RECORD_AUDIO),
        "notifications" to arrayOf(Manifest.permission.POST_NOTIFICATIONS),
        "contacts" to arrayOf(Manifest.permission.READ_CONTACTS),
        "phone" to arrayOf(Manifest.permission.CALL_PHONE),
        "calendar" to arrayOf(Manifest.permission.READ_CALENDAR),
    )
}
