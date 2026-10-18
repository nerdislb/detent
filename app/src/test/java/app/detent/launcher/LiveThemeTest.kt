package app.detent.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveThemeTest {
    private fun body(
        bg: String = "#111419",
        fg: String = "#f0f3f7",
        accent: String? = "#ff9f0a",
        mode: String? = "dark",
        name: String = "smoke",
    ): String {
        val colors = buildList {
            add("\"background\":\"$bg\"")
            add("\"foreground\":\"$fg\"")
            if (accent != null) add("\"accent\":\"$accent\"")
        }.joinToString(",")
        val m = if (mode != null) ",\"mode\":\"$mode\"" else ""
        return "{\"version\":1,\"name\":\"$name\"$m,\"colors\":{$colors}}"
    }

    @Test
    fun readsTheColours() {
        val t = parseLiveTheme(body())!!
        assertEquals("smoke", t.name)
        assertEquals(0xFF111419L, t.bg)
        assertEquals(0xFFF0F3F7L, t.fg)
        assertEquals(0xFFFF9F0AL, t.accent)
        assertFalse(t.light)
    }

    @Test
    fun rejectsABodyWithoutBackgroundAndForeground() {
        assertNull(parseLiveTheme("{\"colors\":{\"foreground\":\"#ffffff\"}}"))
        assertNull(parseLiveTheme("{\"colors\":{\"background\":\"#000000\"}}"))
        assertNull(parseLiveTheme("{}"))
        assertNull(parseLiveTheme("not json"))
    }

    @Test
    fun rejectsColoursThatAreNotHex() {
        assertNull(parseLiveTheme(body(bg = "red")))
        assertNull(parseLiveTheme(body(fg = "#12345")))
    }

    @Test
    fun acceptsAnAlphaSuffixAndDropsIt() {
        assertEquals(0xFF111419L, parseLiveTheme(body(bg = "#111419ff"))!!.bg)
    }

    @Test
    fun derivesTheModeFromTheBackgroundWhenMissing() {
        assertTrue(parseLiveTheme(body(bg = "#f1ede4", fg = "#1c1a17", mode = null))!!.light)
        assertFalse(parseLiveTheme(body(mode = null))!!.light)
    }

    @Test
    fun anExplicitModeWins() {
        assertTrue(parseLiveTheme(body(mode = "light"))!!.light)
    }

    @Test
    fun anAccentThatMeltsIntoTheBackgroundFallsBackToTheForeground() {
        val t = parseLiveTheme(body(accent = "#121519"))!!
        assertEquals(t.fg, t.accent)
    }

    @Test
    fun theHandStaysBetweenBackgroundAndForeground() {
        val t = parseLiveTheme(body())!!
        fun r(c: Long) = ((c shr 16) and 0xFF).toInt()
        assertTrue(r(t.hand) > r(t.bg) && r(t.hand) < r(t.fg))
    }

    @Test
    fun namesAreTrimmedAndDefaulted() {
        assertEquals("laptop", parseLiveTheme(body(name = "  "))!!.name)
        assertEquals(24, parseLiveTheme(body(name = "x".repeat(60)))!!.name.length)
    }

    @Test
    fun anUnknownKeyDoesNotMatter() {
        assertNotNull(parseLiveTheme("{\"extra\":1,\"colors\":{\"background\":\"#000000\",\"foreground\":\"#ffffff\",\"zzz\":3}}"))
    }
}
