package dev.ujhhgtg.via.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.home.HomeBackground
import dev.ujhhgtg.via.home.HomeDesign
import java.util.concurrent.Executors

/** x7.f and c8.f8: publish the browser's current native layers for newly-created settings pages. */
object BrowserBackgrounds {
    private val published = WindowBackgroundDrawable()
    private var imageKey: ImageKey? = null
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private data class ImageKey(val path: String, val modified: Long, val length: Long, val size: Int, val density: Int)

    /** c8.s6.Ab/a9/Q7/sb, called before opening settings and when browser appearance updates. */
    fun update(context: Context, dark: Boolean, coverColor: Int = Color.TRANSPARENT): WindowBackgroundDrawable {
        val appContext = context.applicationContext
        val preferences = BrowserPreferences(appContext)
        val backgroundPath = preferences.backgroundHome
        val file = HomeBackground.imageFile(appContext, backgroundPath)
        val metrics = appContext.resources.displayMetrics
        val key = file?.let { ImageKey(it.path, it.lastModified(), it.length(), maxOf(metrics.widthPixels, metrics.heightPixels), metrics.densityDpi) }
        if (imageKey != key) {
            imageKey = key
            // c8.s6.Ab: decode off the main thread and apply on it; a stale decode is dropped.
            io.execute {
                HomeBackground.regenerateCache(appContext, backgroundPath)
                val image = HomeBackground.createWindowImage(appContext, backgroundPath)
                main.post { if (imageKey == key) published.setImage(image) }
            }
        }
        // The night scrim darkens the customized image; "disable homepage background dimming" skips it
        // while the user's own bginfo opacity still applies.
        val night = if (dark && !preferences.disableHomeBackgroundDimming) if (HomeDesign.isLight(preferences.urlBarColor)) 128 else 64 else 0
        val opacity = ((preferences.backgroundInfo and 127) / 100f * 255).toInt()
        published.setFilterColor(Color.argb(maxOf(night, opacity), 0, 0, 0))
        published.setCoverColor(coverColor)
        return published
    }

    /** g6.y.a0/e and z8.l.f: use a snapshot only when the active browser actually has an image. */
    fun wrapSettings(context: Context, body: View): View {
        val preferences = BrowserPreferences(context)
        if (!preferences.showSettingsBackground || !published.hasImage) {
            val attributes = context.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
            try { body.background = attributes.getDrawable(0) } finally { attributes.recycle() }
            return body
        }
        val attributes = context.obtainStyledAttributes(intArrayOf(R.attr.viaBackgroundColor))
        val color = try { attributes.getColor(0, Color.WHITE) } finally { attributes.recycle() }
        val snapshot = published.snapshot(color)
        val light = if (preferences.backgroundInfo and 128 != 0) preferences.backgroundInfo and 256 != 0
            else HomeDesign.isLight(preferences.urlBarColor)
        val dark = isNight(context, preferences)
        snapshot.setCoverColor(if (!dark && !light) 0x99ffffff.toInt() else Color.TRANSPARENT)
        val image = SettingsBackgroundView(context).apply {
            setImageDrawable(snapshot)
            resetImageBounds()
            if (Build.VERSION.SDK_INT >= 31 && preferences.blurEffect) {
                setRenderEffect(RenderEffect.createBlurEffect(30f, 30f, Shader.TileMode.MIRROR))
            }
        }
        body.background = null
        return FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            addView(image, FrameLayout.LayoutParams(-1, -1))
            addView(body, FrameLayout.LayoutParams(-1, -1))
        }
    }

    internal fun isNight(context: Context, preferences: BrowserPreferences = BrowserPreferences(context)): Boolean =
        preferences.isNightMode
}
