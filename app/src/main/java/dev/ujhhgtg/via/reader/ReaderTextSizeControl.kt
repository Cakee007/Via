package dev.ujhhgtg.via.reader

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.ui.dp

/** g8.f: the in-page reader font-size row, step one and bounds 10..30. */
@SuppressLint("ViewConstructor")
class ReaderTextSizeControl(context: Context, textSize: Int, private val onChanged: (Int) -> Unit) : LinearLayout(context) {
    private val slider: SeekBar
    init {
        layoutParams = android.view.ViewGroup.LayoutParams(-1, context.dp(48f))
        setPaddingRelative(units(8), units(4), units(8), units(4))
        slider = SeekBar(ContextThemeWrapper(context, R.style.OriginalSeekbar)).apply {
            max = 20; progress = (textSize - 10).coerceIn(0, 20); ReaderControls.styleSeekBar(this)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) = Unit
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = onChanged(seekBar.progress + 10)
            })
        }
        fun button(label: Int, size: Float, step: Int) = TextView(context).apply {
            setText(label); gravity = Gravity.CENTER; setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            val values = context.obtainStyledAttributes(intArrayOf(R.attr.viaPrimaryTextColor))
            try { setTextColor(values.getColor(0, 0)) } finally { values.recycle() }
            typeface = BrowserPreferences(context).selectedTypeface()
            setPaddingRelative(units(8), 0, units(8), 0); setBackgroundResource(R.drawable.rounded_rect_ripple)
            setOnClickListener { slider.progress += step; onChanged(slider.progress + 10) }
        }
        addView(button(R.string.reader_font_decrease, 15f, -1), LayoutParams(-2, -1))
        addView(slider, LayoutParams(-1, -2, 1f).apply { gravity = Gravity.CENTER_VERTICAL })
        addView(button(R.string.reader_font_increase, 18f, 1), LayoutParams(-2, -1))
    }
    fun setTextSize(value: Int) { slider.progress = (value - 10).coerceIn(0, 20) }
    private fun units(value: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
}
