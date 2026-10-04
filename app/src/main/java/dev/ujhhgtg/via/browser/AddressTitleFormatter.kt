package dev.ujhhgtg.via.browser

import android.content.Context
import android.webkit.URLUtil
import dev.ujhhgtg.via.R
import dev.ujhhgtg.via.search.UrlInputText
import java.net.URLDecoder

/** c8.s6.F/t: address contents are independent of the title shown in the tab bar. */
internal object AddressTitleFormatter {
    /**
     * [documentUrl] is the actual WebView URL, before BrowserTab maps generated
     * file URLs back to their about:/v: navigation aliases.
     */
    fun format(
        context: Context,
        title: String?,
        url: String?,
        urlBoxMode: Int,
        tabBarEnabled: Boolean,
        documentUrl: String? = url,
    ): String {
        val unwrapped = unwrapErrorUrl(url)
        val displayedTitle = if (url != null && url.length != unwrapped?.length) displayUrl(unwrapped) else title
        val internalDocument = listOfNotNull(unwrapped, documentUrl).any {
            UrlInputText.isInternalDocument(it, context.filesDir.path)
        }
        if (internalDocument) return displayedTitle.orEmpty()

        val mode = if (tabBarEnabled && urlBoxMode == 0) 2 else urlBoxMode
        if (mode != 0) {
            // F leaves the existing title untouched if its URL is absent.
            if (unwrapped.isNullOrEmpty()) return displayedTitle.orEmpty()
            val display = displayUrl(unwrapped).orEmpty()
            if (mode == 2) return domain(display).ifEmpty { display }
            if (!URLUtil.isNetworkUrl(display)) return display
            val withoutScheme = display.substring(display.indexOf("://") + 3)
            val slash = withoutScheme.indexOf('/')
            return if (slash == withoutScheme.length - 1) withoutScheme.substring(0, slash) else withoutScheme
        }
        return formatPageTitle(displayedTitle.takeUnless { it.isNullOrEmpty() } ?: context.getString(R.string.untitled), unwrapped)
    }

    /** z8.w2.f/v0.i: error-jump targets use URLDecoder, not the stricter i6.u decoder. */
    internal fun unwrapErrorUrl(url: String?): String? {
        val prefix = "v://error/jump?url="
        if (url == null || !url.startsWith(prefix, ignoreCase = true)) return url
        val end = url.indexOf('&', prefix.length).takeIf { it >= 0 } ?: url.length
        val target = url.substring(prefix.length, end)
        return try { URLDecoder.decode(target, "UTF-8") } catch (_: Exception) { target }
    }

    /** z8.b0.E calls i6.g0.f, then caps the decoded display URL at 8192 UTF-16 units. */
    internal fun displayUrl(url: String?): String? = url?.let { UrlInputText.display(it).take(8192) }

    /**
     * i6.i0.f deliberately uses string delimiters rather than Uri.host: preserve
     * its case, query-without-slash and first-colon behavior, including IPv6.
     */
    internal fun domain(url: String?): String {
        if (url == null) return ""
        val scheme = url.indexOf("://")
        if (scheme < 0) return ""
        val start = scheme + 3
        val end = url.indexOf('/', start).takeIf { it >= 0 } ?: url.length
        val authority = url.substring(start, end)
        val colon = authority.indexOf(':')
        return if (colon >= 0) authority.substring(0, colon) else authority
    }

    /** z8.b0.G, including the retained external search providers' original title fixes. */
    internal fun formatPageTitle(title: String, url: String?): String {
        if (url == null) return title
        val template = when {
            url.length > 38 && (title == "QQ浏览器搜索" || title == "QQ 浏览器搜索") ->
                "https://wap.sogou.com/web/sl?keyword="
            url.length > 47 && (title == "秘塔AI搜索" || title == "秘塔 AI 搜索") ->
                "https://metaso.cn/search/3333?q="
            else -> null
        }
        if (template != null) {
            val query = UrlInputText.extractSearch(url, template)
            if (!query.isNullOrEmpty()) return "$query - $title"
        }
        if (title == "网页无法打开" || title == "Webpage not available") {
            // i6.g0.b only removes a lowercase m./www. prefix when another dot remains.
            val host = domain(url)
            val dot = host.indexOf('.')
            if (dot == 1 && host[0] == 'm' && host.indexOf('.', dot + 1) >= 0) return host.substring(2)
            if (dot == 3 && host.startsWith("www") && host.indexOf('.', dot + 1) >= 0) return host.substring(4)
            return host
        }
        return title
    }
}
