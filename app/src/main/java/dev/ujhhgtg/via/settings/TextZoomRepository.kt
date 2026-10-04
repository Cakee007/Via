package dev.ujhhgtg.via.settings

import android.content.Context
import dev.ujhhgtg.via.browser.UrlPunycode
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.SiteConfiguration
import dev.ujhhgtg.via.data.SiteConfigurationRepository
import dev.ujhhgtg.via.common.GeneratedDocumentState
import java.util.Locale

/** jb.l5 and c8.ua.Q1: page exceptions and the browser popup share the original conf records. */
class TextZoomRepository(context: Context) {
    private val preferences = BrowserPreferences(context)
    private val sites = SiteConfigurationRepository(BrowserDatabase.shared(context))
    init { GeneratedDocumentState.initialize(preferences) }

    var global: Int
        get() {
            val stored = preferences.textSize
            return if (stored > 5) stored else legacySize(stored).also { preferences.textSize = it }
        }
        set(value) {
            if (value > 0) {
                preferences.textSize = if (value <= 5) legacySize(value) else value
                GeneratedDocumentState.mark(GeneratedDocumentState.PAGE_SETTINGS) // jb.l5.s
            }
        }

    var forceZoom: Boolean
        get() = preferences.webFlags and 524288 != 0
        set(value) { preferences.webFlags = if (value) preferences.webFlags or 524288 else preferences.webFlags and 524288.inv() }

    fun exceptions(): Map<String, Int> = sites.all().mapNotNull { stored ->
        sites.get(stored.domain)?.takeIf { it.isEnabled && it.textZoomOverride > 0 }?.let { stored.domain to it.textZoomOverride }
    }.toMap()

    /** jb.k5.n3 applies ba.c.f (the original Punycode encoder), not URL host extraction. */
    fun encodeExceptionInput(input: String): String = UrlPunycode.encodeHost(input)

    fun saveException(domain: String, percent: Int) {
        val key = normalize(domain)
        if (key.isEmpty()) return
        val existing = sites.get(key) ?: SiteConfiguration(key, null)
        sites.put(existing.withEnabled(true).withTextZoom(percent))
        GeneratedDocumentState.mark(GeneratedDocumentState.PAGE_SETTINGS) // jb.l5.l
    }

    fun deleteException(domain: String) {
        val key = normalize(domain)
        if (key.isEmpty()) return
        val existing = sites.get(key) ?: return
        val changed = existing.withTextZoom(0)
        if (changed.isEmpty) sites.remove(key) else sites.put(changed)
        GeneratedDocumentState.mark(GeneratedDocumentState.PAGE_SETTINGS) // jb.l5.r
    }

    fun overrideForUrl(url: String): Int = dev.ujhhgtg.via.browser.DocumentPolicy.authority(url).takeIf(String::isNotEmpty)?.let { sites.get(it) }
        ?.takeIf { it.isEnabled }?.textZoomOverride ?: 0

    /** c8.ua.Q1 removes a popup-created override when its value equals the global setting. */
    fun saveForUrl(url: String, percent: Int) {
        val key = dev.ujhhgtg.via.browser.DocumentPolicy.authority(url).takeIf(String::isNotEmpty) ?: return
        val existing = sites.get(key) ?: SiteConfiguration(key, null)
        val changed = existing.withEnabled(true).withTextZoom(if (percent == global) 0 else percent)
        if (changed.isEmpty) sites.remove(key) else sites.put(changed)
    }

    companion object {
        private fun normalize(value: String) = value.trim { it <= ' ' }.lowercase(Locale.ROOT)
        private fun legacySize(value: Int) = intArrayOf(130, 115, 100, 85, 70)[(if (value in 1..5) value else 3) - 1]

        /** k8.p: compare labels from the domain suffix, retaining the original case order. */
        internal val domainOrder = Comparator<String> { first, second ->
            var length = first.length
            var otherLength = second.length
            var firstStart = length
            var otherStart = otherLength
            var result = 0
            while (length > 0 && otherLength > 0) {
                val dot = first.lastIndexOf('.', length)
                val otherDot = second.lastIndexOf('.', otherLength)
                firstStart = dot + 1
                otherStart = otherDot + 1
                val count = length - firstStart
                for (index in 0 until minOf(count, otherLength - otherStart)) {
                    result = first[firstStart + index] - second[otherStart + index]
                    if (result != 0) break
                }
                if (result != 0) break
                result = count - otherLength + otherStart
                if (result != 0) break
                length = dot - 1
                otherLength = otherDot - 1
            }
            if (result == 0) firstStart - otherStart else result
        }
    }
}
