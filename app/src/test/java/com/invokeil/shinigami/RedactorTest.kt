package com.invokeil.shinigami

import com.invokeil.shinigami.core.actions.ToolCall
import com.invokeil.shinigami.core.actions.ToolRegistry
import com.invokeil.shinigami.core.util.Redactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactorTest {

    @Test
    fun `bearer token is masked`() {
        val input = "Authorization: Bearer sk-abc123def456ghi789"
        val out = Redactor.redact(input)
        assertFalse(out.contains("sk-abc123"))
        assertTrue(out.contains("••••••••"))
    }

    @Test
    fun `openai style key is masked`() {
        val out = Redactor.redact("my key is sk-proj-9f8e7d6c5b4a3210fedcba")
        assertFalse(out.contains("9f8e7d6c5b4a3210"))
    }

    @Test
    fun `google api key is masked`() {
        val out = Redactor.redact("AIzaSyA-1234567890abcdefghijklmnopqrstuv")
        assertFalse(out.contains("AIzaSyA"))
    }

    @Test
    fun `json secret fields are masked`() {
        val input = """{"api_key":"supersecret","model":"gpt-4o"}"""
        val out = Redactor.redact(input)
        assertFalse(out.contains("supersecret"))
        assertTrue(out.contains("gpt-4o"))
    }

    @Test
    fun `query parameter keys are masked`() {
        val out = Redactor.redact("https://api.example.com/v1/models?key=real-secret-value")
        assertFalse(out.contains("real-secret-value"))
    }

    @Test
    fun `normal text passes through`() {
        val text = "Opened Spotify successfully in 120 ms"
        assertEquals(text, Redactor.redact(text))
    }
}

class ToolRegistryTest {

    private val registry = ToolRegistry()

    @Test
    fun `valid call coerces numbers`() {
        val result = registry.validate(
            ToolCall(
                "set_alarm",
                mapOf("hour" to 7.0, "minute" to "30"),
            ),
        )
        assertTrue(result is ToolRegistry.ValidatedCall.Valid)
        val valid = result as ToolRegistry.ValidatedCall.Valid
        assertEquals(7, valid.args["hour"])
        assertEquals(30, valid.args["minute"])
    }

    @Test
    fun `unknown tool is rejected`() {
        val result = registry.validate(ToolCall("run_shell", mapOf("cmd" to "rm -rf /")))
        assertTrue(result is ToolRegistry.ValidatedCall.Invalid)
    }

    @Test
    fun `missing required parameter is rejected`() {
        val result = registry.validate(ToolCall("open_app", emptyMap()))
        assertTrue(result is ToolRegistry.ValidatedCall.Invalid)
    }

    @Test
    fun `boolean coercion from string`() {
        val result = registry.validate(ToolCall("flashlight", mapOf("on" to "true")))
        assertTrue(result is ToolRegistry.ValidatedCall.Valid)
        assertEquals(true, (result as ToolRegistry.ValidatedCall.Valid).args["on"])
    }

    @Test
    fun `enum parameters accept case-insensitive values`() {
        val result = registry.validate(ToolCall("open_settings", mapOf("screen" to "WIFI")))
        assertTrue(result is ToolRegistry.ValidatedCall.Valid)
        assertEquals("wifi", (result as ToolRegistry.ValidatedCall.Valid).args["screen"])
    }

    @Test
    fun `enum parameters reject unknown values`() {
        val result = registry.validate(ToolCall("open_settings", mapOf("screen" to "format_factory")))
        assertTrue(result is ToolRegistry.ValidatedCall.Invalid)
    }

    @Test
    fun `manifest contains every tool`() {
        val manifest = registry.manifestJson()
        registry.all().forEach { tool ->
            assertTrue("manifest missing ${tool.id}", manifest.contains("\"${tool.id}\""))
        }
    }
}
