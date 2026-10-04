package dev.ujhhgtg.via.browser

import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.SiteConfiguration

/** c8.ua.W1 and w9.p.s/V: privacy is the global setting with an enabled per-site override. */
class BrowserPrivacyPolicy(
    private val preferences: BrowserPreferences,
    private val siteConfiguration: (String) -> SiteConfiguration?,
) {
    var enabled: Boolean
        get() = preferences.webFlags and 64 != 0
        set(value) { preferences.webFlags = if (value) preferences.webFlags or 64 else preferences.webFlags and 64.inv() }

    fun isIncognito(url: String?): Boolean {
        val site = UrlResolver.siteKey(url)?.let(siteConfiguration)?.takeIf { it.isEnabled }
        return site?.incognito(preferences.webFlags) ?: enabled
    }

    fun mayRecord(url: String?): Boolean = !isIncognito(url)

    /** N1 writes every eligible open tab, including private entries; bit 1 records the effective policy. */
    fun openSessionFlags(url: String?, selected: Boolean): Int = 2 or (if (isIncognito(url)) 1 else 0) or (if (selected) 4 else 0)
}
