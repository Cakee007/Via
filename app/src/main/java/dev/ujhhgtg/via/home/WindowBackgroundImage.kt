package dev.ujhhgtg.via.home

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.core.graphics.withClip

/** mark.via.common.widget.o0: center-cropped pixels with a shareable bitmap ConstantState. */
internal class WindowBackgroundImage private constructor(private val state: ImageState) : Drawable() {
    constructor(resources: Resources, bitmap: Bitmap) : this(ImageState(bitmap, resources.displayMetrics.densityDpi))
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()

    override fun draw(canvas: Canvas) {
        if (state.bitmap.isRecycled || bounds.isEmpty) return
        canvas.withClip(bounds) {
            canvas.drawBitmap(state.bitmap, this@WindowBackgroundImage.matrix, this@WindowBackgroundImage.paint)
        }
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val bitmap = state.bitmap
        if (bounds.isEmpty || bitmap.width <= 0 || bitmap.height <= 0) { matrix.reset(); return }
        val scale = maxOf(bounds.width().toFloat() / bitmap.width, bounds.height().toFloat() / bitmap.height)
        matrix.setScale(scale, scale)
        matrix.postTranslate(bounds.left + (bounds.width() - bitmap.width * scale) / 2f,
            bounds.top + (bounds.height() - bitmap.height * scale) / 2f)
    }

    override fun getIntrinsicWidth() = state.bitmap.getScaledWidth(state.density)
    override fun getIntrinsicHeight() = state.bitmap.getScaledHeight(state.density)
    override fun getConstantState(): ConstantState = state
    override fun setAlpha(alpha: Int) { if (paint.alpha != alpha) { paint.alpha = alpha; invalidateSelf() } }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Android API")
    override fun getOpacity() = if (!state.bitmap.hasAlpha() && paint.alpha >= 255) PixelFormat.OPAQUE else PixelFormat.TRANSLUCENT

    private class ImageState(val bitmap: Bitmap, val density: Int) : ConstantState() {
        override fun getChangingConfigurations() = 0
        override fun newDrawable(): Drawable = WindowBackgroundImage(this)
        override fun newDrawable(resources: Resources?): Drawable = WindowBackgroundImage(ImageState(bitmap, resources?.displayMetrics?.densityDpi ?: 160))
    }
}
