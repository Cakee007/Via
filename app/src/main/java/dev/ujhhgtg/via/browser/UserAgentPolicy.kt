package dev.ujhhgtg.via.browser

/** User agent choices and transformations recovered from z8.b4 in the original APK. */
object UserAgentPolicy {
    const val DESKTOP = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36"

    fun resolve(
        choice: Int,
        custom: String?,
        default: String?,
        desktop: Boolean,
        desktopChoice: Int,
        desktopCustom: String?,
        reduce: Boolean,
    ): String? {
        // The default desktop choice returns directly in b4.c, before UA reduction.
        val desktopDefault = desktopDefault(default)
        if (desktop && desktopChoice == 0) return desktopDefault
        val selected = if (desktop) desktopChoice else choice
        val selectedCustom = if (desktop) desktopCustom?.takeIf { it.isNotEmpty() } ?: desktopDefault else custom
        val value = when (selected) {
            -8 -> "Mozilla/5.0 (Symbian/3; Series60/5.2 NokiaN8-00/012.002; Profile/MIDP-2.1 Configuration/CLDC-1.1 ) AppleWebKit/533.4 (KHTML, like Gecko) NokiaBrowser/7.3.0 Mobile Safari/533.4 3gpp-gba"
            -7 -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Safari/605.1.15"
            -6 -> "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1"
            -5 -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36"
            -4 -> "Mozilla/5.0 (Windows NT 10.0; Trident/7.0; rv:11.0) like Gecko"
            -3 -> DESKTOP
            -2 -> "Mozilla/5.0 (Linux; Android 8.1.0; SM-T837A) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36"
            -1 -> "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Mobile Safari/537.36"
            0 -> default
            else -> selectedCustom
        }
        return if (reduce) reduce(value) else value
    }

    private fun desktopDefault(default: String?): String {
        val firefox = default?.let { Regex("Firefox/([0-9.]+)").find(it)?.groupValues?.get(1) } ?: return DESKTOP
        return "Mozilla/5.0 (X11; Linux x86_64; rv:$firefox) Gecko/20100101 Firefox/$firefox"
    }

    /** Original startup removes Chromium's embedded-WebView identifiers (z8.f -> b4.i). */
    fun browserDefault(value: String?): String? {
        if (value == null || !value.contains("; wv")) return value
        return value.replaceFirst("; wv", "").replaceFirst(Regex(" Version/[^ ]*"), "")
    }

    fun reduce(value: String?): String? {
        if (value == null || value.length < 8) return value
        val android = value.indexOf("Android")
        if (android < 0) return value
        val end = value.indexOf(") AppleWebKit", android)
        if (end < 0) return value
        val prefix = value.substring(0, android + 7) + " 10; K"
        val chrome = value.indexOf("Chrome/", end)
        if (chrome < 0) return prefix + value.substring(end)
        val dot = value.indexOf('.', chrome + 7)
        if (dot < 0) return prefix + value.substring(end)
        val suffix = value.indexOf(' ', dot)
        return prefix + value.substring(end, dot + 1) + "0.0.0" + if (suffix > 0) value.substring(suffix) else ""
    }
}
