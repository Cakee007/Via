package dev.ujhhgtg.via.records

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.dp

/** com.tuyafeng.support.widget.a0: wrap-content actions and a two physical pixel rule. */
internal class RecordsActionBar(context: Context) : LinearLayout(context) {
    data class Action(val label: String, val enabled: Boolean = true, val destructive: Boolean = false, val click: (View) -> Unit)
    private val rule = Paint().apply { color = 0x30808080 }
    init { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; minimumHeight = context.dp(48f); setWillNotDraw(false) }

    fun show(left: List<Action> = emptyList(), right: List<Action> = emptyList()) {
        removeAllViews()
        left.forEach { addAction(it) }
        addView(View(context), LayoutParams(0, context.dp(48f), 1f))
        right.forEach { addAction(it) }
    }

    private fun addAction(action: Action) {
        addView(TextView(context).apply {
            text = action.label; gravity = Gravity.CENTER
            minHeight = context.dp(48f)
            setPadding(context.dp(16f), 0, context.dp(16f), 0)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
            typeface = BrowserPreferences(context).selectedTypeface()
            val color = if (action.destructive && action.enabled) context.getColor(R.color.via_warning) else recordColor(context, R.attr.viaPrimaryTextColor)
            setTextColor(if (action.enabled) color else (color and 0x00ffffff) or 0x80000000.toInt())
            background = ContextCompat.getDrawable(context, R.drawable.rounded_rect_ripple)
            isEnabled = action.enabled; isClickable = true; isFocusable = true
            setOnClickListener(action.click)
        }, LayoutParams(-2, -2))
    }
    override fun onDraw(canvas: Canvas) { super.onDraw(canvas); canvas.drawRect(0f, 0f, width.toFloat(), 2f, rule) }
}

internal fun recordColor(context: Context, attribute: Int, fallback: Int = Color.BLACK): Int {
    val attributes = context.obtainStyledAttributes(intArrayOf(attribute))
    return try { attributes.getColor(0, fallback) } finally { attributes.recycle() }
}
