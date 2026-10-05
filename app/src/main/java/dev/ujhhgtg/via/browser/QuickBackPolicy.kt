package dev.ujhhgtg.via.browser

import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.SiteConfiguration
import dev.ujhhgtg.via.search.UrlInputText

/** e8.i.K/N: decide whether navigation gets a separately retained page. */
internal class QuickBackPolicy(
    private val filesPath: String,
    private val preferences: BrowserPreferences,
    private val siteConfiguration: (String) -> SiteConfiguration?,
    private val segmentsSupported: Boolean = dev.ujhhgtg.via.engine.Engines.backend.capabilities.quickBackSegments,
) {
    fun shouldRetain(source: String?, target: String, automatic: Boolean): Boolean {
        if (!segmentsSupported) return false
        val targetInternal = UrlInputText.isInternalDocument(target, filesPath)
        val sourceInternal = source?.let { UrlInputText.isInternalDocument(it, filesPath) } == true
        // The generated-document boundary precedes the redirect and preference
        // tests in N; it remains a separate page even with Quick back off.
        if (targetInternal != sourceInternal) return true
        if (targetInternal || automatic) return false
        if (source != null) {
            val targetHash = target.lastIndexOf('#')
            val sourceHash = source.lastIndexOf('#')
            if (targetHash >= 0 || sourceHash >= 0) {
                val targetEnd = if (targetHash >= 0) targetHash else target.length
                val sourceEnd = if (sourceHash >= 0) sourceHash else source.length
                if (targetEnd == sourceEnd && target.regionMatches(0, source, 0, targetEnd)) return false
            }
        }
        return (enabled(target) || enabled(source)) && !isAuthenticationUrl(source)
    }

    /** SiteConf's explicit override takes precedence over the bundled global exclusions. */
    internal fun enabled(url: String?): Boolean {
        val authority = if (url == null) "" else DocumentPolicy.authority(url)
        val site = siteConfiguration(authority)
        if (site?.isEnabled == true && site.overrides(131072)) return site.backWithoutReload(preferences.webFlags)
        return preferences.webFlags and 512 != 0 && allowedAuthority(authority)
    }

    companion object {
        /** z8.b0.J compares the unmodified i6.i0.e authority, including its port. */
        internal fun allowedAuthority(authority: String): Boolean = !(authority.startsWith("192.168.") ||
            authority == "x.com" || authority == "metaso.cn" || excludedAuthorities.any { authority.contains(it) })

        private val excludedAuthorities = arrayOf("forum.softpedia.com", "3g.163.com", "bbs.binmt.cc",
            "www.giant.com.cn", "www.10099.com.cn", ".10086.cn", ".10010.com", "myaccount.google.com", "accounts.google.com")

        /** z8.b0.K / g6.p.d / i6.f.f: ASCII-letter boundaries, with '=' excluded before a word. */
        internal fun isAuthenticationUrl(url: String?): Boolean {
            if (url == null || url.length < 5) return false
            for (word in arrayOf("login", "signup", "signin", "register", "sign-up", "create-account", "createaccount", "auth")) {
                val start = indexOfIgnoreCase(url, word)
                if (start <= 0) continue
                val before = url[start - 1]
                if (asciiLetter(before) || before == '=') return false
                val end = start + word.length
                if (end >= url.length) return true
                val after = url[end]
                if (before == '.' && after == '.') return false
                return !asciiLetter(after)
            }
            return false
        }

        private fun indexOfIgnoreCase(value: String, query: String): Int {
            for (start in 0..value.length - query.length) {
                var offset = 0
                while (offset < query.length && Character.toLowerCase(value[start + offset]) == Character.toLowerCase(query[offset])) offset++
                if (offset == query.length) return start
            }
            return -1
        }

        private fun asciiLetter(value: Char): Boolean = value in 'A'..'Z' || value in 'a'..'z'
    }
}
