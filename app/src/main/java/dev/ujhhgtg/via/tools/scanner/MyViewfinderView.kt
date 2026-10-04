package dev.ujhhgtg.via.tools.scanner

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.ViewfinderView

/** Kotlin translation of com.tuyafeng.scanner.MyViewfinderView's drawing routine. */
class MyViewfinderView(context: Context, attrs: AttributeSet?) : ViewfinderView(context, attrs) {
    private val cornerFraction = .06f
    private val cornerWidth = dip(2f)
    private var lineOffset = 0
    private val lineHeight = dip(3f)
    private val lineStep = dip(2f)
    private val stops = floatArrayOf(0f, .5f, 1f)
    private val colors = intArrayOf(0x00ffffff, -1, 0x00ffffff)
    private fun dip(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    // The original MyViewfinderView allocates the gradient shader and result points while drawing.
    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        refreshSizes()
        val rect = framingRect ?: return
        val preview = previewSize ?: return
        paint.color = -1
        paint.style = Paint.Style.STROKE
        canvas.drawRect(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat(), paint)
        paint.style = Paint.Style.FILL
        val left = rect.left.toFloat(); val top = rect.top.toFloat(); val right = rect.right.toFloat(); val bottom = rect.bottom.toFloat()
        canvas.drawRect(left, top, left + rect.width() * cornerFraction, top + cornerWidth, paint)
        canvas.drawRect(left, top, left + cornerWidth, top + rect.height() * cornerFraction, paint)
        canvas.drawRect(right - rect.width() * cornerFraction, top, right, top + cornerWidth, paint)
        canvas.drawRect(right - cornerWidth, top, right, top + rect.height() * cornerFraction, paint)
        canvas.drawRect(left, bottom - cornerWidth, left + rect.width() * cornerFraction, bottom, paint)
        canvas.drawRect(left, bottom - rect.height() * cornerFraction, left + cornerWidth, bottom, paint)
        canvas.drawRect(right - rect.width() * cornerFraction, bottom - cornerWidth, right, bottom, paint)
        canvas.drawRect(right - cornerWidth, bottom - rect.height() * cornerFraction, right, bottom, paint)
        paint.color = if (resultBitmap != null) resultColor else maskColor
        canvas.drawRect(0f, 0f, width.toFloat(), top, paint)
        canvas.drawRect(0f, top, left, bottom + 1, paint)
        canvas.drawRect(right + 1, top, width.toFloat(), bottom + 1, paint)
        canvas.drawRect(0f, bottom + 1, width.toFloat(), height.toFloat(), paint)
        val bitmap = resultBitmap
        if (bitmap != null) {
            paint.alpha = 160
            canvas.drawBitmap(bitmap, null as Rect?, rect, paint)
        } else {
            lineOffset = (lineOffset + lineStep).toInt()
            if (lineOffset > rect.height()) lineOffset = 0
            paint.shader = LinearGradient(left, top + lineOffset, right, top + lineOffset, colors, stops, Shader.TileMode.CLAMP)
            canvas.drawRect(left, top + lineOffset, right, top + lineOffset + lineHeight, paint)
            paint.shader = null
            // Confirmed div-float in the original smali; JADX drops the casts.
            val scaleX = rect.width().toFloat() / preview.width
            val scaleY = rect.height().toFloat() / preview.height
            val points = possibleResultPoints
            val previous = lastPossibleResultPoints
            if (points.isEmpty()) lastPossibleResultPoints = null
            else {
                possibleResultPoints = ArrayList<ResultPoint>(5)
                lastPossibleResultPoints = points
                paint.alpha = 160
                paint.color = resultPointColor
                points.forEach { canvas.drawCircle((it.x * scaleX).toInt() + left, (it.y * scaleY).toInt() + top, 6f, paint) }
            }
            if (previous != null) {
                paint.alpha = 80
                paint.color = resultPointColor
                previous.forEach { canvas.drawCircle((it.x * scaleX).toInt() + left, (it.y * scaleY).toInt() + top, 3f, paint) }
            }
        }
        postInvalidateDelayed(16, rect.left, rect.top, rect.right, rect.bottom)
    }
}
