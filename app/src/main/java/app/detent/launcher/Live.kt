package app.detent.launcher

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.pow

/**
 * A theme that follows another computer. The laptop publishes its colours as JSON in the shape of Flux's
 * flux.theme body: a name, an optional mode, and a colors map of "#rrggbb" values.
 */
class LiveTheme(val name: String, val light: Boolean, val bg: Long, val fg: Long, val accent: Long, val hand: Long)

private val HEX = Regex("^#?([0-9a-fA-F]{6})(?:[0-9a-fA-F]{2})?$")

private fun hex(v: Any?): Long? {
    val m = HEX.matchEntire((v as? String)?.trim() ?: return null) ?: return null
    return 0xFF000000L or m.groupValues[1].toLong(16)
}

private fun channel(c: Long, shift: Int) = ((c shr shift) and 0xFF).toInt()

private fun lin(v: Int): Double {
    val s = v / 255.0
    return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
}

private fun luminance(c: Long) = .2126 * lin(channel(c, 16)) + .7152 * lin(channel(c, 8)) + .0722 * lin(channel(c, 0))

private fun contrast(a: Long, b: Long): Double {
    val x = luminance(a)
    val y = luminance(b)
    return (max(x, y) + .05) / (minOf(x, y) + .05)
}

/** Mixes [a] into [b]. t = 1 gives [a]. */
private fun mixRgb(a: Long, b: Long, t: Double): Long {
    fun ch(shift: Int) = (channel(a, shift) * t + channel(b, shift) * (1 - t)).toInt().coerceIn(0, 255)
    return 0xFF000000L or (ch(16).toLong() shl 16) or (ch(8).toLong() shl 8) or ch(0).toLong()
}

/**
 * Reads a theme body. Returns null when it has no readable background and foreground, so a broken file never
 * replaces the colours that are showing.
 */
fun parseLiveTheme(body: String): LiveTheme? = try {
    val json = JSONObject(body)
    val colors = json.optJSONObject("colors") ?: return null
    val bg = hex(colors.opt("background")) ?: return null
    val fg = hex(colors.opt("foreground")) ?: return null
    // The accent has to stand out from the background. Otherwise fall back to the next colour that does.
    val accent = listOf("accent", "blue", "orange", "green").firstNotNullOfOrNull { k ->
        hex(colors.opt(k))?.takeIf { contrast(it, bg) >= 2.2 }
    } ?: fg
    val light = when (json.optString("mode")) {
        "light" -> true
        "dark" -> false
        else -> luminance(bg) > .4
    }
    val name = json.optString("name").trim().take(24).ifEmpty { "laptop" }
    // The dot-hand art sits quietly behind the dial, so its dots only lift a little off the background.
    LiveTheme(name, light, bg, fg, accent, mixRgb(fg, bg, .22))
} catch (e: Exception) {
    null
}

/** Name on the dial, so a live theme can't be mistaken for the built-in theme of the same name. */
private fun LiveTheme.label() = if (name == "laptop") name else "laptop · $name".take(17)

/** The theme dial entry that follows the laptop. It is always the last one. */
object Live {
    const val GROUP = "laptop"

    val installed get() = THEMES.lastOrNull()?.group == GROUP

    /** Index of the live entry, or -1 when none is set up. */
    val index get() = if (installed) THEMES.lastIndex else -1

    fun install(t: LiveTheme) {
        val def = ThemeDef(t.label(), GROUP, t.light, t.bg, t.fg, t.accent, null, t.hand)
        if (installed) {
            THEMES[THEMES.lastIndex] = def
            PALETTES[PALETTES.lastIndex] = Palette(def)
        } else {
            THEMES.add(def)
            PALETTES.add(Palette(def))
        }
    }

    fun remove() {
        if (!installed) return
        THEMES.removeAt(THEMES.lastIndex)
        PALETTES.removeAt(PALETTES.lastIndex)
    }
}

sealed interface Fetch {
    class Updated(val theme: LiveTheme, val body: String) : Fetch
    data object Unchanged : Fetch
    data object Failed : Fetch
}

/**
 * Reads the theme from [url], which has to be https. The ETag from the last answer goes back as If-None-Match,
 * so an unchanged theme costs a few bytes. This blocks, so call it off the main thread.
 */
class LiveClient(private val url: URL) {
    private var etag: String? = null

    fun fetch(): Fetch {
        val c = url.openConnection() as? HttpURLConnection ?: return Fetch.Failed
        return try {
            c.connectTimeout = 4000
            c.readTimeout = 4000
            c.instanceFollowRedirects = false
            c.setRequestProperty("Accept", "application/json")
            etag?.let { c.setRequestProperty("If-None-Match", it) }
            when (c.responseCode) {
                304 -> Fetch.Unchanged
                200 -> {
                    val body = readCapped(c)
                    val theme = parseLiveTheme(body) ?: return Fetch.Failed
                    etag = c.getHeaderField("ETag")
                    Fetch.Updated(theme, body)
                }
                else -> Fetch.Failed
            }
        } catch (e: Exception) {
            Fetch.Failed
        } finally {
            c.disconnect()
        }
    }

    /** A theme is a few kilobytes. Anything bigger is not one. */
    private fun readCapped(c: HttpURLConnection): String {
        val out = ByteArrayOutputStream()
        c.inputStream.use { input ->
            val buf = ByteArray(4096)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                check(out.size() <= MAX_BYTES) { "theme too large" }
            }
        }
        return out.toString(Charsets.UTF_8)
    }

    private companion object {
        const val MAX_BYTES = 64 * 1024
    }
}
