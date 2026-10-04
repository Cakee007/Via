package dev.ujhhgtg.via.ui

import android.content.Context

internal fun Context.dp(value: Float): Int =
    (value * resources.displayMetrics.density + 0.5f).toInt()

internal fun Context.textAppearanceColor(dark: Boolean = false): Int =
    if (dark) 0xfff5f5f5.toInt() else 0xde000000.toInt()
