package app.detent.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL

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

    private val sha = "3f0ee7d38d7d3022845bbd796c0291ae77be9f7ad1a6bdbaca19ca2ed1349475"

    private fun withWall(path: String, hash: String = sha) =
        "{\"colors\":{\"background\":\"#000000\",\"foreground\":\"#ffffff\"}," +
            "\"wallpaper\":{\"path\":\"$path\",\"sha256\":\"$hash\"}}"

    @Test
    fun readsTheWallpaperReference() {
        val w = parseLiveTheme(withWall("wallpaper/3f0ee7.jpg"))!!.wall!!
        assertEquals("wallpaper/3f0ee7.jpg", w.path)
        assertEquals(sha, w.sha)
    }

    @Test
    fun aThemeWithoutAWallpaperHasNone() {
        assertNull(parseLiveTheme(body())!!.wall)
    }

    @Test
    fun theHashIsLowerCased() {
        assertEquals(sha, parseLiveTheme(withWall("w/a.jpg", sha.uppercase()))!!.wall!!.sha)
    }

    @Test
    fun badWallpaperReferencesAreDroppedButTheThemeStays() {
        for (path in listOf("../x.jpg", "/abs.jpg", "//evil.example/x.jpg", "http://evil.example/x", "a/../b.jpg", "", "a b.jpg")) {
            val t = parseLiveTheme(withWall(path))
            assertNotNull(path, t)
            assertNull(path, t!!.wall)
        }
        assertNull(parseLiveTheme(withWall("w/a.jpg", "abc"))!!.wall)
    }

    private val base = URL("https://laptop.example:8444/theme.json")

    @Test
    fun resolvesTheWallpaperNextToTheTheme() {
        assertEquals("https://laptop.example:8444/wallpaper/a.jpg", resolveWallUrl(base, "wallpaper/a.jpg").toString())
    }

    @Test
    fun refusesAWallpaperOnAnotherHost() {
        assertNull(resolveWallUrl(base, "//evil.example/a.jpg"))
        assertNull(resolveWallUrl(base, "https://evil.example/a.jpg"))
        assertNull(resolveWallUrl(base, "../a.jpg"))
    }

    @Test
    fun theDialLabelIsShortAndMarked() {
        assertEquals("~smoke", liveLabel("smoke"))
        assertEquals("laptop", liveLabel("laptop"))
        assertEquals(17, liveLabel("x".repeat(24)).length)
    }

    @Test
    fun anAddressHasToBeAPlainHttpsUrl() {
        assertEquals("https://laptop.example:8444/theme.json", parseLiveUrl(" https://laptop.example:8444/theme.json ").toString())
        assertNull(parseLiveUrl("http://laptop.example/theme.json"))
        assertNull(parseLiveUrl("ftp://laptop.example/theme.json"))
        assertNull(parseLiveUrl("laptop.example/theme.json"))
        assertNull(parseLiveUrl("https://user:pw@laptop.example/theme.json"))
        assertNull(parseLiveUrl("https:///theme.json"))
        assertNull(parseLiveUrl("https://laptop.example/a b.json"))
        assertNull(parseLiveUrl("https://laptop.example/" + "x".repeat(400)))
        assertNull(parseLiveUrl(""))
    }
}
