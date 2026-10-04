package dev.ujhhgtg.via.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.os.Build
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.common.WindowInsetsHelper
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.settings.settingsColor
import java.lang.ref.WeakReference

/** c8.s6.yb/Q7/C5: choose toolbar colors and animate only their background layers. */
internal class ToolbarColorController(
    private val context: Context,
    private val preferences: BrowserPreferences,
    private val isNight: () -> Boolean,
    private val hasBackgroundImage: () -> Boolean,
    private val isAttached: () -> Boolean,
    private val onControlsChanged: (Controls) -> Unit,
    private val onBackgroundFrame: (BackgroundFrame) -> Unit,
) {
    data class Controls(
        val iconColor: Int,
        val textColor: Int,
        val webFilterColor: Int,
        val lightSystemBars: Boolean,
    )

    data class BackgroundFrame(
        val color: Int,
        /** f8.k receives true while an animation is between its first and last frames. */
        val intermediate: Boolean,
        /** Q7's transparent-image fast path changes w0 alone, leaving f8/B0 untouched. */
        val onlyToolbar: Boolean = false,
    )

    // c8.s6.e9 initializes U0 to -1 when it constructs the address toolbar.
    var currentColor: Int = -1
        private set
    var usesDefaultBackground: Boolean = false
        private set
    private var animator: WeakReference<ValueAnimator>? = null

    /** Input zero denotes an internal document/default background, not a sampled transparent pixel. */
    fun setPageColor(pageColor: Int, animate: Boolean = true) {
        val shouldAnimate = animate && currentColor != 0
        if (pageColor == 0) {
            val color = if (hasBackgroundImage()) 0 else {
                val configured = configuredBackgroundColor()
                if (configured == -1) defaultColor()
                else if (isNight()) blend(Color.BLACK, configured, .5f) else configured
            }
            if (color != currentColor || !usesDefaultBackground) apply(color, shouldAnimate, true)
        } else {
            val color = if (preferences.appFlags and 2 == 0 || isNight()) defaultColor() else pageColor
            if (color != currentColor || usesDefaultBackground) apply(color, shouldAnimate, false)
        }
    }

    /** c8.s6.sb invalidates U0's alpha before applying the new night/day palette. */
    fun onThemeChanged(internalDocument: Boolean) {
        currentColor = invalidateColor(currentColor)
        setPageColor(if (internalDocument) 0 else defaultColor(), false)
    }

    /** c8.s6.zb forces a refresh after changing the home background while it is visible. */
    fun onHomeBackgroundChanged(internalDocument: Boolean) {
        if (!internalDocument) return
        currentColor = invalidateColor(currentColor)
        setPageColor(0, true)
    }

    /** Reapply Q7's controls after constructing/replacing native toolbar views. */
    fun rebindControls() {
        val controls = controls(currentColor, usesDefaultBackground)
        onControlsChanged(controls)
        applyWindowAppearance(controls.lightSystemBars, currentColor)
    }

    fun cancelAnimation() {
        animator?.get()?.takeIf { it.isRunning }?.cancel()
        animator = null
    }

    private fun apply(color: Int, animate: Boolean, defaultBackground: Boolean) {
        cancelAnimation()
        val hasImage = hasBackgroundImage()
        val controls = controls(color, defaultBackground)
        onControlsChanged(controls)
        applyWindowAppearance(controls.lightSystemBars, color)
        val previous = currentColor
        currentColor = color
        usesDefaultBackground = defaultBackground
        if (!animate || previous == color) {
            onBackgroundFrame(BackgroundFrame(color, false, color == 0 && hasImage))
            return
        }
        val transition = ValueAnimator.ofObject(ArgbEvaluator(), previous, color)
        transition.addUpdateListener { animation ->
            if (isAttached()) {
                onBackgroundFrame(BackgroundFrame(animation.animatedValue as Int, animation.animatedFraction != 1f))
            }
        }
        transition.duration = context.resources.getInteger(R.integer.toolbar_color_animation_duration).toLong()
        // Q7 leaves the platform ValueAnimator interpolator unchanged.
        transition.start()
        animator = WeakReference(transition)
    }

    private fun controls(color: Int, defaultBackground: Boolean): Controls {
        val night = isNight()
        val design = preferences.backgroundInfo
        val light = !night && if (color == 0 && design and 128 != 0) design and 256 != 0
            else isLight(if (color == 0) configuredBackgroundColor() else color)
        val textColor = when {
            night -> context.getColor(R.color.via_primary_text_dark)
            light -> context.getColor(R.color.via_primary_text_light)
            else -> Color.WHITE
        }
        val iconColor = when {
            night -> context.getColor(R.color.via_subtle_dark)
            light -> context.getColor(R.color.via_subtle_light)
            else -> Color.WHITE
        }
        val opacity = if (night && !defaultBackground) preferences.nightFilter.coerceIn(0, 255) else 0
        return Controls(iconColor, textColor, Color.argb(opacity, 0, 0, 0), light)
    }

    @Suppress("DEPRECATION")
    private fun applyWindowAppearance(light: Boolean, color: Int) {
        val lightStatus = WindowInsetsHelper.setLightStatusBar(context, light)
        val lightNavigation = WindowInsetsHelper.setLightNavigationBar(context, light)
        val window = WindowInsetsHelper.window(context) ?: return
        window.statusBarColor = if (light && !lightStatus) Color.argb(51, 0, 0, 0) else 0
        var navigation = if (light && !lightNavigation) Color.argb(51, 0, 0, 0) else 0
        window.navigationBarColor = navigation
        window.navigationBarDividerColor = navigation
    }

    private fun defaultColor(): Int = settingsColor(context, R.attr.viaBackgroundColor, 0)

    /** w9.k.c0 also canonicalizes non-negative legacy values to the -1 default. */
    private fun configuredBackgroundColor(): Int {
        val color = preferences.urlBarColor
        if (color < 0) return color
        preferences.urlBarColor = -1
        return -1
    }

    companion object {
        /** g6.y.C ignores alpha and uses the original 192 luminance threshold. */
        internal fun isLight(color: Int): Boolean = Color.red(color) * .299 + Color.green(color) * .587 + Color.blue(color) * .114 >= 192.0

        /** g6.y.G truncates each interpolated channel and always produces opaque RGB. */
        private fun blend(first: Int, second: Int, amount: Float): Int = Color.rgb(
            (Color.red(first) * (1f - amount) + Color.red(second) * amount).toInt(),
            (Color.green(first) * (1f - amount) + Color.green(second) * amount).toInt(),
            (Color.blue(first) * (1f - amount) + Color.blue(second) * amount).toInt(),
        )

        /** g6.y.H changes only alpha so the next yb call cannot skip an equal color. */
        private fun invalidateColor(color: Int): Int {
            val alpha = color ushr 24
            return color and 0xffffff or ((if (alpha < 255) alpha + 1 else alpha - 1) shl 24)
        }
    }
}
