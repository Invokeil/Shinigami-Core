package com.invokeil.shinigami.core.ai

import com.invokeil.shinigami.core.actions.ToolRegistry
import org.json.JSONArray
import org.json.JSONObject

/**
 * Strict structured-output parsing (MASTER SPEC §24).
 *
 * Models without native tool calling are driven through a JSON envelope.
 * Anything malformed is NEVER executed — it is surfaced as a plain reply
 * and, if tool JSON was detected but broken, a repair round may be requested.
 */
object ToolCallParser {

    data class Parsed(
        val reply: String,
        val actions: List<Action>,
        val malformed: Boolean = false,
    ) {
        data class Action(val tool: String, val arguments: Map<String, Any?>)

        val hasActions: Boolean get() = actions.isNotEmpty()
    }

    /**
     * Extracts the first balanced JSON object from [text], tolerating code
     * fences and leading prose. Returns null when the text contains no JSON.
     */
    fun extractJsonObject(text: String): String? {
        val cleaned = text.replace("```json", "```").trim()
        val start = cleaned.indexOf('{')
        if (start == -1) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until cleaned.length) {
            val c = cleaned[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) return cleaned.substring(start, i + 1)
                }
            }
        }
        return null
    }

    /**
     * Parses a full model response into [Parsed].
     * - Valid envelope → reply + validated-shape actions (tool names/args, no
     *   schema check here — that is [ToolRegistry.validate]'s job).
     * - No JSON → the whole text is treated as the reply (chat behaviour).
     * - JSON detected but unparseable → malformed=true, raw text as reply.
     */
    fun parse(text: String): Parsed {
        val jsonRaw = extractJsonObject(text)
        if (jsonRaw == null) {
            // Model answered in plain text — perfectly acceptable for chat.
            return Parsed(reply = text.trim(), actions = emptyList())
        }
        return try {
            val obj = JSONObject(jsonRaw)
            val reply = obj.optString("reply", "").ifBlank {
                // Some models put the text elsewhere
                obj.optString("text", "").ifBlank { obj.optString("message", "") }
            }
            val actions = mutableListOf<Parsed.Action>()
            val arr = obj.optJSONArray("actions") ?: obj.optJSONArray("tools") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val a = arr.optJSONObject(i) ?: continue
                val tool = a.optString("tool", a.optString("name", "")).trim()
                if (tool.isEmpty()) continue
                val argsJson = a.optJSONObject("arguments")
                    ?: a.optJSONObject("args")
                    ?: JSONObject()
                val args = mutableMapOf<String, Any?>()
                argsJson.keys().forEach { key -> args[key] = argsJson.opt(key) }
                actions.add(Parsed.Action(tool, args))
            }
            Parsed(reply = reply.ifBlank { "Done." }, actions = actions)
        } catch (t: Throwable) {
            // JSON-looking but broken: do not execute, surface text minus fences
            Parsed(
                reply = text.replace(Regex("```(?:json)?"), "").trim(),
                actions = emptyList(),
                malformed = true,
            )
        }
    }
}
