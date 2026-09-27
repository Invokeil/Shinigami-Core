package com.invokeil.shinigami

import com.invokeil.shinigami.core.ai.ToolCallParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallParserTest {

    @Test
    fun `parses a valid envelope`() {
        val text = """{"reply":"Opening it now.","actions":[{"tool":"open_app","arguments":{"app_name":"Spotify"}}]}"""
        val parsed = ToolCallParser.parse(text)
        assertEquals("Opening it now.", parsed.reply)
        assertEquals(1, parsed.actions.size)
        assertEquals("open_app", parsed.actions[0].tool)
        assertEquals("Spotify", parsed.actions[0].arguments["app_name"])
        assertFalse(parsed.malformed)
    }

    @Test
    fun `parses envelope inside code fences`() {
        val text = """
            ```json
            {"reply":"Done!","actions":[]}
            ```
        """.trimIndent()
        val parsed = ToolCallParser.parse(text)
        assertEquals("Done!", parsed.reply)
        assertTrue(parsed.actions.isEmpty())
    }

    @Test
    fun `plain text answer is treated as reply`() {
        val parsed = ToolCallParser.parse("The Eiffel Tower is in Paris.")
        assertEquals("The Eiffel Tower is in Paris.", parsed.reply)
        assertTrue(parsed.actions.isEmpty())
        assertFalse(parsed.malformed)
    }

    @Test
    fun `malformed json is never executed`() {
        val text = """{"reply":"here","actions":[{"tool":"open_app","arguments":{"app_name": }}]}"""
        val parsed = ToolCallParser.parse(text)
        assertTrue(parsed.malformed)
        assertTrue(parsed.actions.isEmpty())
    }

    @Test
    fun `extraction finds balanced json with braces in strings`() {
        val text = """{"reply":"use { and } carefully","actions":[]}"""
        val extracted = ToolCallParser.extractJsonObject(text)
        assertEquals(text, extracted)
    }

    @Test
    fun `actions array may be named tools`() {
        val text = """{"reply":"ok","tools":[{"tool":"set_alarm","arguments":{"hour":7,"minute":0}}]}"""
        val parsed = ToolCallParser.parse(text)
        assertEquals(1, parsed.actions.size)
        assertEquals("set_alarm", parsed.actions[0].tool)
    }
}
