package com.invokeil.shinigami

import com.invokeil.shinigami.core.ai.ContextBudgeter
import com.invokeil.shinigami.core.ai.ReplyStreamExtractor
import com.invokeil.shinigami.core.data.db.MessageEntity
import com.invokeil.shinigami.core.data.db.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBudgeterTest {

    private fun msg(role: MessageRole, content: String) =
        MessageEntity(conversationId = 1, role = role, content = content)

    @Test
    fun `keeps recent messages within budget`() {
        val history = (0 until 100).map { msg(MessageRole.USER, "message $it ${"x".repeat(50)}") }
        val selected = ContextBudgeter.select(history, charBudget = 2000)
        assertTrue(selected.size < history.size)
        assertEquals(history.last().content, selected.last().content)
    }

    @Test
    fun `always keeps the newest message even if huge`() {
        val history = listOf(
            msg(MessageRole.USER, "old"),
            msg(MessageRole.ASSISTANT, "older reply"),
            msg(MessageRole.USER, "x".repeat(20_000)),
        )
        val selected = ContextBudgeter.select(history, charBudget = 100)
        assertEquals(1, selected.size)
        assertEquals(history.last().content, selected[0].content)
    }

    @Test
    fun `does not start with an orphan assistant message`() {
        val history = listOf(
            msg(MessageRole.ASSISTANT, "answer"),
            msg(MessageRole.USER, "question"),
        )
        val selected = ContextBudgeter.select(history, charBudget = 10_000)
        assertEquals(MessageRole.USER, selected.first().role)
    }
}

class ReplyStreamExtractorTest {

    @Test
    fun `streams reply text progressively from json envelope`() {
        val extractor = ReplyStreamExtractor()
        var out = ""
        out += extractor.feed("{\"rep")
        out += extractor.feed("ly\":\"Hel")
        out += extractor.feed("lo, user!\"")
        out += extractor.feed(",\"actions\":[]}")
        assertTrue(out.contains("Hel"))
        assertTrue(out.contains("lo, user!"))
    }

    @Test
    fun `plain text passes through`() {
        val extractor = ReplyStreamExtractor()
        var out = ""
        out += extractor.feed("The weather ")
        out += extractor.feed("is nice.")
        assertEquals("The weather is nice.", out)
    }

    @Test
    fun `handles escaped quotes and newlines`() {
        val extractor = ReplyStreamExtractor()
        val full = extractor.feed("""{"reply":"say \"hi\"\nline2","actions":[]}""")
        assertEquals("say \"hi\"\nline2", full)
    }

    @Test
    fun `does not leak json syntax into reply`() {
        val extractor = ReplyStreamExtractor()
        val out = extractor.feed("""{"reply":"All set","actions":[{"tool":"open_app"}]}""")
        assertFalse(out.contains("{"))
        assertFalse(out.contains("\"actions\""))
    }
}
