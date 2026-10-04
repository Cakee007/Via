package dev.ujhhgtg.via.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import dev.ujhhgtg.via.ui.dp
import java.io.File
import java.util.Locale

/**
 * z8/w3 icon helpers for the favorite editors: the cache folder (w3.m), the
 * url key (w3.r), disk decode with the center-crop/round pass (w3.i/t/s),
 * the letter tile fallback (w3.h/q), and the copy/delete of custom icons
 * (w3.a/e). The homepage renderer reads the same files (w3.n names).
 */
object HomeFavoriteIcons {
    /** w3.m: the icon cache directory under filesDir/icon. */
    fun directory(context: Context): File = File(context.filesDir, "icon").apply { mkdirs() }

    /** w3.r: the icon key; http(s) keeps the authority, everything else md5-hashes the lowercase url. */
    fun key(url: String?): String {
        if (url.isNullOrEmpty()) return "untitled"
        if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
            val start = url.indexOf("://") + 3
            val slash = url.indexOf('/', start)
            val end = if (slash >= 0) slash else url.indexOf('?', start).takeIf { it >= 0 } ?: url.length
            if (end > start) return url.substring(start, end).lowercase(Locale.ROOT)
        }
        return HomeDesign.md5(url.lowercase(Locale.ROOT))
    }

    /** w3.i+t: decode the stored icon, center-cropped to size and rounded. */
    fun load(context: Context, url: String?, size: Int, radius: Int): Bitmap? {
        return load(context, url, size, size, radius)
    }

    fun load(context: Context, url: String?, width: Int, height: Int, radius: Int): Bitmap? =
        loadFile(File(directory(context), key(url) + ".png").path, width, height, radius)

    fun loadFile(path: String, width: Int, height: Int, radius: Int): Bitmap? {
        if (!File(path).isFile) return null
        return try {
            val decoded = File(path).inputStream().use { BitmapFactory.decodeStream(it) } ?: return null
            rounded(decoded, width, height, radius)
        } catch (error: Exception) {
            android.util.Log.w("Via", "Cannot decode favorite icon", error)
            null
        }
    }

    /** z8.w3.p: the 12dp inset is part of the drawable, not ImageView padding. */
    fun placeholder(context: Context, radius: Int): android.graphics.drawable.Drawable {
        val surface = android.graphics.drawable.GradientDrawable().apply {
            setColor(0x20c0c0c0); cornerRadius = radius.toFloat()
            setStroke(context.dp(1f), 0x40000000, context.dp(4f).toFloat(), context.dp(4f).toFloat())
        }
        val plus = ContextCompat.getDrawable(context, dev.ujhhgtg.via.R.drawable.plus)!!.mutate().apply {
            setTint(dev.ujhhgtg.via.settings.settingsColor(context, dev.ujhhgtg.via.R.attr.viaDisabledTextColor, Color.GRAY))
        }
        return android.graphics.drawable.LayerDrawable(arrayOf(surface, plus)).apply {
            val inset = context.dp(12f); setLayerInset(1, inset, inset, inset, inset)
        }
    }

    /** w3.q: the stored icon, else the letter tile colored by the favorite url hash. */
    fun loadOrTile(context: Context, url: String?, title: String?, size: Int, radius: Int): Bitmap {
        load(context, url, size, radius)?.let { return it }
        // w3.q calls i0.f, then i0.b (which applies i0.f again) for an empty title.
        val host = dev.ujhhgtg.via.browser.DocumentPolicy.host(url.orEmpty())
        val letter = title?.takeIf { it.isNotEmpty() }?.let { String(Character.toChars(it.codePointAt(0))) }
            ?: dev.ujhhgtg.via.browser.DocumentPolicy.navigationDomain(host)
        // b0.y/z uses +100 and exchanges the middle/last channels; r8's homepage palette differs.
        val hash = HomeDesign.md5(url.orEmpty())
        val red = hash.take(3).toInt(16) % 128 + 100
        val green = hash.takeLast(3).toInt(16) % 128 + 100
        val blue = hash.substring(hash.length / 2 - 3, hash.length / 2).toInt(16) % 128 + 100
        return tile(context, letter, Color.rgb(red, green, blue), size, radius)
    }

    /** w3.a: move the picked cache file over the favorite's icon slot. */
    fun adoptCache(context: Context, cachePath: String?, url: String?): Boolean {
        if (cachePath.isNullOrEmpty() || url.isNullOrEmpty()) return false
        val directory = File(context.filesDir, "icon")
        if (directory.exists() && !directory.isDirectory && !directory.delete()) return false
        if (!directory.exists() && !directory.mkdirs()) return false
        val cache = File(cachePath)
        if (!cache.exists() || cache.isDirectory) return false
        // w3.a requires w3.c to normalize the chosen image before replacing the old icon.
        if (!normalizeStoredFile(cache)) return false
        val destination = File(directory, key(url) + ".png")
        return !(destination.exists() && !destination.delete()) && cache.renameTo(destination)
    }

    /** w3.e: drop the favorite's custom icon. */
    fun remove(context: Context, url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        val file = File(directory(context), key(url) + ".png")
        return !file.exists() || file.delete()
    }

    /** w3.c/s is shared by picked favorite images and downloaded touch icons. */
    internal fun normalizeStoredFile(file: File): Boolean {
        return !(!file.exists() || file.isDirectory) && try {
            val source = file.inputStream().use { BitmapFactory.decodeStream(it) } ?: return false
            val width = source.width
            val height = source.height
            val cropSize = minOf(width, height)
            val scale = minOf(144f / cropSize, 1f)
            val bitmap = if (scale < 1f) Bitmap.createBitmap(source, 0, 0, width, height,
                Matrix().apply { postScale(scale, scale) }, true) else source
            val size = if (scale < 1f) 144 else cropSize
            val output = createBitmap(size, size, Bitmap.Config.RGB_565)
            val canvas = Canvas(output)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { isAntiAlias = true }
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(bitmap, size / 2f - width * scale / 2f, size / 2f - height * scale / 2f, paint)
            canvas.save()
            bitmap.recycle()
            file.outputStream().use { output.compress(Bitmap.CompressFormat.PNG, 100, it); it.flush() }
            output.recycle()
            true
        } catch (error: Exception) {
            android.util.Log.w("Via", "Cannot normalize favorite icon", error)
            false
        }
    }

    /** w3.h: the letter tile with the rounded background. */
    private fun tile(context: Context, letter: String, color: Int, size: Int, radius: Int): Bitmap {
        val bitmap = createBitmap(size, size)
        bitmap.eraseColor(0)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = color
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawRoundRect(rect, radius.toFloat(), radius.toFloat(), paint)
        paint.color = Color.WHITE
        paint.textSize = maxOf((16f * context.resources.displayMetrics.density).toInt(), size / 4).toFloat()
        val bounds = Rect()
        paint.getTextBounds(letter, 0, letter.length, bounds)
        // w3.h performs integer division before converting the baseline coordinates to float.
        canvas.drawText(letter, ((size - bounds.width()) / 2).toFloat(), ((size + bounds.height()) / 2).toFloat(), paint)
        return bitmap
    }

    /** w3.t: center-crop, scale and round into an ARGB_8888 bitmap. */
    private fun rounded(source: Bitmap, width: Int, height: Int, radius: Int): Bitmap? {
        if (source.isRecycled || width <= 0 || height <= 0) return null
        val ratio = width.toFloat() / height
        var scaled = source
        var targetWidth: Int
        var targetHeight: Int
        if (source.width / source.height.toFloat() >= ratio) {
            targetWidth = (ratio * source.height).toInt(); targetHeight = source.height
        } else {
            targetHeight = (source.width / ratio).toInt(); targetWidth = source.width
        }
        val shrink = minOf(height.toFloat() / targetHeight, 1f)
        if (shrink < 1f) {
            val matrix = Matrix(); matrix.postScale(shrink, shrink)
            scaled = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }
        // w3.t (also checked against smali): after shrinking, the output keeps
        // the requested dimensions; only a smaller source uses its crop size.
        if (shrink < 1f) { targetWidth = width; targetHeight = height }
        val output = createBitmap(targetWidth, targetHeight)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(0)
        canvas.drawRoundRect(RectF(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat()), radius.toFloat(), radius.toFloat(), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(
            scaled,
            targetWidth / 2f - source.width * shrink / 2f,
            targetHeight / 2f - source.height * shrink / 2f,
            paint,
        )
        canvas.save()
        scaled.recycle()
        return output
    }
}
