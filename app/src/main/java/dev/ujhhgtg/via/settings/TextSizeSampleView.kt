package dev.ujhhgtg.via.settings

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.TextView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.reader.ReaderControls
import dev.ujhhgtg.via.ui.dp
import java.util.Locale

/** ib.u: the source text-size range and preview model. */
class TextSizeSampleRow(id: Int, title: String, format: String, val sample: String,
    val value: Int, val minimum: Int, val maximum: Int, val step: Int, val baseline: Int,
) : SettingsRow(id, title, format)

/** ib.x view plus ib.w binding and stop/focus-loss persistence. */
@SuppressLint("ViewConstructor")
class TextSizeSampleView(context: Context, private val changed: (Int) -> Unit) : RelativeLayout(context) {
    private val title = TextView(context).apply {
        id = generateViewId(); setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, 0xff000000.toInt()))
        setSingleLine(); maxLines = 1; setLines(1); ellipsize = TextUtils.TruncateAt.END
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
        textDirection = TEXT_DIRECTION_LOCALE; typeface = BrowserPreferences(context).selectedTypeface()
    }
    private val value = TextView(context).apply {
        id = generateViewId(); minWidth = context.dp(32f)
        setTextColor(settingsColor(context, R.attr.viaSecondaryTextColor, 0xff444444.toInt()))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_row_title_size).toFloat())
        ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER; typeface = BrowserPreferences(context).selectedTypeface()
    }
    private val seek = SeekBar(ContextThemeWrapper(context, R.style.Seekbar)).apply {
        ReaderControls.styleSeekBar(this)
    }
    private val sample = TextView(context).apply {
        minHeight = context.dp(36f); maxLines = 3; ellipsize = TextUtils.TruncateAt.END
        setTextColor(settingsColor(context, R.attr.viaPrimaryTextColor, 0xff000000.toInt()))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.settings_toolbar_title_size).toFloat())
        textDirection = TEXT_DIRECTION_LOCALE; typeface = BrowserPreferences(context).selectedTypeface()
        val padding = context.dp(16f); setPadding(padding, padding, padding, padding)
        background = GradientDrawable().apply { setColor(0x30808080); cornerRadius = context.dp(8f).toFloat() }
    }
    init {
        setPadding(context.dp(16f), context.dp(20f), context.dp(16f), context.dp(20f))
        background = settingsRipple(context)
        addView(title, LayoutParams(-1, -2))
        addView(value, LayoutParams(-2, -2).apply { addRule(ALIGN_PARENT_START); addRule(BELOW, title.id); topMargin = context.dp(2f) })
        addView(seek, LayoutParams(-1, -2).apply {
            addRule(ALIGN_PARENT_END); addRule(END_OF, value.id); addRule(ALIGN_TOP, value.id)
            topMargin = -context.dp(3f) + resources.getDimensionPixelSize(R.dimen.settings_row_summary_size) / 2
        })
        // ib.x uses h6.a.B here, which truncates applyDimension instead of rounding like g6.y.h.
        addView(sample, LayoutParams(-1, -2).apply { addRule(BELOW, value.id); topMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics).toInt() })
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
    }
    fun bind(item: TextSizeSampleRow) {
        title.text = item.title; sample.text = item.sample
        seek.setOnSeekBarChangeListener(null)
        seek.max = (item.maximum - item.minimum) / item.step
        val position = (item.value - item.minimum) / item.step
        if (position == 0) seek.progress = 1
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                val selected = item.minimum + progress * item.step
                val pixels = if (item.baseline > 0) {
                    val base = (14f * resources.displayMetrics.scaledDensity + .5f).toInt()
                    (base.toDouble() * selected / item.baseline).toInt()
                } else selected
                sample.setTextSize(TypedValue.COMPLEX_UNIT_PX, pixels.toFloat())
                value.text = String.format(Locale.ROOT, item.summary.orEmpty(), selected)
            }
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) { changed(bar.progress * item.step + item.minimum) }
        })
        seek.onFocusChangeListener = OnFocusChangeListener { _, focused -> if (!focused) changed(seek.progress * item.step + item.minimum) }
        seek.progress = position
        listOf(title, value, seek, sample).forEach { it.alpha = if (item.disabled) .5f else 1f }
        isEnabled = !item.disabled
    }
}
