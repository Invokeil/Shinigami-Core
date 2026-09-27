package com.invokeil.shinigami.core.offline

import android.content.Context
import android.media.AudioManager
import com.invokeil.shinigami.core.actions.AppCatalog
import com.invokeil.shinigami.core.actions.ToolCall
import com.invokeil.shinigami.core.data.db.CustomCommandDao
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic, fully-offline command understanding (MASTER SPEC §21).
 *
 * Android-side commands never need an LLM: this parser runs before any
 * provider call, costs zero tokens, works with no internet and is unit
 * tested. It understands a curated set of everyday phrases and maps them to
 * *the same* validated tool calls the AI path must use — there is no second,
 * unguarded execution path.
 */
@Singleton
class OfflineCommandParser @Inject constructor(
    private val appCatalog: AppCatalog,
    private val customCommandDao: CustomCommandDao,
) {

    data class Match(
        val toolCalls: List<ToolCall>,
        val spoken: String,
        val directReply: String? = null,
    )

    suspend fun parse(context: Context, rawInput: String): Match? =
        parse(rawInput, context = context, appMatcher = { query -> appCatalog.find(context, query) })

    /**
     * Core parser. [appMatcher] resolves "open <app>" queries; pass null in
     * unit tests (pure-JVM) where the package manager is unavailable.
     * [context] is needed only for relative-volume adjustments.
     */
    suspend fun parse(
        rawInput: String,
        context: Context? = null,
        appMatcher: (suspend (String) -> AppCatalog.AppEntry?)? = null,
    ): Match? {
        val input = normalize(rawInput) ?: return null

        // 1) user-defined custom commands win
        customCommandDao.all().firstOrNull { cmd -> normalize(cmd.phrase) == input }?.let { cmd ->
            val args = try {
                val json = org.json.JSONObject(cmd.argumentsJson.ifBlank { "{}" })
                buildMap {
                    json.keys().forEach { k -> put(k, json.opt(k)) }
                }
            } catch (_: Exception) {
                emptyMap()
            }
            return Match(listOf(ToolCall(cmd.toolId, args)), spoken = cmd.phrase)
        }

        val t = input

        // 2) time & date questions — answered locally, no tool needed
        if (t.matches(Regex("(what('s| is)? the )?time( is it)?\\??"))) {
            val now = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
            return Match(emptyList(), spoken = "It's $now.", directReply = "It's $now.")
        }
        if (t.matches(Regex("(what('s| is)? (the |today's )?)?date( is it| today)?\\??"))) {
            val today = java.time.LocalDate.now()
                .format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH))
            return Match(emptyList(), spoken = "Today is $today.", directReply = "Today is $today.")
        }

        // 3) battery
        if (t.contains(Regex("(battery|charge level)"))) {
            return Match(listOf(ToolCall("battery_status")), spoken = "Checking your battery.")
        }
        if (t.contains(Regex("(device|phone) info"))) {
            return Match(listOf(ToolCall("device_info")), spoken = "Checking your device.")
        }

        // 4) flashlight
        Regex("\\b(flashlight|torch)\\b.*\\b(on|off)\\b|\\b(on|off)\\b.*\\b(flashlight|torch)\\b")
            .find(t)?.let {
                val on = it.groupValues.firstOrNull { g -> g == "on" } != null
                return Match(
                    listOf(ToolCall("flashlight", mapOf("on" to on))),
                    spoken = if (on) "Flashlight on." else "Flashlight off.",
                )
            }

        // 5) alarms — "set an alarm for 7", "wake me at 7:30", "alarm 19.45"
        alarmRegex.find(t)?.let { m ->
            val (hour, minute) = parseTime(m.groupValues[1], t) ?: return@let
            val label = Regex("(?:called|named|label(ed)?)\\s+([\\w ]+)$").find(t)?.groupValues?.get(2)
            return Match(
                listOf(
                    ToolCall(
                        "set_alarm",
                        buildMap {
                            put("hour", hour)
                            put("minute", minute)
                            if (label != null) put("label", label.trim())
                        },
                    ),
                ),
                spoken = "Alarm set for %02d:%02d.".format(hour, minute),
            )
        }

        // 6) timers — "set a timer for 20 minutes"
        timerRegex.find(t)?.let { m ->
            val seconds = m.groupValues[1].toIntOrNull() ?: return@let
            val unit = m.groupValues[2].lowercase()
            val total = when {
                unit.startsWith("hour") -> seconds * 3600
                unit.startsWith("min") -> seconds * 60
                else -> seconds
            }
            return Match(
                listOf(ToolCall("set_timer", mapOf("seconds" to total))),
                spoken = "Timer started for ${durationText(total.toLong())}.",
            )
        }

        // 7) volume
        when {
            t.matches(Regex("(mute|silence)( the)?( media| volume)?")) ->
                return Match(listOf(ToolCall("volume_mute")), spoken = "Muted.")

            t.matches(Regex("(unmute|restore)( the)?( media| volume)?")) ->
                return Match(listOf(ToolCall("volume_unmute")), spoken = "Sound restored.")

            t.contains(Regex("volume (up|down)")) -> {
                val am = context?.getSystemService(AudioManager::class.java) ?: return null
                val up = t.contains("up")
                val max = am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                val cur = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: (max / 2)
                val step = max / 10
                val next = if (up) (cur + step).coerceAtMost(max) else (cur - step).coerceAtLeast(0)
                val pct = (next * 100) / max
                return Match(
                    listOf(ToolCall("volume_set", mapOf("level" to pct))),
                    spoken = "Volume $pct%.",
                )
            }

            Regex("(volume|media) (to )?(\\d{1,3})\\s*%?").find(t) != null ||
                Regex("(set )?(volume|media)\\s*(\\d{1,3})\\s*%?").find(t) != null -> {
                val num = Regex("(\\d{1,3})").find(t)?.groupValues?.get(1)?.toIntOrNull() ?: 50
                return Match(
                    listOf(ToolCall("volume_set", mapOf("level" to num.coerceIn(0, 100)))),
                    spoken = "Volume ${num.coerceIn(0, 100)}%.",
                )
            }
        }

        // 8) media transport
        when {
            t.matches(Regex("(play|resume)( some)?( music| media)?")) || t == "play" ->
                return Match(listOf(ToolCall("media_play")), spoken = "Playing.")

            t.contains(Regex("^(pause|stop)( the)?( music| media| playback)?$")) ->
                return Match(listOf(ToolCall("media_pause")), spoken = "Paused.")

            t.contains(Regex("(next|skip)( song| track)?")) ->
                return Match(listOf(ToolCall("media_next")), spoken = "Next track.")

            t.contains(Regex("(previous|last)( song| track)?")) ->
                return Match(listOf(ToolCall("media_previous")), spoken = "Previous track.")
        }

        // 9) brightness / dnd
        Regex("brightness (to )?(\\d{1,3})\\s*%?").find(t)?.let { m ->
            val lvl = m.groupValues[2].toIntOrNull()?.coerceIn(0, 100) ?: 50
            return Match(
                listOf(ToolCall("brightness_set", mapOf("level" to lvl))),
                spoken = "Brightness $lvl%.",
            )
        }
        Regex("\\b(do not disturb|dnd|silent mode)\\b.*\\b(on|off)\\b").find(t)?.let { m ->
            val on = m.groupValues.last() == "on"
            return Match(
                listOf(ToolCall("dnd_set", mapOf("on" to on))),
                spoken = if (on) "Do Not Disturb on." else "Do Not Disturb off.",
            )
        }

        // 10) communication
        Regex("^call ([\\w' ]+)$").find(t)?.let { m ->
            return Match(
                listOf(ToolCall("call_contact", mapOf("name" to m.groupValues[1].trim()))),
                spoken = "Calling ${m.groupValues[1].trim()}.",
            )
        }
        Regex("^dial ([\\d+()\\- ]+)$").find(t)?.let { m ->
            return Match(
                listOf(ToolCall("dial_number", mapOf("number" to m.groupValues[1].trim()))),
                spoken = "Dialing ${m.groupValues[1].trim()}.",
            )
        }
        Regex("^(?:text|message|sms) ([\\w' ]+?)(?: saying| that|:)? (.+)$").find(t)?.let { m ->
            return Match(
                listOf(
                    ToolCall(
                        "sms_compose",
                        mapOf(
                            "phone" to "",
                            "body" to m.groupValues[2].trim(),
                        ),
                    ),
                ),
                spoken = "Drafting a message to ${m.groupValues[1].trim()}.",
            )
        }

        // 11) web / navigation
        Regex("^(?:search( the web)?( for)?|google|look up) (.+)$").find(t)?.let { m ->
            return Match(
                listOf(ToolCall("web_search", mapOf("query" to m.groupValues[3].trim()))),
                spoken = "Searching for “${m.groupValues[3].trim()}”.",
            )
        }
        Regex("^(?:navigate to|directions to|route to) (.+)$").find(t)?.let { m ->
            return Match(
                listOf(ToolCall("navigate", mapOf("destination" to m.groupValues[1].trim()))),
                spoken = "Navigating to ${m.groupValues[1].trim()}.",
            )
        }
        Regex("^open (youtube|google) (and )?(search( for)? )?(.+)$").find(t)?.let { m ->
            val engine = m.groupValues[1]
            val q = m.groupValues[5].trim()
            return if (engine == "youtube") {
                Match(
                    listOf(
                        ToolCall("open_url", mapOf("url" to "https://www.youtube.com/results?search_query=${java.net.URLEncoder.encode(q, "UTF-8")}")),
                    ),
                    spoken = "Searching YouTube for “$q”.",
                )
            } else {
                Match(
                    listOf(ToolCall("web_search", mapOf("query" to q))),
                    spoken = "Searching for “$q”.",
                )
            }
        }

        // 12) settings screens
        settingsMap[t]?.let { screen ->
            return Match(
                listOf(ToolCall("open_settings", mapOf("screen" to screen))),
                spoken = "Opening ${screen} settings.",
            )
        }

        // 13) open app
        Regex("^open (.+)$").find(t)?.let { m ->
            val query = m.groupValues[1].trim()
            val app = appMatcher?.invoke(query)
            if (app != null) {
                return Match(
                    listOf(ToolCall("open_app", mapOf("app_name" to query))),
                    spoken = "Opening ${app.label}.",
                )
            }
            // Fall through: maybe it's a URL-ish thing like "open youtube.com"
            if (query.contains(".")) {
                return Match(
                    listOf(ToolCall("open_url", mapOf("url" to query))),
                    spoken = "Opening $query.",
                )
            }
            return null // let the AI decide what "open photography" means
        }

        return null
    }

    /** Strips wake phrase, punctuation and normalises whitespace. */
    fun normalize(raw: String): String? {
        var s = raw.trim().lowercase(Locale.ENGLISH)
        // strip any leading wake phrase variant
        s = s.replace(Regex("^(hey |hi |ok |okay )?(shini|shiny|sheeni|shinigami)[,!.: ]+"), "").trim()
        if (s.isEmpty()) return null
        s = s.replace(Regex("[!.,?]+$"), "").trim()
        if (s.isEmpty()) return null
        return s
    }

    private fun parseTime(raw: String, full: String): Pair<Int, Int>? {
        val cleaned = raw.trim().removePrefix("at ").removePrefix("for ").trim()
        val m = Regex("(\\d{1,2})[:.](\\d{2})|(\\d{1,2})").find(cleaned) ?: return null
        val hour = (m.groupValues[1].ifEmpty { m.groupValues[3] }).toIntOrNull() ?: return null
        val minute = m.groupValues[2].ifEmpty { "0" }.toIntOrNull() ?: 0
        var h = hour
        if (Regex("\\bp\\.?m\\.?\\b").containsMatchIn(full) && h in 1..11) h += 12
        if (Regex("\\ba\\.?m\\.?\\b").containsMatchIn(full) && h == 12) h = 0
        if (h !in 0..23 || minute !in 0..59) return null
        return h to minute
    }

    private fun durationText(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return when {
            m > 0 && s > 0 -> "${m}m ${s}s"
            m > 0 -> "${m}m"
            else -> "${s}s"
        }
    }

    private companion object {
        val alarmRegex =
            Regex("(?:set(?: up| an)? alarm(?: for| at)?|alarm(?: for| at)?|wake me(?: up)?(?: at| for)?|remind me(?: at)?)\\s+([\\d:.]+\\s*(?:a\\.?m\\.?|p\\.?m\\.?)?|\\d{1,2}[:.]\\d{2}\\s*(?:a\\.?m\\.?|p\\.?m\\.?)?)", RegexOption.IGNORE_CASE)

        val timerRegex =
            Regex("timer(?: for)?\\s+(\\d+)\\s*(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)\\b", RegexOption.IGNORE_CASE)

        val settingsMap = mapOf(
            "wifi settings" to "wifi",
            "open wifi settings" to "wifi",
            "wi-fi settings" to "wifi",
            "bluetooth settings" to "bluetooth",
            "open bluetooth settings" to "bluetooth",
            "display settings" to "display",
            "sound settings" to "sound",
            "battery settings" to "battery",
            "app settings" to "apps",
            "open settings" to "general",
            "settings" to "general",
        )
    }
}
