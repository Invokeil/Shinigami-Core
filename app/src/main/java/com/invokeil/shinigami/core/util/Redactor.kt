package com.invokeil.shinigami.core.util

import android.util.Log
import java.util.regex.Pattern

/**
 * Centralized redaction of sensitive material (API keys, bearer tokens,
 * passwords) from anything that may reach logs, crash reports or on-screen
 * "technical details" sections. See MASTER SPEC §113.
 */
object Redactor {

    private const val MASK = "••••••••"

    /** A pattern plus the replacement that keeps only non-secret groups. */
    private data class Rule(val pattern: Pattern, val replacement: String)

    private val rules = listOf(
        // Authorization headers: keep "Authorization:" label, mask value
        Rule(
            Pattern.compile("(?i)(authorization\\s*:\\s*)(bearer\\s+)?[A-Za-z0-9._~+/=-]+"),
            "$1$2" + MASK,
        ),
        // Common API key header forms: keep header name, mask value
        Rule(
            Pattern.compile("(?i)((x-api-key|api-key|api_key|key|x-goog-api-key)\\s*[=:]\\s*)[\"']?[A-Za-z0-9._~+/=-]+"),
            "$1" + MASK,
        ),
        // Known key prefixes (OpenAI, GitHub, Google): mask everything
        Rule(
            Pattern.compile("(?i)\\b(sk-[A-Za-z0-9_-]{8,}|ghp_[A-Za-z0-9]{20,}|AIza[A-Za-z0-9_-]{20,})\\b"),
            MASK,
        ),
        // JSON secret fields: keep field name, mask value
        Rule(
            Pattern.compile("(?i)(\"(api_?key|password|token|secret|authorization)\"\\s*:\\s*)\"[^\"]*\""),
            "$1" + MASK,
        ),
        // Query-parameter keys: keep prefix, mask value
        Rule(
            Pattern.compile("(?i)((\\?|&)(key|api_key|apikey|token)=)[A-Za-z0-9._~+/=-]+"),
            "$1" + MASK,
        ),
    )

    /** Replaces every recognised secret with a fixed mask, preserving structure. */
    fun redact(input: String): String {
        if (input.isEmpty()) return input
        var out = input
        for (rule in rules) {
            out = rule.pattern.matcher(out).replaceAll(rule.replacement)
        }
        return out
    }

    /** Diagnostic line for a header whose value must never be shown. */
    fun redactPair(label: String): String = "$label: $MASK"

    /** True when [text] still contains something that looks like a secret. */
    fun looksSensitive(text: String): Boolean =
        rules.any { it.pattern.matcher(text).find() }
}

/**
 * Structured logging facade. Always routes through [Redactor] so that no
 * secret can reach logcat, even in debug builds.
 */
object ShiniLog {

    @Volatile
    var verbose: Boolean = false

    fun d(tag: String, message: String) {
        if (verbose) Log.d(tag, Redactor.redact(message))
    }

    fun i(tag: String, message: String) {
        Log.i(tag, Redactor.redact(message))
    }

    fun w(tag: String, message: String, tr: Throwable? = null) {
        Log.w(tag, Redactor.redact(message), tr)
    }

    fun e(tag: String, message: String, tr: Throwable? = null) {
        Log.e(tag, Redactor.redact(message), tr)
    }
}
