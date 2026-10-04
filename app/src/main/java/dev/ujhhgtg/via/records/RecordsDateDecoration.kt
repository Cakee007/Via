package dev.ujhhgtg.via.records

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** v8.a/b and c6.b: local-day group labels, 32dp tall, retained while scrolling. */
internal class RecordsDateDecoration(private val adapter: RecordsRowAdapter) : RecyclerView.ItemDecoration() {
    private fun day(millis: Long): Long = Calendar.getInstance().apply { timeInMillis = millis; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private fun starts(position: Int) = position == 0 || day(adapter.rows()[position - 1].timestamp) != day(adapter.rows()[position].timestamp)
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildAdapterPosition(view)
        if (position in adapter.rows().indices && starts(position)) outRect.top = parent.context.dp(32f)
    }
    override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val context = parent.context; val height = context.dp(32f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 11f, context.resources.displayMetrics)
            typeface = BrowserPreferences(context).selectedTypeface()
        }
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index); val position = parent.getChildAdapterPosition(child)
            val row = adapter.rows().getOrNull(position) ?: continue
            if (index != 0 && !starts(position)) continue
            val nextStarts = position + 1 in adapter.rows().indices && starts(position + 1)
            val top = if (index == 0) (if (nextStarts) minOf(0, child.bottom - height) else 0) else child.top - height
            val today = day(System.currentTimeMillis()); val yesterday = Calendar.getInstance().apply { timeInMillis = today; add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
            val label = when (val date = day(row.timestamp)) {
                today -> context.getString(R.string.today)
                yesterday -> context.getString(R.string.yesterday)
                else -> {
                    val sameYear = Calendar.getInstance().get(Calendar.YEAR) == Calendar.getInstance().apply { timeInMillis = date }.get(Calendar.YEAR)
                    SimpleDateFormat(context.getString(if (sameYear) R.string.same_year_date_format else R.string.default_date_format), Locale.getDefault()).format(Date(date))
                }
            }
            if (index == 0 && child.top < height) {
                paint.color = recordColor(context, R.attr.viaBackgroundColor, android.graphics.Color.WHITE)
                canvas.drawRect(0f, top.toFloat(), parent.width.toFloat(), (top + height).toFloat(), paint)
            }
            paint.color = recordColor(context, R.attr.viaSecondaryTextColor)
            paint.textAlign = if (parent.layoutDirection == View.LAYOUT_DIRECTION_RTL) Paint.Align.RIGHT else Paint.Align.LEFT
            val x = if (parent.layoutDirection == View.LAYOUT_DIRECTION_RTL) parent.width - context.dp(18f) else context.dp(18f)
            canvas.drawText(label, x.toFloat(), top + height / 2f - (paint.descent() + paint.ascent()) / 2f + context.dp(4f), paint)
        }
    }
}
