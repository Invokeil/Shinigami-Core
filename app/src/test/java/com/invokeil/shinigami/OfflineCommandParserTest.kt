package com.invokeil.shinigami

import com.invokeil.shinigami.core.actions.AppCatalog
import com.invokeil.shinigami.core.actions.ToolCall
import com.invokeil.shinigami.core.data.db.CustomCommandDao
import com.invokeil.shinigami.core.data.db.CustomCommandEntity
import com.invokeil.shinigami.core.offline.OfflineCommandParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OfflineCommandParserTest {

    private class FakeCommandDao : CustomCommandDao {
        val items = mutableListOf<CustomCommandEntity>()
        override fun observeAll() = throw UnsupportedOperationException()
        override suspend fun all(): List<CustomCommandEntity> = items

        override suspend fun insert(command: CustomCommandEntity): Long {
            items.add(command)
            return items.size.toLong()
        }

        override suspend fun delete(command: CustomCommandEntity) {
            items.removeIf { it.id == command.id }
        }
    }

    private lateinit var parser: OfflineCommandParser

    @Before
    fun setup() {
        parser = OfflineCommandParser(AppCatalog(), FakeCommandDao())
    }

    private fun spotifyMatcher(q: String): AppCatalog.AppEntry? =
        if (q.contains("spotify")) AppCatalog.AppEntry("com.spotify.music", "Spotify") else null

    @Test
    fun `wake phrase is stripped`() = runTest {
        val match = parser.parse("Hey Shini, flashlight on", appMatcher = null)
        assertNotNull(match)
        assertEquals(true, match!!.toolCalls.first().arguments["on"])
    }

    @Test
    fun `flashlight on`() = runTest {
        val match = parser.parse("flashlight on", appMatcher = null)
        assertNotNull(match)
        assertEquals("flashlight", match!!.toolCalls.first().tool)
        assertEquals(true, match.toolCalls.first().arguments["on"])
    }

    @Test
    fun `flashlight off`() = runTest {
        val match = parser.parse("turn the flashlight off", appMatcher = null)
        assertNotNull(match)
        assertEquals(false, match!!.toolCalls.first().arguments["on"])
    }

    @Test
    fun `alarm with am time`() = runTest {
        val match = parser.parse("set an alarm for 7 am", appMatcher = null)
        assertNotNull(match)
        val call: ToolCall = match!!.toolCalls.first()
        assertEquals("set_alarm", call.tool)
        assertEquals(7, call.arguments["hour"])
        assertEquals(0, call.arguments["minute"])
    }


    @Test
    fun `user phrasing set alarm at 7 am routes to direct set`() = runTest {
        // Regression v0.2.0: "Set alarm at 7 AM" must produce a direct
        // set_alarm tool call (SKIP_UI honored via SET_ALARM permission),
        // never a fallback that merely opens the clock app.
        val match = parser.parse("set alarm at 7 am", appMatcher = null)
        assertNotNull(match)
        val call = match!!.toolCalls.single()
        assertEquals("set_alarm", call.tool)
        assertEquals(7, call.arguments["hour"])
        assertEquals(0, call.arguments["minute"])
    }

    @Test
    fun `alarm at exact time without minutes`() = runTest {
        val match = parser.parse("set alarm at 7:15 am", appMatcher = null)
        assertNotNull(match)
        val call = match!!.toolCalls.first()
        assertEquals("set_alarm", call.tool)
        assertEquals(7, call.arguments["hour"])
        assertEquals(15, call.arguments["minute"])
    }

    @Test
    fun `alarm with colon and pm`() = runTest {
        val match = parser.parse("wake me at 7:30 pm", appMatcher = null)
        assertNotNull(match)
        val call = match!!.toolCalls.first()
        assertEquals(19, call.arguments["hour"])
        assertEquals(30, call.arguments["minute"])
    }

    @Test
    fun `timer minutes`() = runTest {
        val match = parser.parse("set a timer for 20 minutes", appMatcher = null)
        assertNotNull(match)
        val call = match!!.toolCalls.first()
        assertEquals("set_timer", call.tool)
        assertEquals(1200, call.arguments["seconds"])
    }

    @Test
    fun `volume set percent`() = runTest {
        val match = parser.parse("volume 40", appMatcher = null)
        assertNotNull(match)
        val call = match!!.toolCalls.first()
        assertEquals("volume_set", call.tool)
        assertEquals(40, call.arguments["level"])
    }

    @Test
    fun `web search`() = runTest {
        val match = parser.parse("search for best ramen recipe", appMatcher = null)
        assertNotNull(match)
        val call = match!!.toolCalls.first()
        assertEquals("web_search", call.tool)
        assertEquals("best ramen recipe", call.arguments["query"])
    }

    @Test
    fun `navigate to`() = runTest {
        val match = parser.parse("navigate to Central Park", appMatcher = null)
        assertNotNull(match)
        assertEquals("navigate", match!!.toolCalls.first().tool)
    }

    @Test
    fun `open app via matcher`() = runTest {
        val match = parser.parse("open spotify", appMatcher = ::spotifyMatcher)
        assertNotNull(match)
        assertEquals("open_app", match!!.toolCalls.first().tool)
        assertTrue(match.spoken.contains("Spotify"))
    }

    @Test
    fun `open app without matcher returns null`() = runTest {
        val match = parser.parse("open spotify", appMatcher = null)
        assertNull(match)
    }

    @Test
    fun `unknown command returns null`() = runTest {
        val match = parser.parse("what is the meaning of life according to cicero", appMatcher = null)
        assertNull(match)
    }

    @Test
    fun `normalize strips wake word and punctuation`() {
        assertEquals("open youtube", parser.normalize("Hey Shini! Open YouTube."))
    }
}
