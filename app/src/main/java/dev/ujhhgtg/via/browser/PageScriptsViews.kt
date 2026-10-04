package dev.ujhhgtg.via.browser

import android.content.Context
import android.graphics.Typeface
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.settingsColor
import dev.ujhhgtg.via.ui.dp

/** mark.via.common.widget.SizedLinearLayoutManager.C1: clamp the measured list height itself. */
internal class PageScriptsLayoutManager(context: Context, minimumDivisor: Int) : LinearLayoutManager(context) {
    private val minimum = minOf(context.resources.displayMetrics.heightPixels / minimumDivisor, context.dp(200f))
    private val maximum = minOf(context.resources.displayMetrics.heightPixels / 2, context.dp(600f))
    override fun setMeasuredDimension(width: Int, height: Int) {
        var measured = if (minimum > 0 && height < minimum) minimum else height
        if (maximum > 0 && height > maximum) measured = maximum
        super.setMeasuredDimension(width, measured)
    }
}

/** h6.a.k truncates TypedValue dimensions; g6.y.h remains the rounded context.dp helper. */
internal fun Context.pageScriptsUnits(value: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

internal fun pageScriptsTitle(context: Context) = TextView(context).apply {
    setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_toolbar_title_size).toFloat())
    setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, 0))
    setText(R.string.settings_script)
    setSingleLine(); maxLines = 1; setLines(1); ellipsize = android.text.TextUtils.TruncateAt.END
    textDirection = View.TEXT_DIRECTION_LOCALE
    setTypeface(BrowserPreferences(context).selectedTypeface(), Typeface.BOLD)
}

/** r3.p applied by the two source row delegates. */
internal fun TextView.pageScriptsFadingText() {
    ellipsize = null; setSingleLine(true)
    setFadingEdgeLength(context.dp(24f)); isHorizontalFadingEdgeEnabled = true
    textDirection = View.TEXT_DIRECTION_LOCALE
    typeface = BrowserPreferences(context).selectedTypeface()
}

/** y5.a uses v5.c's 300ms listener; header arrow/add listeners are plain clicks. */
internal fun View.setPageScriptsRowClick(action: () -> Unit) {
    var last: Long? = null
    setOnClickListener {
        val now = SystemClock.elapsedRealtime()
        if (last == null || kotlin.math.abs(now - last!!) > 300L) { last = now; action() }
    }
    setOnLongClickListener { false }
}
