package dev.ujhhgtg.via.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.LruCache
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import java.io.File

/** z8.x0/y0: website favicons, separate from w3's files/icon favorite/touch images. */
object HomeIcons {
    private val cache = object : LruCache<String, Bitmap>(
        maxOf(2048, (Runtime.getRuntime().maxMemory() / 1024).toInt() / 64),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            if (value.isRecycled) 0 else maxOf(1, value.byteCount / 1024)
    }

    /** y0.b also accepts bare host keys (password records). Slash precedes the '?' fallback. */
    private fun authority(url: String): String {
        val scheme = url.indexOf("://")
        val start = if (scheme >= 0) scheme + 3 else url.indexOf(':') + 1
        val slash = url.indexOf('/', start)
        val end = if (slash >= 0) slash else url.indexOf('?', start).takeIf { it >= 0 } ?: url.length
        return url.substring(start, end)
    }

    /** y0.d/c1.w: external favicons/<MD5(authority)>, with internal fallback and no extension. */
    fun file(context: Context, url: String?): File? {
        if (url.isNullOrEmpty()) return null
        val key = authority(url).takeIf { it.isNotEmpty() } ?: return null
        val directory = context.getExternalFilesDir("favicons") ?: File(context.filesDir, "favicons")
        if (!directory.exists() && !directory.mkdirs()) return null
        return File(directory, HomeDesign.md5(key))
    }

    /** x0.d: the full disk path is the LRU key; a live cached bitmap precedes disk lookup. */
    fun load(context: Context, url: String?): Bitmap? {
        val file = file(context, url) ?: return null
        cache.get(file.path)?.takeUnless(Bitmap::isRecycled)?.let { return it }
        val bitmap = decode(file, dp(context, 24)) ?: return null
        if (!bitmap.isRecycled) cache.put(file.path, bitmap)
        return bitmap
    }

    /** y0.f/g writes the rounded copy but neither changes x0's LRU nor recycles WebView's bitmap. */
    fun save(context: Context, url: String?, bitmap: Bitmap): Boolean {
        val file = file(context, url) ?: return false
        val processed = rounded(bitmap, dp(context, 24), dp(context, 4)) ?: return false
        val directory = file.parentFile ?: return false
        if (!directory.exists() && !directory.mkdirs()) return false
        if (file.exists() && !file.delete()) return false
        return try {
            file.outputStream().use { processed.compress(Bitmap.CompressFormat.PNG, 100, it); it.flush() }
            true
        } catch (error: Exception) {
            android.util.Log.w("Via", "Cannot save favicon", error)
            false
        }
    }

    fun clearMemory() = cache.evictAll()
    /** y0.e: never enlarge a small icon; scaled sources keep the requested output dimensions. */
    private fun rounded(source: Bitmap, size: Int, radius: Int): Bitmap? {
        if (source.isRecycled) return null
        val width = source.width
        val height = source.height
        val cropSize = minOf(width, height)
        val scale = minOf(size.toFloat() / cropSize, 1f)
        val bitmap = if (scale < 1f) {
            val matrix = Matrix().apply { postScale(scale, scale) }
            Bitmap.createBitmap(source, 0, 0, width, height, matrix, true)
        } else source
        val outputSize = if (scale < 1f) size else cropSize
        val output = createBitmap(outputSize, outputSize)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(0)
        canvas.drawRoundRect(RectF(0f, 0f, outputSize.toFloat(), outputSize.toFloat()), radius.toFloat(), radius.toFloat(), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, outputSize / 2f - width * scale / 2f, outputSize / 2f - height * scale / 2f, paint)
        canvas.save()
        return output
    }

    /** y0.a -> i.c/a/b: power-of-two decode, then center crop and downscale, never fit/letterbox. */
    private fun decode(file: File, size: Int): Bitmap? {
        if (!file.exists()) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        var sample = 1
        if (options.outWidth > size || options.outHeight > size) {
            val halfWidth = options.outWidth / 2
            val halfHeight = options.outHeight / 2
            while (halfWidth / sample >= size && halfHeight / sample >= size) sample *= 2
        }
        options.inSampleSize = sample
        options.inJustDecodeBounds = false
        val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return null
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
        val cropSize = minOf(bitmap.width, bitmap.height)
        val cropped = Bitmap.createBitmap(bitmap, (bitmap.width - cropSize) / 2, (bitmap.height - cropSize) / 2, cropSize, cropSize)
        return if (cropSize > size) cropped.scale(size, size) else cropped
    }
    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density + .5f).toInt()
}
