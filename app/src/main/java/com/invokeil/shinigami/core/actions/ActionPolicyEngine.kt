package com.invokeil.shinigami.core.actions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.invokeil.shinigami.core.data.ConfirmationPolicy
import com.invokeil.shinigami.core.data.PrefsRepository
import com.invokeil.shinigami.core.data.PermissionMode
import kotlinx.coroutines.flow.firstOrNull
import com.invokeil.shinigami.core.data.db.RiskLevel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Policy Engine (MASTER SPEC §22, §26, §118). The trusted boundary between
 * "the AI asked for something" and "Android will now do it".
 *
 * Decision order: tool known → mode unlocked → runtime permissions held →
 * confirmation required by global policy or per-tool rule.
 */
@Singleton
class ActionPolicyEngine @Inject constructor(
    private val prefs: PrefsRepository,
) {

    sealed interface Decision {
        /** Execute right away. */
        data object Allow : Decision

        /** Execute after the user confirms (summary shown in the UI). */
        data class Confirm(val summary: String, val reason: Reason) : Decision {
            enum class Reason { POLICY_ALWAYS, SENSITIVE_RISK, BIOMETRIC }
        }

        /** Refuse; [reason] is shown to the user and fed to the AI. */
        data class Deny(val reason: String) : Decision
    }

    suspend fun evaluate(
        context: Context,
        call: ValidatedToolCall,
    ): Decision {
        val def = call.def
        val mode = prefs.permissionMode.firstOrNull()
            ?: PermissionMode.STANDARD

        if (def.minMode > mode) {
            return Decision.Deny(
                "${def.displayName} needs ${modeName(def.minMode)} permission mode " +
                    "(current: ${modeName(mode)}). You can change it in Settings → Permissions.",
            )
        }

        val missing = def.manifestPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            return Decision.Deny(
                "Missing permission: ${prettyPermission(missing.first())}. " +
                    "Grant it from the Permission Dashboard.",
            )
        }

        val special = specialAccessMissing(context, def.id)
        if (special != null) {
            return Decision.Deny(special)
        }

        val policy = prefs.confirmationPolicy.firstOrNull()
            ?: ConfirmationPolicy.SENSITIVE
        val biometric = prefs.biometricHighRisk.firstOrNull() ?: false

        val needsConfirm = when {
            def.alwaysConfirm -> true
            policy == ConfirmationPolicy.ALWAYS && def.risk >= RiskLevel.MEDIUM -> true
            policy == ConfirmationPolicy.SENSITIVE && def.risk >= RiskLevel.HIGH -> true
            else -> false
        }

        return if (needsConfirm) {
            Decision.Confirm(
                summary = def.summarizer(call.args),
                reason = if (def.alwaysConfirm) Decision.Confirm.Reason.POLICY_ALWAYS
                else Decision.Confirm.Reason.SENSITIVE_RISK,
            )
        } else if (biometric && def.risk >= RiskLevel.HIGH) {
            Decision.Confirm(
                summary = def.summarizer(call.args),
                reason = Decision.Confirm.Reason.BIOMETRIC,
            )
        } else {
            Decision.Allow
        }
    }

    /** Returns a human message when a special access is required but missing. */
    fun specialAccessMissing(context: Context, toolId: String): String? = when (toolId) {
        "brightness_set" -> if (!Settings.System.canWrite(context)) {
            "Modify system settings is required for brightness. " +
                "Grant it from the Permission Dashboard."
        } else {
            null
        }

        "dnd_set" -> {
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            if (nm?.isNotificationPolicyAccessGranted != true) {
                "Do Not Disturb access is required. Grant it from the Permission Dashboard."
            } else {
                null
            }
        }

        "read_notifications", "dismiss_notification", "reply_notification" ->
            if (!isNotificationListenerBound(context)) {
                "Notification access is required. Grant it from the Permission Dashboard."
            } else {
                null
            }

        else -> null
    }

    private fun isNotificationListenerBound(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        return flat.split(":").any {
            val cn = android.content.ComponentName.unflattenFromString(it)
            cn?.packageName == context.packageName
        }
    }

    fun modeName(mode: PermissionMode): String = when (mode) {
        PermissionMode.STANDARD -> "Standard"
        PermissionMode.ENHANCED -> "Enhanced"
        PermissionMode.FULL -> "Full Control"
    }

    private fun prettyPermission(permission: String): String = when (permission) {
        Manifest.permission.RECORD_AUDIO -> "Microphone"
        Manifest.permission.READ_CONTACTS -> "Contacts"
        Manifest.permission.CALL_PHONE -> "Phone"
        Manifest.permission.READ_CALENDAR -> "Calendar"
        Manifest.permission.CAMERA -> "Camera"
        Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION -> "Location"
        else -> permission.substringAfterLast('.').replace('_', ' ')
    }

    companion object {
        val CONTACTS = arrayOf(Manifest.permission.READ_CONTACTS)
        val PHONE = arrayOf(Manifest.permission.CALL_PHONE)
        val MICROPHONE = arrayOf(Manifest.permission.RECORD_AUDIO)
        val NOTIFICATIONS: Array<String> =
            if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else arrayOf()

        /** Tools and the runtime permissions they require, for the dashboard. */
        val manifestRequirements: Map<String, List<String>> = mapOf(
            "contact_lookup" to listOf(Manifest.permission.READ_CONTACTS),
            "call_contact" to listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE),
            "dial_number" to emptyList(),
        )
    }
}

/** Result of [ToolRegistry.validate], carried alongside the definition. */
data class ValidatedToolCall(
    val def: com.invokeil.shinigami.core.actions.ToolDefinition,
    val args: Map<String, Any?>,
)
