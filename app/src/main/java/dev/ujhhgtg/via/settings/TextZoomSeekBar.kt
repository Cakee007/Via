package dev.ujhhgtg.via.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.ContextThemeWrapper
import android.widget.SeekBar
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.reader.ReaderControls
import dev.ujhhgtg.via.ui.dp

/** mark.via.common.widget.w0: the global/default zoom marker is drawn behind the normal slider. */
internal class TextZoomSeekBar(context: Context) : SeekBar(ContextThemeWrapper(context, R.style.OriginalSeekbar)) {
    var highlightProgress: Int = -1
        set(value) { field = value; invalidate() }
    private val marker = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val accent = settingsColor(context, R.attr.viaAccentColor, 0xff000000.toInt())

    init {
        max = 30
        minimumHeight = context.dp(2f)
        ReaderControls.styleSeekBar(this)
        setPaddingRelative(context.dp(16f), 0, context.dp(16f), 0)
    }

    override fun onDraw(canvas: Canvas) {
        if (highlightProgress in 0..max && max > 0) {
            marker.color = if (highlightProgress <= progress) accent else 0x40808080
            val position = if (resources.configuration.layoutDirection == LAYOUT_DIRECTION_RTL) max - highlightProgress else highlightProgress
            val x = (position.toFloat() / max * (width - paddingLeft - paddingRight)).toInt() + paddingLeft
            val radius = thumb?.intrinsicWidth?.takeIf { it > 0 }?.div(3f) ?: context.dp(2f).toFloat()
            canvas.drawCircle(x.toFloat(), height / 2f, radius, marker)
        }
        super.onDraw(canvas)
    }
}
