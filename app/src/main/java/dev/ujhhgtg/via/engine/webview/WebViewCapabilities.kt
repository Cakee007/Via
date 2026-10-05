package dev.ujhhgtg.via.engine.webview

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/** e8.i.J/L darkening and the explicitly requested Via #1936 WebAuthn opt-in. */
internal object WebViewCapabilities {
    /** e8.i.O preserves metadata fields while replacing platform/mobile and Android brands. */
    fun applyUserAgentMetadata(view: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) return
        val userAgent = view.settings.userAgentString.orEmpty()
        if (userAgent.isEmpty() || userAgent.contains("Android")) return
        val previous = WebSettingsCompat.getUserAgentMetadata(view.settings)
        val mobile = userAgent.contains("iPhone") || userAgent.contains("iPad")
        val platform = when {
            userAgent.contains("Windows") -> "Windows"
            mobile -> "iOS"
            userAgent.contains("Macintosh") -> "macOS"
            else -> "Unknown"
        }
        val metadata = androidx.webkit.UserAgentMetadata.Builder(previous)
            .setPlatform(platform).setMobile(mobile)
            .setBrandVersionList(previous.brandVersionList.filterNot { it.brand.contains("Android") })
            .setFullVersion(previous.fullVersion?.trim()?.takeIf(String::isNotEmpty)).build()
        WebSettingsCompat.setUserAgentMetadata(view.settings, metadata)
    }

    @SuppressLint("WrongConstant")
    fun configure(view: WebView, darkening: Boolean) {
        val settings = view.settings
        applyNightTheme(view, darkening)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) {
            WebSettingsCompat.setWebAuthenticationSupport(settings, WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER)
        }
    }

    /** c8.s6.sb/ua.n1: update an already-loaded WebView without navigation. */
    @Suppress("DEPRECATION")
    fun applyNightTheme(view: WebView, enabled: Boolean) {
        val settings = view.settings
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, enabled)
        else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) WebSettingsCompat.setForceDark(settings, if (enabled) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF)
    }
}
