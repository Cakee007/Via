package dev.ujhhgtg.via.video

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager

/** The z8.n3 display, brightness, and orientation helpers. */
object VideoWindowPolicy {
    fun orientationSupported(context: Context): Boolean =
        Build.VERSION.SDK_INT < 36 || context.resources.configuration.smallestScreenWidthDp < 600

    fun systemBrightness(context: Context): Float {
        val current = Settings.System.getInt(context.contentResolver, "screen_brightness", 10)
        val maximum = brightnessMaximum(context)
        val minimum = minimumBrightness(context)
        return (current.toFloat() - minimum.toFloat()) / (maximum - minimum).toFloat()
    }

    fun windowBrightness(context: Context): Float = activity(context)?.window?.attributes?.screenBrightness ?: -1f

    fun currentOrientation(context: Context): Int {
        val rotation = if (Build.VERSION.SDK_INT >= 30) {
            context.display.rotation
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.rotation ?: return -1
        }
        return when (rotation) {
            0 -> 1
            1 -> 0
            2 -> 9
            3 -> 8
            else -> -1
        }
    }

    fun requestOrientation(context: Context, orientation: Int) {
        val target = activity(context) ?: return
        if (orientationSupported(context) && target.requestedOrientation != orientation) {
            target.requestedOrientation = orientation
        }
    }

    /** n3.j; raw values are intentional so -1 restores the system default. */
    fun setBrightness(context: Context, value: Float) {
        val window = activity(context)?.window ?: return
        val attributes = window.attributes
        attributes.screenBrightness = value
        window.attributes = attributes
    }

    private fun activity(context: Context): Activity? {
        var current: Context? = context
        while (current is android.content.ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    private fun brightnessMaximum(context: Context): Int {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return 255
        for (field in power.javaClass.declaredFields) {
            if (field.name != "BRIGHTNESS_ON") continue
            return try {
                field.isAccessible = true
                (field.get(power) as? Int) ?: 255
            } catch (_: IllegalAccessException) {
                255
            } catch (_: SecurityException) {
                255
            }
        }
        return 255
    }

    // z8.n3.c reads this framework value by name.
    @SuppressLint("DiscouragedApi")
    private fun minimumBrightness(context: Context): Int = try {
        val resources = context.resources
        resources.getInteger(resources.getIdentifier("config_screenBrightnessSettingMinimum", "integer", "android"))
    } catch (_: Exception) {
        0
    }
}
