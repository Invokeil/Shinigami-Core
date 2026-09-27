package com.invokeil.shinigami.core.actions

import com.invokeil.shinigami.core.data.PrefsRepository
import com.invokeil.shinigami.core.data.PermissionMode
import com.invokeil.shinigami.core.data.db.RiskLevel
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/* ---------------------------------------------------------------------------
 * Tool Registry (MASTER SPEC §22, §23).
 *
 * Every capability the assistant can request is declared here with a typed
 * parameter schema, a risk level and the permission mode that unlocks it.
 * The AI can ONLY request tools from this registry — there is no free-form
 * command surface anywhere in the codebase.
 * --------------------------------------------------------------------------*/

enum class ParamType { STRING, INT, FLOAT, BOOLEAN }

data class ToolParam(
    val name: String,
    val type: ParamType,
    val description: String,
    val required: Boolean = true,
    val enumValues: List<String> = emptyList(),
)

data class ToolDefinition(
    val id: String,
    val displayName: String,
    val description: String,
    val params: List<ToolParam> = emptyList(),
    val risk: RiskLevel = RiskLevel.LOW,
    val minMode: PermissionMode = PermissionMode.STANDARD,
    val manifestPermissions: List<String> = emptyList(),
    val alwaysConfirm: Boolean = false,
    /** Human-friendly summary builder for confirmation sheets. */
    val summarizer: (Map<String, Any?>) -> String = { id },
)

data class ToolCall(
    val tool: String,
    val arguments: Map<String, Any?> = emptyMap(),
)

@Singleton
class ToolRegistry @Inject constructor() {

    val tools: List<ToolDefinition> by lazy { buildTools() }

    fun byId(id: String): ToolDefinition? = tools.firstOrNull { it.id == id }

    fun all(): List<ToolDefinition> = tools

    /**
     * Validates and coerces raw arguments (usually straight from an LLM) into
     * a typed map. Unknown keys are dropped, missing required params fail.
     * Malformed calls are NEVER executed (§24) — the caller feeds the error
     * back to the model as a failed tool result.
     */
    fun validate(call: ToolCall): ValidatedCall {
        val def = byId(call.tool)
            ?: return ValidatedCall.Invalid("unknown tool '${call.tool}'")
        val out = mutableMapOf<String, Any?>()
        for (param in def.params) {
            val raw = call.arguments[param.name]
            if (raw == null || raw == JSONObject.NULL) {
                if (param.required) {
                    return ValidatedCall.Invalid("missing required parameter '${param.name}' for ${def.id}")
                }
                continue
            }
            val coerced = when (param.type) {
                ParamType.STRING -> when (raw) {
                    is String -> raw
                    is Number, is Boolean -> raw.toString()
                    else -> return ValidatedCall.Invalid("parameter '${param.name}' must be a string")
                }

                ParamType.INT -> (raw as? Number)?.toInt()
                    ?: raw.toString().toIntOrNull()
                    ?: return ValidatedCall.Invalid("parameter '${param.name}' must be an integer")

                ParamType.FLOAT -> (raw as? Number)?.toFloat()
                    ?: raw.toString().toFloatOrNull()
                    ?: return ValidatedCall.Invalid("parameter '${param.name}' must be a number")

                ParamType.BOOLEAN -> when (raw) {
                    is Boolean -> raw
                    is String -> raw.lowercase().let {
                        when (it) {
                            "true", "on", "1", "yes" -> true
                            "false", "off", "0", "no" -> false
                            else -> return ValidatedCall.Invalid("parameter '${param.name}' must be a boolean")
                        }
                    }

                    is Number -> raw.toInt() != 0
                    else -> return ValidatedCall.Invalid("parameter '${param.name}' must be a boolean")
                }
            }
            if (param.type == ParamType.STRING && param.enumValues.isNotEmpty()) {
                val v = (coerced as String).trim()
                val match = param.enumValues.firstOrNull { it.equals(v, ignoreCase = true) }
                if (match == null) {
                    return ValidatedCall.Invalid(
                        "parameter '${param.name}' must be one of ${param.enumValues}",
                    )
                }
                out[param.name] = match
            } else {
                out[param.name] = coerced
            }
        }
        return ValidatedCall.Valid(def, out)
    }

    sealed interface ValidatedCall {
        data class Valid(val def: ToolDefinition, val args: Map<String, Any?>) : ValidatedCall
        data class Invalid(val reason: String) : ValidatedCall
    }

    /** Serialises the registry into the system-prompt tool manifest (§79). */
    fun manifestJson(): String {
        val arr = JSONArray()
        for (t in tools) {
            val params = JSONObject()
            for (p in t.params) {
                params.put(
                    p.name,
                    JSONObject()
                        .put("type", p.type.name.lowercase())
                        .put("description", p.description)
                        .put("required", p.required)
                        .apply {
                            if (p.enumValues.isNotEmpty()) put("enum", JSONArray(p.enumValues))
                        },
                )
            }
            arr.put(
                JSONObject()
                    .put("tool", t.id)
                    .put("description", t.description)
                    .put("risk", t.risk.name)
                    .put("parameters", params),
            )
        }
        return arr.toString()
    }

    // ------------------------------------------------------------------ //

    private fun buildTools(): List<ToolDefinition> = listOf(
        // ---- Apps & web -------------------------------------------------
        ToolDefinition(
            id = "open_app",
            displayName = "Open app",
            description = "Launch an installed application by name",
            params = listOf(ToolParam("app_name", ParamType.STRING, "App name, e.g. Spotify")),
            risk = RiskLevel.LOW,
            summarizer = { a -> "Open ${a["app_name"]}" },
        ),
        ToolDefinition(
            id = "app_info",
            displayName = "App details",
            description = "Open the system App Info screen for an installed application",
            params = listOf(ToolParam("app_name", ParamType.STRING, "App name")),
            risk = RiskLevel.LOW,
            summarizer = { a -> "Open app info for ${a["app_name"]}" },
        ),
        ToolDefinition(
            id = "open_url",
            displayName = "Open link",
            description = "Open a URL in the browser",
            params = listOf(ToolParam("url", ParamType.STRING, "Full URL including https://")),
            risk = RiskLevel.LOW,
            summarizer = { a -> "Open ${a["url"]}" },
        ),
        ToolDefinition(
            id = "web_search",
            displayName = "Web search",
            description = "Search the web in the user's browser",
            params = listOf(ToolParam("query", ParamType.STRING, "Search query")),
            risk = RiskLevel.LOW,
            summarizer = { a -> "Search the web for “${a["query"]}”" },
        ),
        ToolDefinition(
            id = "navigate",
            displayName = "Navigate",
            description = "Open directions in maps",
            params = listOf(ToolParam("destination", ParamType.STRING, "Place or address")),
            risk = RiskLevel.LOW,
            summarizer = { a -> "Navigate to ${a["destination"]}" },
        ),

        // ---- Time -------------------------------------------------------
        ToolDefinition(
            id = "set_alarm",
            displayName = "Set alarm",
            description = "Create an alarm in the system clock app",
            params = listOf(
                ToolParam("hour", ParamType.INT, "Hour 0-23"),
                ToolParam("minute", ParamType.INT, "Minute 0-59"),
                ToolParam("label", ParamType.STRING, "Optional label", required = false),
            ),
            risk = RiskLevel.LOW,
            summarizer = { a ->
                val h = a["hour"]?.toString() ?: "?"
                val m = (a["minute"] as? Number)?.toInt() ?: 0
                "Set alarm for %02d:%02d".format(h.toIntOrNull() ?: 0, m)
            },
        ),
        ToolDefinition(
            id = "set_timer",
            displayName = "Set timer",
            description = "Start a countdown timer",
            params = listOf(
                ToolParam("seconds", ParamType.INT, "Duration in seconds"),
                ToolParam("label", ParamType.STRING, "Optional label", required = false),
            ),
            risk = RiskLevel.LOW,
            summarizer = { a -> "Start a ${humanDuration((a["seconds"] as? Number)?.toLong() ?: 0)} timer" },
        ),

        // ---- Media ------------------------------------------------------
        ToolDefinition(
            id = "media_play",
            displayName = "Play media",
            description = "Resume playback of the active media session",
            risk = RiskLevel.LOW,
        ),
        ToolDefinition(
            id = "media_pause",
            displayName = "Pause media",
            description = "Pause the active media session",
            risk = RiskLevel.LOW,
        ),
        ToolDefinition(
            id = "media_next",
            displayName = "Next track",
            description = "Skip to the next track",
            risk = RiskLevel.LOW,
        ),
        ToolDefinition(
            id = "media_previous",
            displayName = "Previous track",
            description = "Skip to the previous track",
            risk = RiskLevel.LOW,
        ),
        ToolDefinition(
            id = "volume_set",
            displayName = "Set volume",
            description = "Set media volume to a percentage",
            params = listOf(ToolParam("level", ParamType.INT, "0-100")),
            risk = RiskLevel.LOW,
            summarizer = { a -> "Set media volume to ${a["level"]}%" },
        ),
        ToolDefinition(
            id = "volume_mute",
            displayName = "Mute",
            description = "Mute media audio",
            risk = RiskLevel.LOW,
        ),
        ToolDefinition(
            id = "volume_unmute",
            displayName = "Unmute",
            description = "Restore media audio",
            risk = RiskLevel.LOW,
        ),

        // ---- Device -----------------------------------------------------
        ToolDefinition(
            id = "flashlight",
            displayName = "Flashlight",
            description = "Turn the camera torch on or off",
            params = listOf(ToolParam("on", ParamType.BOOLEAN, "true to enable")),
            risk = RiskLevel.LOW,
            summarizer = { a -> if (a["on"] == true) "Turn the flashlight ON" else "Turn the flashlight OFF" },
        ),
        ToolDefinition(
            id = "battery_status",
            displayName = "Battery status",
            description = "Report the current battery level and charging state",
            risk = RiskLevel.LOW,
        ),
        ToolDefinition(
            id = "device_info",
            displayName = "Device info",
            description = "Report device model, Android version, storage and battery",
            risk = RiskLevel.LOW,
        ),
        ToolDefinition(
            id = "open_settings",
            displayName = "Open settings",
            description = "Open a system settings screen",
            params = listOf(
                ToolParam(
                    "screen",
                    ParamType.STRING,
                    "Which settings screen",
                    enumValues = listOf(
                        "general", "wifi", "bluetooth", "display", "sound",
                        "battery", "apps", "date", "location", "volume",
                    ),
                ),
            ),
            risk = RiskLevel.LOW,
            summarizer = { a -> "Open ${a["screen"]} settings" },
        ),
        ToolDefinition(
            id = "dnd_set",
            displayName = "Do Not Disturb",
            description = "Enable or disable Do Not Disturb (opens the panel when access is missing)",
            params = listOf(ToolParam("on", ParamType.BOOLEAN, "true to enable")),
            risk = RiskLevel.MEDIUM,
            minMode = PermissionMode.FULL,
            summarizer = { a -> if (a["on"] == true) "Turn ON Do Not Disturb" else "Turn OFF Do Not Disturb" },
        ),
        ToolDefinition(
            id = "brightness_set",
            displayName = "Set brightness",
            description = "Set screen brightness (requires Modify system settings)",
            params = listOf(ToolParam("level", ParamType.INT, "0-100")),
            risk = RiskLevel.MEDIUM,
            minMode = PermissionMode.ENHANCED,
            summarizer = { a -> "Set brightness to ${a["level"]}%" },
        ),

        // ---- Communication ----------------------------------------------
        ToolDefinition(
            id = "contact_lookup",
            displayName = "Find contact",
            description = "Look up a contact's phone number",
            params = listOf(ToolParam("name", ParamType.STRING, "Contact name")),
            risk = RiskLevel.MEDIUM,
            minMode = PermissionMode.ENHANCED,
            manifestPermissions = listOf(android.Manifest.permission.READ_CONTACTS),
        ),
        ToolDefinition(
            id = "dial_number",
            displayName = "Dial number",
            description = "Open the dialer with a number prefilled (never auto-calls)",
            params = listOf(ToolParam("number", ParamType.STRING, "Phone number")),
            risk = RiskLevel.MEDIUM,
            minMode = PermissionMode.ENHANCED,
            summarizer = { a -> "Dial ${a["number"]}" },
        ),
        ToolDefinition(
            id = "call_contact",
            displayName = "Call contact",
            description = "Place a phone call to a contact by name",
            params = listOf(ToolParam("name", ParamType.STRING, "Contact name")),
            risk = RiskLevel.HIGH,
            minMode = PermissionMode.ENHANCED,
            manifestPermissions = listOf(
                android.Manifest.permission.READ_CONTACTS,
                android.Manifest.permission.CALL_PHONE,
            ),
            summarizer = { a -> "Call ${a["name"]}" },
        ),
        ToolDefinition(
            id = "sms_compose",
            displayName = "Compose SMS",
            description = "Open the SMS app with a prefilled message",
            params = listOf(
                ToolParam("body", ParamType.STRING, "Message text"),
                ToolParam("phone", ParamType.STRING, "Optional phone number", required = false),
            ),
            risk = RiskLevel.MEDIUM,
            minMode = PermissionMode.ENHANCED,
            alwaysConfirm = true,
            summarizer = { a -> "Compose: “${a["body"]}”" },
        ),

        // ---- Notifications ----------------------------------------------
        ToolDefinition(
            id = "read_notifications",
            displayName = "Read notifications",
            description = "Read recent notifications aloud / as text",
            params = listOf(ToolParam("app", ParamType.STRING, "Optional app filter", required = false)),
            risk = RiskLevel.MEDIUM,
            minMode = PermissionMode.FULL,
        ),
        ToolDefinition(
            id = "dismiss_notification",
            displayName = "Dismiss notification",
            description = "Dismiss a recent notification",
            params = listOf(ToolParam("app", ParamType.STRING, "Optional app filter", required = false)),
            risk = RiskLevel.MEDIUM,
            minMode = PermissionMode.FULL,
        ),
        ToolDefinition(
            id = "reply_notification",
            displayName = "Reply to notification",
            description = "Reply to a conversation notification via its inline reply action",
            params = listOf(
                ToolParam("text", ParamType.STRING, "Reply text"),
                ToolParam("app", ParamType.STRING, "Optional app filter", required = false),
            ),
            risk = RiskLevel.HIGH,
            minMode = PermissionMode.FULL,
            alwaysConfirm = true,
            summarizer = { a -> "Send reply: “${a["text"]}”" },
        ),

        // ---- v0.2: screen automation (accessibility-backed) -------------
        ToolDefinition(
            id = "screen_read",
            displayName = "Read screen",
            description = "Read visible text of the current screen for context (never for protected apps)",
            risk = RiskLevel.MEDIUM,
            minMode = PermissionMode.ENHANCED,
            summarizer = { _ -> "Read the current screen" },
        ),
        ToolDefinition(
            id = "screen_tap",
            displayName = "Tap screen point",
            description = "Tap a screen coordinate via accessibility gesture",
            params = listOf(
                ToolParam("x", ParamType.FLOAT, "X in pixels"),
                ToolParam("y", ParamType.FLOAT, "Y in pixels"),
            ),
            risk = RiskLevel.HIGH,
            minMode = PermissionMode.FULL,
            alwaysConfirm = true,
            summarizer = { a -> "Tap screen at (${a["x"]}, ${a["y"]})" },
        ),
        ToolDefinition(
            id = "screen_scroll",
            displayName = "Scroll screen",
            description = "Scroll the current screen down",
            risk = RiskLevel.MEDIUM,
            minMode = PermissionMode.ENHANCED,
            summarizer = { _ -> "Scroll down" },
        ),
        ToolDefinition(
            id = "screen_back",
            displayName = "Press back",
            description = "Press the system back action",
            risk = RiskLevel.MEDIUM,
            minMode = PermissionMode.ENHANCED,
            summarizer = { _ -> "Press back" },
        ),

        // ---- v0.2: routines --------------------------------------------
        ToolDefinition(
            id = "run_routine",
            displayName = "Run routine",
            description = "Run a user-defined routine by name",
            params = listOf(ToolParam("name", ParamType.STRING, "Routine name")),
            risk = RiskLevel.MEDIUM,
            summarizer = { a -> "Run routine “${a["name"]}”" },
        ),
    )

    private fun humanDuration(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return when {
            m > 0 && s > 0 -> "$m min $s sec"
            m > 0 -> "$m min"
            else -> "$s sec"
        }
    }
}
