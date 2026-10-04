package dev.ujhhgtg.via.downloads

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.settingsColor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** c6.b + v8.a/b: 32dp sticky day headings, with the download host's 6dp horizontal shift. */
internal class DownloadDateDecoration(private val context: Context, private val records: () -> List<DownloadRecord>) : RecyclerView.ItemDecoration() {
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int) = (density * value).toInt()
    private val height = dp(32)
    private val rtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    private val foreground = settingsColor(context, R.attr.viaSecondaryTextColor, 0xff444444.toInt())
    private val background = settingsColor(context, R.attr.viaBackgroundColor, -1)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.resources.getDimensionPixelSize(R.dimen.download_small_size).toFloat()
        typeface = BrowserPreferences(context).selectedTypeface()
        if (rtl) textAlign = Paint.Align.RIGHT
    }
    private fun day(seconds: Long) = Calendar.getInstance().apply {
        timeInMillis = seconds * 1000
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    private fun startsGroup(position: Int, rows: List<DownloadRecord>) = position == 0 || day(rows[position].createdAt) != day(rows[position - 1].createdAt)
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildAdapterPosition(view)
        val rows = records()
        if (position in rows.indices && startsGroup(position, rows)) outRect.top = height
    }
    override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val rows = records()
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            val position = parent.getChildAdapterPosition(child)
            if (position !in rows.indices) continue
            val first = index == 0
            if (!first && !startsGroup(position, rows)) continue
            var top = child.top - height
            var fade = 0f
            if (first) {
                top = parent.paddingTop
                if (position == rows.lastIndex || day(rows[position + 1].createdAt) != day(rows[position].createdAt)) top = minOf(top, child.bottom - height)
                fade = if (child.top >= 0) (1f - child.top.toFloat() / height).coerceIn(0f, 1f) else 1f
            }
            draw(canvas, title(rows[position].createdAt), parent.paddingLeft, parent.width - parent.paddingRight, top + child.translationY.toInt(), fade)
        }
    }
    private fun draw(canvas: Canvas, title: String, left: Int, right: Int, top: Int, fade: Float) {
        val shift = dp(6) * fade
        val width = paint.measureText(title)
        val radius = dp(18).toFloat()
        val inset = dp(9)
        val start = if (rtl) right - inset - radius - width - shift else left + inset + shift
        val end = if (rtl) right - inset - shift else left + width + radius + inset + shift
        paint.color = (background and 0xffffff) or ((fade * 255).toInt() shl 24)
        canvas.drawRoundRect(start, (top + dp(4)).toFloat(), end, (top + height).toFloat(), radius, radius, paint)
        paint.color = foreground
        val x = if (rtl) right - radius - shift else left + radius + shift
        val baseline = top + height / 2f - (paint.descent() + paint.ascent()) / 2 + dp(4)
        canvas.drawText(title, x, baseline, paint)
    }
    private fun title(seconds: Long): String {
        val date = day(seconds)
        val today = day(System.currentTimeMillis() / 1000)
        if (date == today) return context.getString(R.string.today)
        val yesterday = Calendar.getInstance().apply { timeInMillis = today; add(Calendar.DATE, -1) }.timeInMillis
        if (date == yesterday) return context.getString(R.string.yesterday)
        val sameYear = Calendar.getInstance().apply { timeInMillis = date }.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)
        return SimpleDateFormat(context.getString(if (sameYear) R.string.same_year_date_format else R.string.default_date_format), Locale.getDefault()).format(Date(date))
    }
}
