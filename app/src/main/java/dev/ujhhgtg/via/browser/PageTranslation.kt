package dev.ujhhgtg.via.browser

import android.app.Activity
import android.os.LocaleList
import android.webkit.URLUtil
import android.webkit.WebView
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.ui.ViaToast
import dev.ujhhgtg.via.ui.dialog.ViaDialog
import java.net.URLEncoder

/** c8.s6.ob/z5 and c8.ua.a2/b2, with literal injection templates from i6.e0. */
class PageTranslation(
    private val activity: Activity,
    private val currentWebView: () -> WebView?,
    private val currentUrl: () -> String?,
    private val bridgeSecret: String,
    private val openPage: (String) -> Unit,
    private val openTextTranslation: (String?) -> Unit,
) {
    /** C9(14) -> fa(15) -> ua.Z1. The injection fallback separately opens ob(false). */
    fun translate() = translateInPage(microsoft = dev.ujhhgtg.via.data.BrowserPreferences(activity).searchMode != -1)

    /** s6.ob: 3 providers after an injection failure, 5 for the menu's long press. */
    fun show(extended: Boolean = false) {
        val url = currentUrl()
        if (!URLUtil.isNetworkUrl(url)) { toast(R.string.cannot_work); return }
        val language = systemLanguage()
        // z8.c0.f() is true in the original CN build, so the Baidu/Microsoft branch is present.
        val providers = buildList {
            if (extended) add(6 to R.string.translation_engine_microsoft)
            add(1 to R.string.translation_engine_baidu)
            if (extended) add(4 to R.string.translation_engine_google_in_page)
            add(3 to R.string.translation_engine_google)
            add(5 to R.string.translation_engine_google_text)
        }
        ViaDialog(activity).title(R.string.action_translate)
            .items(providers.map { activity.getString(it.second) }.toTypedArray(), onClick = { position ->
                when (val provider = providers[position].first) {
                    6 -> translateInPage(microsoft = true)
                    4 -> translateInPage(microsoft = false)
                    5 -> openTextTranslation(null)
                    else -> externalUrl(provider, url.orEmpty(), language)?.let(openPage)
                }
            }).show()
    }

    private fun translateInPage(microsoft: Boolean) {
        val webView = currentWebView()
        if (webView == null || !UrlResolver.isHttpUrl(webView.url)) { toast(R.string.cannot_work); return }
        val language = systemLanguage()
        val fallback = if (bridgeSecret.isEmpty()) "" else "window.via.postMessage('$bridgeSecret', JSON.stringify({action: 108}));"
        val template = if (microsoft) "translate-microsoft-in-page.js" else "translate-google-in-page.js"
        val source = activity.assets.open("browser/injection/$template").bufferedReader().use { it.readText() }
            .replace("__VIA_FALLBACK__", fallback)
        val translated = if (microsoft) source.replace("__VIA_LANGUAGE__", microsoftLanguage(language))
            else source.replace("__VIA_LANGUAGES__", googleLanguages(language))
        webView.evaluateJavascript(translated, null)
        toast(R.string.wait_a_moment)
    }

    private fun toast(resource: Int) = ViaToast.makeText(activity, resource, ViaToast.LENGTH_SHORT).show()

    companion object {
        /** z8.t1.g/n/i uses the adjusted system locale, excluding script and ordinary variant subtags. */
        internal fun systemLanguage(): String {
            val locale = LocaleList.getAdjustedDefault()[0]
            val language = when (val code = locale.language) {
                "in" -> "id"
                "iw" -> "he"
                "ji" -> "yi"
                "jw" -> "jv"
                "tl" -> "fil"
                else -> code
            }
            val country = locale.country
            if (language == "no" && country == "NO" && locale.variant == "NY") return "nn-NO"
            return if (country.isEmpty()) language else "$language-$country"
        }

        /** i6.f0.a/c/d: provider 2 is the retained Youdao callback, absent from both current menus. */
        internal fun externalUrl(provider: Int, url: String, language: String): String? {
            if (url.isEmpty() || language.isEmpty()) return null
            val encoded = runCatching { URLEncoder.encode(url, "UTF-8").replace("+", "%20") }.getOrDefault(url)
            return when (provider) {
                1 -> "https://fanyi.baidu.com/transpage?query=$encoded&source=url&ie=utf8&from=auto&to=${language.substringBefore('-')}&render=1"
                2 -> "http://webtrans.yodao.com/webTransPc/index.html#/?url=$encoded&from=auto&to=${language.substringBefore('-')}&type=1"
                3 -> "https://translate.google.com/translate?sl=auto&tl=$language&u=$encoded"
                else -> null
            }
        }

        /** i6.f0.f deliberately includes the original non-Chinese list's "js" language code. */
        internal fun googleLanguages(language: String): String {
            val languages = if (language.startsWith("zh-")) mutableListOf(
                "en", "ar", "ja", "ko", "ru", "fr", "de", "es", "it", "pt", "zh-CN", "zh-TW",
            ) else mutableListOf(
                "en", "ar", "az", "be", "bn", "cs", "da", "de", "es", "fa", "fil", "fr", "hi", "hr", "hu", "id", "in", "it",
                "js", "ko", "lt", "ne", "pl", "pt", "pt-BR", "ro", "ru", "sk", "sl", "ta", "th", "tr", "uk", "uz", "vi", "zh-CN", "zh-TW",
            )
            languages.remove(language)
            languages.sort()
            languages.add(0, language)
            return languages.joinToString(",")
        }

        internal fun microsoftLanguage(language: String): String = if (language.startsWith("zh-")) {
            if (language.endsWith("TW") || language.endsWith("HK")) "chinese_traditional" else "chinese_simplified"
        } else "english"
    }
}
