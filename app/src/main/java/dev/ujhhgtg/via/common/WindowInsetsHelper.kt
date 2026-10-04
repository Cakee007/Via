package dev.ujhhgtg.via.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.ujhhgtg.via.R

/** Kotlin translation of z8.l3; the original enables edge-to-edge on API 29+. */
@Suppress("DEPRECATION")
object WindowInsetsHelper {
    val cornerRadii = IntArray(4)

    fun apply(view: View, top: View? = null, bottom: View? = null) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            applyInsets(insets, target, top, bottom)
            insets
        }
    }

    private fun applyInsets(insets: WindowInsetsCompat, view: View, top: View?, bottom: View?) {
        if (!isFullscreen(insets, view)) {
            applySystemBars(insets, true, view, top, bottom)
            return
        }
        val bars = insets.getInsets(WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.systemBars())
        if (bars == Insets.NONE) {
            applySystemBars(insets, false, view, top, bottom)
            return
        }
        if (Resources.getSystem().configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            view.setPadding(bars.left, 0, bars.right, 0)
            clearPadding(top, bottom)
            return
        }
        val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
        if (top == null && bottom == null) {
            view.setPadding(0, bars.top, 0, if (keyboard.bottom > 0) 0 else bars.bottom)
            return
        }
        val cutout = insets.displayCutout ?: return
        val width = view.measuredWidth
        val height = view.measuredHeight
        if (width <= 0 || height <= 0) return
        val upper = IntArray(2)
        val lower = IntArray(2)
        val edge = (view.resources.displayMetrics.density * 16f + .5f).toInt()
        for (rect in cutout.boundingRects) {
            if (rect.top <= edge) {
                if (rect.left < edge * 2) upper[0] = maxOf(upper[0], rect.right)
                else if (rect.right >= width - edge * 2) upper[1] = maxOf(upper[1], width - rect.left)
            } else if (keyboard.bottom <= 0 && rect.bottom >= height - edge) {
                if (rect.left < edge * 2) lower[0] = maxOf(lower[0], rect.right)
                else if (rect.right >= width - edge * 2) lower[1] = maxOf(lower[1], width - rect.left)
            }
        }
        top?.setPadding(upper[0], 0, upper[1], 0)
        bottom?.setPadding(lower[0], 0, lower[1], 0)
        view.setPadding(0, 0, 0, 0)
    }

    fun applySystemBars(insets: WindowInsetsCompat?, enabled: Boolean, view: View, top: View? = null, bottom: View? = null) {
        if (insets == null) return
        if (!enabled) {
            clearPadding(top, bottom, view)
            return
        }
        val bars = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        top?.setPadding(0, bars.top, 0, 0)
        bottom?.setPadding(0, 0, 0, bars.bottom)
        view.setPadding(bars.left, if (top == null) bars.top else 0, bars.right, if (bottom == null) bars.bottom else 0)
    }

    fun window(context: Context?): Window? {
        var candidate = context
        while (candidate is ContextWrapper) {
            if (candidate is Activity) return candidate.window
            candidate = candidate.baseContext
        }
        return null
    }

    fun isFullscreen(window: Window?): Boolean {
        if (window == null) return false
        val view = window.decorView
        if (Build.VERSION.SDK_INT >= 30) {
            (view.getTag(R.id.fullscreen_state) as? Int)?.let { return it == 1 }
            view.rootWindowInsets?.let { return !it.isVisible(WindowInsets.Type.statusBars()) }
        }
        return view.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0
    }

    fun isFullscreen(insets: WindowInsetsCompat?, view: View): Boolean {
        if (Build.VERSION.SDK_INT >= 30) {
            (view.rootView.getTag(R.id.fullscreen_state) as? Int)?.let { return it == 1 }
            if (insets != null) return !insets.isVisible(WindowInsetsCompat.Type.statusBars())
        }
        return isFullscreen(window(view.context))
    }

    fun clearPadding(vararg views: View?) = views.forEach { it?.setPadding(0, 0, 0, 0) }

    fun restoreBarAppearance(context: Context, state: IntArray?) {
        val window = window(context) ?: return
        if (state == null || state.isEmpty()) return
        window.statusBarColor = state[0]
        if (state.size >= 2) {
            window.navigationBarColor = state[1]
            window.navigationBarDividerColor = state[1]
        }
        if (state.size >= 3) {
            if (Build.VERSION.SDK_INT < 30) window.decorView.systemUiVisibility = state[2]
            else window.insetsController?.setSystemBarsAppearance(state[2],
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        }
    }

    fun saveBarAppearance(context: Context): IntArray {
        val window = window(context) ?: return intArrayOf()
        return intArrayOf(window.statusBarColor, window.navigationBarColor,
            if (Build.VERSION.SDK_INT < 30) window.decorView.systemUiVisibility else window.insetsController?.systemBarsAppearance ?: 0)
    }

    fun enableEdgeToEdge(context: Context) {
        val window = window(context) ?: return
        if (Build.VERSION.SDK_INT >= 35) return
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        else {
            window.decorView.systemUiVisibility = 1792
            window.statusBarColor = 0
            window.navigationBarColor = 0
        }
    }

    fun setFullscreen(window: Window?, fullscreen: Boolean) {
        if (window == null) return
        val decor = window.decorView
        if (Build.VERSION.SDK_INT >= 30) {
            decor.setTag(R.id.fullscreen_state, if (fullscreen) 1 else 0)
            if (Build.VERSION.SDK_INT < 35) window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                if (fullscreen) {
                    it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    it.hide(WindowInsets.Type.systemBars())
                } else {
                    if (Build.VERSION.SDK_INT >= 31) it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_DEFAULT
                    it.show(WindowInsets.Type.systemBars())
                }
                return
            }
        }
        window.attributes = window.attributes.apply {
            flags = if (fullscreen) flags or WindowManager.LayoutParams.FLAG_FULLSCREEN else flags and WindowManager.LayoutParams.FLAG_FULLSCREEN.inv()
        }
        val mask = if (fullscreen) 5638 else 4102
        decor.systemUiVisibility = if (fullscreen) decor.systemUiVisibility or mask else decor.systemUiVisibility and mask.inv()
    }

    fun setLightNavigationBar(context: Context, light: Boolean): Boolean {
        val window = window(context) ?: return !light
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let { it.setSystemBarsAppearance(if (light) WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0, WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS); return true }
        }
        val decor = window.decorView
        decor.systemUiVisibility = if (light) decor.systemUiVisibility or 16 else decor.systemUiVisibility and 16.inv()
        return true
    }

    fun setLightStatusBar(context: Context, light: Boolean): Boolean {
        val window = window(context) ?: return !light
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let { it.setSystemBarsAppearance(if (light) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS); return true }
        }
        val decor = window.decorView
        decor.systemUiVisibility = if (light) decor.systemUiVisibility or 8192 else decor.systemUiVisibility and 8192.inv()
        return true
    }

    fun readRoundedCorners(insets: WindowInsetsCompat?) {
        if (Build.VERSION.SDK_INT < 31) return
        val platform = insets?.toWindowInsets() ?: return
        for (index in cornerRadii.indices) platform.getRoundedCorner(index)?.let { cornerRadii[index] = it.radius }
    }
}
