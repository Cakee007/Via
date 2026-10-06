package dev.ujhhgtg.via.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.view.WindowManager
import androidx.core.graphics.scale
import java.io.File

/** j9.a image import/cache and d9.q/c8.s6 background decoding. */
object HomeBackground {
    private fun contentDirectory(context: Context) = context.getExternalFilesDir("content") ?: File(context.filesDir, "content")
    fun clearCache(context: Context) {
        contentDirectory(context).listFiles()?.filter { it.isFile && (it.name == "background.jpg" || it.name.startsWith("background-") && (it.extension == "jpg" || it.extension == "jpeg")) }?.forEach(File::delete)
    }
    /** j9/a: retain the imported original and cache a screen-sized JPEG at quality 92. */
    fun process(context: Context, source: File) {
        val bitmap = decode(context, source.path) ?: return
        try {
            clearCache(context)
            val output = File(contentDirectory(context).apply { mkdirs() }, "background.jpg")
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        } finally { bitmap.recycle() }
    }
    private fun decode(context: Context, path: String): Bitmap? {
        val screen = displaySize(context)
        val target = maxOf(screen.x, screen.y)
        return sampled(path, target, target)
    }
    private fun displaySize(context: Context) = Point().also { size ->
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealSize(size)
    }
    private fun sampled(path: String, width: Int, height: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        var sample = 1
        // z8.i.a uses both dimensions; long, narrow images must not be undersampled.
        if (width > 0 && height > 0) {
            while (options.outWidth / 2 / sample >= width && options.outHeight / 2 / sample >= height) sample *= 2
        }
        options.inJustDecodeBounds = false; options.inSampleSize = sample
        return BitmapFactory.decodeFile(path, options)
    }
    /** c8.s6.Ab / j9.a.b: native pages read the same cached image as the homepage. */
    internal fun imageFile(context: Context, backgroundPath: String?): File? {
        val source = backgroundPath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf(File::isFile) ?: return null
        return File(contentDirectory(context), "background.jpg").takeIf(File::isFile) ?: source
    }
    /** d9.q.b3/c8.s6.p6: cached JPEG is decoded directly; an uncached source uses z8.i.c. */
    private fun displayBitmap(context: Context, backgroundPath: String?): Bitmap? {
        val source = backgroundPath?.let(::File)?.takeIf(File::isFile) ?: return null
        val cached = File(contentDirectory(context), "background.jpg")
        if (cached.isFile) return BitmapFactory.decodeFile(cached.path)
        val screen = displaySize(context)
        val bitmap = sampled(source.path, screen.x, screen.y) ?: return null
        if (bitmap.width <= 0 || bitmap.height <= 0 || screen.x <= 0 || screen.y <= 0) return null
        val ratio = screen.x.toFloat() / screen.y
        return if (bitmap.width.toFloat() / bitmap.height > ratio) {
            val width = minOf((bitmap.height * ratio).toInt(), bitmap.width)
            val cropped = Bitmap.createBitmap(bitmap, (bitmap.width - width) / 2, 0, width, bitmap.height)
            if (bitmap.height > screen.y) cropped.scale(screen.x, screen.y) else cropped
        } else {
            val height = minOf((bitmap.width / ratio).toInt(), bitmap.height)
            val cropped = Bitmap.createBitmap(bitmap, 0, (bitmap.height - height) / 2, bitmap.width, height)
            if (bitmap.width > screen.x) cropped.scale(screen.x, screen.y) else cropped
        }
    }
    /** The raw image layer has no homepage color/filter baked into it. */
    internal fun createWindowImage(context: Context, backgroundPath: String?): Drawable? =
        displayBitmap(context, backgroundPath)?.let { WindowBackgroundImage(context.resources, it) }

    /** c8.s6.A3 / d9.q.e3: rebuild the screen-sized cache when the source survives but background.jpg was wiped. */
    internal fun regenerateCache(context: Context, backgroundPath: String?) {
        val source = backgroundPath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf(File::isFile) ?: return
        if (File(contentDirectory(context), "background.jpg").isFile) return
        process(context, source)
    }
}
