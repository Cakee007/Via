package dev.ujhhgtg.via.browser

import java.util.Locale

/** Explicit navigation headers from t4.b.loadUrl and setAcceptLanguageLocales. */
object RequestHeaderPolicy {
    fun headers(url: String, webFlags: Int, referer: String? = null, languages: String? = null): Map<String, String> =
        linkedMapOf<String, String>().apply {
            if (webFlags and 128 != 0) put("DNT", "1")
            if (webFlags and 256 != 0) put("Save-Data", "on")
            if (webFlags and 65536 != 0) put("Sec-GPC", "1")
            if (referer != null && (referer.startsWith("http://", true) || referer.startsWith("https://", true)) &&
                !url.contains(".aliyundrive.net/")) put("Referer", referer)
            acceptLanguage(languages)?.let { put("Accept-Language", it) }
        }

    fun acceptLanguage(languages: String?): String? {
        if (languages.isNullOrEmpty()) return null
        val values = (if (languages.contains("en-US")) languages else "$languages,en-US").split(',')
        val added = mutableSetOf<String>()
        val result = StringBuilder()
        var quality = 1f
        values.forEachIndexed { index, language ->
            if (language.isNotEmpty() && added.add(language)) {
                quality = maxOf(0.1f, quality - 0.1f)
                result.append(',').append(language)
                val base = language.substringBefore('-')
                if (base != language && added.add(base)) {
                    if (index == 0) {
                        result.append(',').append(base)
                    } else {
                        result.append(";q=").append(String.format(Locale.ROOT, "%.1f", quality))
                        result.append(',').append(base)
                        quality = maxOf(0.1f, quality - 0.1f)
                    }
                }
                result.append(";q=").append(String.format(Locale.ROOT, "%.1f", quality))
            }
        }
        return result.takeIf { it.isNotEmpty() }?.substring(1)
    }
}
