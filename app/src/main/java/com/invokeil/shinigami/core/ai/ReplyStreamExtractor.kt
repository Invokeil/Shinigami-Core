package com.invokeil.shinigami.core.ai

/**
 * Progressive extraction of the "reply" text from a streaming JSON envelope
 * so the user sees words as they are generated (MASTER SPEC §76) — without
 * ever flashing raw JSON at the UI.
 *
 * If the model answers in plain text instead, the stream is passed through.
 */
class ReplyStreamExtractor {

    private val buffer = StringBuilder()
    private var mode: Mode? = null

    private var replyKeyFound = false
    private var inReplyString = false
    private var replyClosed = false
    private var escaped = false
    private var unicodeRemain = 0
    private val unicodeBuf = StringBuilder()
    private var emittedIndex = 0 // plain-text mode cursor

    private enum class Mode { PLAIN, JSON }

    /** Feed a chunk; returns reply text newly produced by this chunk. */
    fun feed(chunk: String): String {
        if (chunk.isEmpty()) return ""
        buffer.append(chunk)
        if (mode == null) {
            val trimmed = buffer.trimStart().toString()
            mode = when {
                trimmed.startsWith("{") -> Mode.JSON
                trimmed.startsWith("```") -> Mode.PLAIN // fenced plain answer, pass through
                trimmed.isEmpty() -> return "" // need more signal
                else -> Mode.PLAIN
            }
        }
        return when (mode!!) {
            Mode.PLAIN -> plainText()
            Mode.JSON -> jsonReply()
        }
    }

    private fun plainText(): String {
        val out = buffer.substring(emittedIndex)
        emittedIndex = buffer.length
        return out
    }

    private fun jsonReply(): String {
        if (replyClosed) return ""
        val s = buffer.toString()
        if (!replyKeyFound) {
            val idx = s.indexOf(REPLY_KEY)
            if (idx == -1) return ""
            val colon = s.indexOf(':', idx + REPLY_KEY.length)
            if (colon == -1) return ""
            val quote = s.indexOf('"', colon)
            if (quote == -1) return ""
            replyKeyFound = true
            inReplyString = true
            return scanFrom(s, quote + 1)
        }
        return scanFrom(s, lastScanned)
    }

    private var lastScanned = 0

    private fun scanFrom(s: String, startIdx: Int): String {
        val sb = StringBuilder()
        var i = startIdx.coerceAtLeast(lastScanned.coerceAtMost(s.length))
        // If we are mid-unicode escape, complete it first
        if (unicodeRemain > 0) {
            while (unicodeRemain > 0 && i < s.length) {
                unicodeBuf.append(s[i]); i++
                unicodeRemain--
                if (unicodeRemain == 0) {
                    sb.append(unicodeBuf.toString().toInt(16).toChar())
                    unicodeBuf.clear()
                }
            }
            lastScanned = i
            if (unicodeRemain > 0) return sb.toString()
        }
        while (i < s.length) {
            val c = s[i]
            if (!inReplyString) {
                // shouldn't happen before closing; treat as closed
                replyClosed = true
                break
            }
            if (escaped) {
                escaped = false
                when (c) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('')
                    'u' -> {
                        // consume up to 4 hex digits, possibly across chunks
                        val available = (s.length - (i + 1)).coerceAtLeast(0)
                        val take = minOf(4, available)
                        if (take < 4) {
                            unicodeRemain = 4 - take
                            i += take
                            lastScanned = i + 1
                            return sb.toString()
                        }
                        val hex = s.substring(i + 1, i + 5)
                        val code = hex.toIntOrNull(16)
                        if (code != null) sb.append(code.toChar())
                        i += 4
                    }

                    else -> sb.append(c)
                }
            } else when (c) {
                '\\' -> escaped = true
                '"' -> {
                    inReplyString = false
                    replyClosed = true
                    i++
                    break
                }

                else -> sb.append(c)
            }
            i++
        }
        lastScanned = i
        return sb.toString()
    }

    private companion object {
        const val REPLY_KEY = "\"reply\""
    }
}
