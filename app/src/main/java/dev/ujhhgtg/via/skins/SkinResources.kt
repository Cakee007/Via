package dev.ujhhgtg.via.skins

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableContainer
import android.util.LruCache
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import java.io.File

/** lb.a/b: a process-wide ic_*.png override, with the original 32-entry drawable cache. */
object SkinResources {
    private var directory: File? = null
    private val cache = LruCache<String, Drawable.ConstantState>(32)

    /** c8.s6.V1 initializes this before constructing browser controls; selection takes effect on restart. */
    fun load(context: Context, skinName: String) {
        directory = skinName.takeIf(String::isNotEmpty)?.let { File(SkinRepository.directory(context), it) }
    }

    /** The explicit key overload handles icons sharing a packaged fallback, such as forward/go. */
    fun drawable(context: Context, fallback: Int, iconKey: String? = SkinIconKeys.forDrawable(fallback)): Drawable? =
        iconKey?.let(::icon) ?: if (fallback != 0) ContextCompat.getDrawable(context, fallback) else null

    fun icon(key: String): Drawable? {
        val root = directory ?: return null
        if (!key.startsWith("ic_")) return null
        val drawable = cache[key]?.newDrawable() ?: decode(File(root, "$key.png")) ?: return null
        drawable.constantState?.let { cache.put(key, it) }
        return if (drawable is DrawableContainer) drawable.constantState?.newDrawable() ?: drawable else drawable
    }

    /** z8.i.a/c: power-of-two sampling, followed by the original centered square crop. */
    private fun decode(file: File): Drawable? {
        if (!file.exists()) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        var sample = 1
        if (options.outWidth > 96 || options.outHeight > 96) {
            val halfWidth = options.outWidth / 2
            val halfHeight = options.outHeight / 2
            while (halfWidth / sample >= 96 && halfHeight / sample >= 96) sample *= 2
        }
        options.inSampleSize = sample
        options.inJustDecodeBounds = false
        val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return null
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
        val side = minOf(bitmap.width, bitmap.height)
        val cropped = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        val result = if (side > 96) cropped.scale(96, 96) else cropped
        @SuppressLint("UseKtx") // the ktx function doesn't allow null resources
        return BitmapDrawable(null, result)
    }
}

/** Keeps each ImageView's existing tint, padding, accessibility and click behavior. */
fun ImageView.setSkinImageResource(resource: Int, iconKey: String? = SkinIconKeys.forDrawable(resource)) {
    setImageDrawable(SkinResources.drawable(context, resource, iconKey))
}
