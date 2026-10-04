package dev.ujhhgtg.via.reader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.ui.dp

/** g8.j: fixed five-colour row, source margins, night colour mix and selection stroke. */
@SuppressLint("ViewConstructor")
class ReaderColorPalette(context: Context, selectedColor: Int, private val dark: Boolean, private val onColorSelected: (Int) -> Unit) : LinearLayout(context) {
    private var selected = -1
    init {
        gravity = Gravity.CENTER_VERTICAL
        setPaddingRelative(units(8), units(12), units(8), units(12))
        colors.forEach { color ->
            addView(View(context).apply {
                tag = color; background = swatch(color, false)
                setOnClickListener { onColorSelected(color); select(color) }
            }, LayoutParams(0, context.dp(42f), 1f).apply { marginStart = units(8); marginEnd = units(8) })
        }
        select(selectedColor)
    }
    fun select(color: Int) {
        val index = colors.indexOf(color)
        if (index == selected) return
        val previous = selected; selected = index
        if (previous in colors.indices) getChildAt(previous).background = swatch(colors[previous], false)
        if (selected in colors.indices) getChildAt(selected).background = swatch(colors[selected], true)
    }
    private fun swatch(color: Int, selected: Boolean): GradientDrawable {
        val background = if (color != 0 && dark) (0xff000000L or (((color shr 16 and 255) * .3f).toLong() shl 16) or (((color shr 8 and 255) * .3f).toLong() shl 8) or ((color and 255) * .3f).toLong()).toInt() else color
        val theme = context.obtainStyledAttributes(intArrayOf(R.attr.viaAccentColor))
        val accent = try { theme.getColor(0, 0) } finally { theme.recycle() }
        return GradientDrawable().apply {
            cornerRadius = resources.getDimensionPixelSize(R.dimen.menu_corner_radius).toFloat()
            setColor(background); setStroke(context.dp(2f), if (selected) accent else 0x30808080)
        }
    }
    private fun units(value: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
    companion object { val colors = intArrayOf(0, -462365, -4133433, -11908531, -15592942) }
}
