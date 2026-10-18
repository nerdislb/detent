package app.detent.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The user's own wallpaper. Detent keeps a screen-sized copy, because the picker grants access only for a short time. */
object CustomWallpaper {
    private fun dir(ctx: Context) = File(ctx.filesDir, "walls").apply { mkdirs() }

    /**
     * Decodes [uri], crops the centre to the shape of a [w] x [h] screen, and saves it as a new file.
     * The image is never larger than the screen. This blocks, so call it off the main thread.
     */
    fun save(ctx: Context, uri: Uri, w: Int, h: Int): File {
        val file = File(dir(ctx), "${System.currentTimeMillis()}.jpg")
        crop(ImageDecoder.createSource(ctx.contentResolver, uri), w, h, file)
        return file
    }

    /** The same crop for an image file, saved to [dest]. Used by the live wallpaper, which lives outside this folder. */
    fun crop(src: File, w: Int, h: Int, dest: File) = crop(ImageDecoder.createSource(src), w, h, dest)

    private fun crop(source: ImageDecoder.Source, w: Int, h: Int, dest: File) {
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val iw = info.size.width
            val ih = info.size.height
            val scale = min(1f, max(w.toFloat() / iw, h.toFloat() / ih))
            val sw = (iw * scale).roundToInt().coerceAtLeast(1)
            val sh = (ih * scale).roundToInt().coerceAtLeast(1)
            decoder.setTargetSize(sw, sh)
            // The crop applies to the scaled image.
            val cw = min(sw.toFloat(), sh * w.toFloat() / h).roundToInt().coerceAtLeast(1)
            val ch = min(sh.toFloat(), sw * h.toFloat() / w).roundToInt().coerceAtLeast(1)
            val left = (sw - cw) / 2
            val top = (sh - ch) / 2
            decoder.crop = Rect(left, top, left + cw, top + ch)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        try {
            dest.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        } finally {
            bitmap.recycle()
        }
    }

    /** Deletes every saved wallpaper except [keep]. */
    fun clean(ctx: Context, keep: String?) {
        dir(ctx).listFiles()?.forEach { if (it.path != keep) it.delete() }
    }
}
