package dev.ujhhgtg.via.ui

import androidx.core.net.toUri
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.SiteConfiguration
import dev.ujhhgtg.via.data.SiteConfigurationRepository
import dev.ujhhgtg.via.browser.DocumentPolicy
import java.util.Locale

/** Permission modes and packed SiteConf bits from e8.y0 and ba.b. */
class WebsitePermissions(private val preferences: BrowserPreferences, private val sites: SiteConfigurationRepository) {
    enum class Kind(val globalBit: Int, val decisionBit: Int, val askBit: Int) {
        LOCATION(2, 65536, 32768), CAMERA(8388608, 8192, 16384), MICROPHONE(16777216, 2048, 4096)
    }
    fun domain(origin: String): String = origin.toUri().let { (it.host ?: "") + if (it.port < 0) "" else ":${it.port}" }
    fun mode(origin: String, kind: Kind): Int {
        val site = sites.get(domain(origin))?.takeIf { it.isEnabled }
        if (site != null) {
            if (site.enabledFlags and kind.askBit != 0 && site.flags and kind.askBit != 0) return 3
            if (site.enabledFlags and kind.decisionBit != 0) return if (site.flags and kind.decisionBit != 0) 2 else 1
        }
        return if (preferences.webFlags and kind.globalBit != 0) 3 else 2
    }
    /** c8.ua.P0 / w9.p.f: web-opened external apps use the source page's policy. */
    fun openAppMode(url: String): Int {
        val site = sites.get(DocumentPolicy.authority(url))?.takeIf { it.isEnabled }
        return site?.openAppMode?.takeIf { it != 0 } ?: when {
            preferences.webFlags and 67108864 != 0 -> 3
            preferences.webFlags and 33554432 != 0 -> 2
            else -> 1
        }
    }

    /** c8.ua.c1: popups and normal page navigation share this effective source-page value. */
    fun redirectionAllowed(url: String): Boolean {
        val site = sites.get(DocumentPolicy.authority(url))?.takeIf { it.isEnabled }
        return site?.allowRedirection(preferences.webFlags) ?: (preferences.webFlags and 134217728 == 0)
    }

    fun remember(origin: String, kind: Kind, allow: Boolean) {
        var domain = domain(origin)
        if (kind == Kind.LOCATION) domain = domain.trim().lowercase(Locale.ROOT) // e8.y0.S -> kb.c.a
        if (domain.isEmpty()) return
        val site = sites.get(domain) ?: SiteConfiguration(domain, null)
        // e8.y0.L/S clones ba.a, preserving decoded legacy UA/text-size fields,
        // then enables the site and writes the two-bit permission decision.
        sites.put(site.withEnabled(true).withPermission(kind.decisionBit, kind.askBit, if (allow) 1 else 2))
    }
}
