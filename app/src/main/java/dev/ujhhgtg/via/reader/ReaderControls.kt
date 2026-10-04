package dev.ujhhgtg.via.reader

import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.widget.SeekBar
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.ui.dp

/** z8.r3.g and x8.h.n/w, shared by all original reader/night slider binders. */
object ReaderControls {
    fun styleSeekBar(seekBar: SeekBar) {
        val context = seekBar.context
        val theme = context.obtainStyledAttributes(intArrayOf(R.attr.viaAccentColor))
        val accent = try { theme.getColor(0, 0) } finally { theme.recycle() }
        val track = GradientDrawable().apply { cornerRadius = context.dp(5f).toFloat(); setColor(0x40808080); setSize(0, context.dp(2f)) }
        val fill = GradientDrawable().apply { cornerRadius = context.dp(5f).toFloat(); setColor(accent); setSize(0, context.dp(2f)) }
        seekBar.progressDrawable = LayerDrawable(arrayOf(track, ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL))).apply {
            setId(0, android.R.id.background); setId(1, android.R.id.progress)
        }
        seekBar.thumb = GradientDrawable().apply { cornerRadius = context.dp(6f).toFloat(); setColor(accent); setSize(context.dp(12f), context.dp(12f)) }
    }
}
