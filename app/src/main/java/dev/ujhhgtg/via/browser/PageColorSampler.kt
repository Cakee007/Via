package dev.ujhhgtg.via.browser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.webkit.WebView
import androidx.core.graphics.createBitmap
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.search.UrlInputText
import dev.ujhhgtg.via.settings.settingsColor
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** e8.k0: sample the rendered page's top-left pixel using the original event timing. */
internal class PageColorSampler(
    private val isNight: () -> Boolean,
    private val onVisibleColorChanged: (Int) -> Unit,
) {
    // t4.b stores this on each WebView; it is independent of d8.g's process cache.
    fun setAccentColor(view: WebView, color: Int) { restoreColor(view, color) }

    companion object {
        private val accentColors = WeakHashMap<WebView, Int>()
        internal fun colorOf(view: WebView): Int = accentColors[view] ?: 0
        internal fun restoreColor(view: WebView, color: Int) { accentColors[view] = color }
    }

    /** e8.k0.o: page-start uses the cached color immediately, without starting a sample. */
    fun onPageStarted(view: WebView, url: String?) {
        val color: Int
        if (isInternalDocument(view, url)) {
            setAccentColor(view, 0)
            color = 0
        } else {
            val cached = PageColorCache.get(url, 0)
            if (cached != 0) setAccentColor(view, cached)
            color = if (cached != 0) cached else backgroundColor(view)
        }
        if (view.isShown) onVisibleColorChanged(color)
    }

    /** e8.k0.n: completion and progress can both request a sample; the cache owns suppression. */
    fun onPageFinished(view: WebView, url: String?) {
        if (!isInternalDocument(view, url)) scheduleSample(view, url)
    }

    /** e8.k0.r: the original threshold is strictly greater than 70 and at most 100. */
    fun onProgressChanged(view: WebView, progress: Int) {
        if (progress > 70 && progress <= 100) {
            val url = view.url
            if (!isInternalDocument(view, url) && PageColorCache.get(url, 0) == 0) scheduleSample(view, url)
        }
    }

    /** e8.k0.D, for the currently selected/resumed WebView (the original flags contain bit 1). */
    fun onCurrentPageChanged(view: WebView) {
        val url = view.url
        onVisibleColorChanged(if (isInternalDocument(view, url)) 0 else PageColorCache.get(url, backgroundColor(view)))
    }

    /** Reader colors use the same d8.g cache as the page sampler (c8.s6.M7). */
    fun setPageAccentColor(view: WebView, color: Int) {
        PageColorCache.put(view.url, color)
        setAccentColor(view, color)
        if (view.isShown) onVisibleColorChanged(color)
    }

    private fun scheduleSample(view: WebView, url: String?) {
        if (url.isNullOrEmpty() || isNight() || PageColorCache.contains(url)) return
        if (url.length > 12 && url.startsWith("view-source:", ignoreCase = true)) {
            val color = backgroundColor(view)
            setAccentColor(view, color)
            if (view.isShown) onVisibleColorChanged(color)
            return
        }
        // The original writes zero before posting. Do not replace this with a
        // repeating task, or resample merely because the cached value is zero.
        PageColorCache.put(url, 0)
        val reference = WeakReference(view)
        view.postDelayed({
            reference.get()?.let { page ->
                val sampled = samplePixel(page)
                val color = if (sampled == 0) backgroundColor(page) else sampled
                PageColorCache.put(url, color)
                setAccentColor(page, color)
                if (page.isShown) onVisibleColorChanged(color)
            }
        }, 200L)
    }

    private fun backgroundColor(view: View): Int = settingsColor(view.context, R.attr.viaBackgroundColor, 0)

    private fun isInternalDocument(view: View, url: String?): Boolean = url != null &&
        UrlInputText.isInternalDocument(url, view.context.filesDir.path)

    /** s4.b.e: a 1×1 canvas clips WebView.draw; the source does not scale or translate it. */
    private fun samplePixel(view: View): Int {
        var bitmap: Bitmap? = null
        return try {
            val sampled = createBitmap(1, 1)
            bitmap = sampled
            view.draw(Canvas(sampled))
            sampled.getPixel(0, 0)
        } catch (error: Exception) {
            error.printStackTrace()
            0
        } finally {
            bitmap?.recycle()
        }
    }
}

/** d8.g: process-lifetime color cache, with the original URL key extraction. */
internal object PageColorCache {
    private val colors = HashMap<String, Int>()

    fun contains(url: String?): Boolean = key(url).let { it.isNotEmpty() && colors.containsKey(it) }
    fun get(url: String?, fallback: Int): Int = key(url).let { if (it.isEmpty()) fallback else colors[it] ?: fallback }
    fun put(url: String?, color: Int) { key(url).takeIf(String::isNotEmpty)?.let { colors[it] = color } }
    fun remove(url: String?) { key(url).takeIf(String::isNotEmpty)?.let(colors::remove) }

    internal fun key(url: String?): String {
        if (url == null || url.length < 3) return ""
        val scheme = url.indexOf("://")
        if (scheme <= 0) return ""
        if (scheme > 12 && url.startsWith("view-source:", ignoreCase = true)) return "S"
        val end = if (scheme == 4 && url.regionMatches(0, "file", 0, 4, ignoreCase = true)) {
            url.lastIndexOf('?').takeIf { it >= 0 } ?: url.length
        } else {
            val start = scheme + 3
            val slash = url.indexOf('/', start)
            val boundary = if (slash >= 0) slash else url.indexOf('?', start).takeIf { it >= 0 } ?: url.length
            minOf(boundary, scheme + 128)
        }
        return url.substring(scheme + 3, end)
    }
}
