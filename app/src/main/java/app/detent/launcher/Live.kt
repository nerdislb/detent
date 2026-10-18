package app.detent.launcher

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.pow

/**
 * A theme that follows another computer. The laptop publishes its colours as JSON in the shape of Flux's
 * flux.theme body: a name, an optional mode, and a colors map of "#rrggbb" values.
 */
class LiveTheme(
    val name: String,
    val light: Boolean,
    val bg: Long,
    val fg: Long,
    val accent: Long,
    val hand: Long,
    val wall: LiveWallRef? = null,
)

/** The wallpaper a live theme points at: a path next to the theme file, and the SHA-256 of the image. */
class LiveWallRef(val path: String, val sha: String)

// Relative, no leading slash, no "..". A path like "//host/x" would otherwise name another host.
private val WALL_PATH = Regex("^(?!/)(?!.*\\.\\.)[A-Za-z0-9._/-]{1,200}$")
private val SHA256 = Regex("^[0-9a-f]{64}$")

private fun readWall(json: JSONObject): LiveWallRef? {
    val w = json.optJSONObject("wallpaper") ?: return null
    val path = w.optString("path")
    val sha = w.optString("sha256").lowercase()
    return if (WALL_PATH.matches(path) && SHA256.matches(sha)) LiveWallRef(path, sha) else null
}

/** Where [path] lives next to [base], or null when it would leave the host that served the theme. */
fun resolveWallUrl(base: URL, path: String): URL? {
    if (!WALL_PATH.matches(path)) return null
    val u = runCatching { URL(base, path) }.getOrNull() ?: return null
    val same = u.protocol == "https" && u.host == base.host && u.port == base.port
    return if (same) u else null
}

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
    LiveTheme(name, light, bg, fg, accent, mixRgb(fg, bg, .22), readWall(json))
} catch (e: Exception) {
    null
}

/** The address of the theme file, or null when it is not a plain https address. */
fun parseLiveUrl(text: String): URL? {
    val s = text.trim()
    if (s.length > 300 || s.any { it.isWhitespace() }) return null
    val u = runCatching { URL(s) }.getOrNull() ?: return null
    return u.takeIf { it.protocol == "https" && it.host.isNotEmpty() && it.userInfo == null && it.ref == null }
}

/** Name on the dial. The tilde keeps a live theme from being mistaken for the built-in theme of the same name. */
fun liveLabel(name: String) = if (name == "laptop") name else "~" + name.take(16)

/** The theme dial entry that follows the laptop. It is always the last one. */
object Live {
    const val GROUP = "laptop"

    val installed get() = THEMES.lastOrNull()?.group == GROUP

    /** Index of the live entry, or -1 when none is set up. */
    val index get() = if (installed) THEMES.lastIndex else -1

    /** [wall] is the path of the saved wallpaper, or null for the dot-hand art. */
    fun install(t: LiveTheme, wall: String? = null) {
        val def = ThemeDef(liveLabel(t.name), GROUP, t.light, t.bg, t.fg, t.accent, wall, t.hand)
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

    /**
     * Downloads the wallpaper to [dest] when its SHA-256 is [LiveWallRef.sha]. Anything else leaves no file.
     * This blocks, so call it off the main thread.
     */
    fun fetchWall(ref: LiveWallRef, dest: File): Boolean {
        val target = resolveWallUrl(url, ref.path) ?: return false
        val c = target.openConnection() as? HttpURLConnection ?: return false
        return try {
            c.connectTimeout = 4000
            c.readTimeout = 10_000
            c.instanceFollowRedirects = false
            if (c.responseCode != 200) return false
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0
            dest.outputStream().use { out ->
                c.inputStream.use { input ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        size += n
                        check(size <= MAX_WALL_BYTES) { "wallpaper too large" }
                        digest.update(buf, 0, n)
                        out.write(buf, 0, n)
                    }
                }
            }
            val got = digest.digest().joinToString("") { "%02x".format(it) }
            (got == ref.sha).also { if (!it) dest.delete() }
        } catch (e: Exception) {
            dest.delete()
            false
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
        const val MAX_WALL_BYTES = 12 * 1024 * 1024
    }
}
