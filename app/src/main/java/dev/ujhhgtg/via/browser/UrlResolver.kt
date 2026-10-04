package dev.ujhhgtg.via.browser

import android.webkit.URLUtil
import androidx.core.net.toUri
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** URL and search handling shared by the shell and individual browser tabs. */
object UrlResolver {
    const val HOME = "about:home"
    const val BOOKMARKS = "v://bookmarks"
    const val HISTORY = "v://history"
    const val SCANNER = "v://scanner"
    const val DOWNLOADER = "v://downloader"
    const val READ_ALOUD = "v://readaloud"
    const val SEARCH = "v://search"
    const val ERROR_JUMP = "v://error/jump"
    const val TRANSLATOR = "v://translator/translate"

    /** The address/intent entry point rejects empty input before original i6.i0.v normalization. */
    fun resolveInput(text: String?, searchTemplate: String): String? {
        val input = text?.trim().orEmpty()
        return input.takeIf(String::isNotEmpty)?.let { normalizeInput(it, searchTemplate) }
    }

    /** i6.i0.v. A null search template returns unrecognized text verbatim, as settings requires. */
    fun normalizeInput(text: String?, searchTemplate: String? = null): String? {
        if (text == null) return null
        val parsed = UrlParser(text)
        if (text.isEmpty() || !parsed.isValid) return if (searchTemplate.isNullOrEmpty()) text else search(searchTemplate, text)
        val input = parsed.value!!
        val host = parsed.hostRange
        return buildString {
            if (!parsed.hasScheme) {
                if (host == null) append("http://")
                else append(if (usesHttp(UrlParser.normalizeDots(parsed.host ?: input.substring(host[0], host[1])))) "http://" else "https://")
            }
            if (host != null) {
                append(input.substring(0, host[0]))
                val value = input.substring(host[0], host[1])
                append(if (isHttpUrl(input)) UrlPunycode.encodeHost(UrlParser.normalizeDots(value)) else value)
                if (host[1] < input.length) append(input.substring(host[1]))
            } else if (input.startsWith("javascript:", true)) append("javascript:").append(input.substring(11))
            else append(input)
        }
    }

    private fun usesHttp(host: String): Boolean {
        if (host.startsWith('[') || ':' in host || host.count { it == '.' } == 3 && host.all { it == '.' || it in '0'..'9' }) return true
        val length = if (host.last() == '.') host.length - 1 else host.length
        val dot = host.lastIndexOf('.', length - 1)
        return dot < 0 || when (TopLevelDomains.hash(host.substring(dot + 1, length).lowercase(java.util.Locale.ROOT))) {
            186586613 -> TopLevelDomains.hash(host.substring(host.lastIndexOf('.', dot - 1) + 1, dot).lowercase(java.util.Locale.ROOT)) == 233246255
            233246255, 689013009, 884420632, 1578185509 -> true
            else -> false
        }
    }

    /** i6.g0.e/a: extract HTTP URLs from surrounding text using paired punctuation boundaries. */
    fun extractUrls(text: String?): List<String> {
        if (text == null || text.length < 8) return emptyList()
        val input = text.trim()
        val result = mutableListOf<String>()
        val pairs = "''\"\"{}()[]<>「」『』【】〔〕〖〗〘〙〚〛（）［］｛｝＜＞〈〉《》‘’“”"
        var cursor = 0
        while (cursor < input.length) {
            val separator = input.indexOf("://", cursor)
            if (separator < 0) break
            val start = when {
                separator >= 5 && input.substring(separator - 5, separator).equals("https", true) -> separator - 5
                separator >= 4 && input.substring(separator - 4, separator).equals("http", true) -> separator - 4
                else -> { cursor = separator + 3; continue }
            }
            val previous = if (start > 0) input[start - 1] else ' '
            val pair = pairs.indexOf(previous)
            val closing = if (pair >= 0 && pair % 2 == 0) pairs[pair + 1] else ' '
            val contentStart = separator + 3
            var end = (contentStart until input.length).firstOrNull { input[it] == closing || input[it].isWhitespace() || input[it] == '\u00a0' } ?: -1
            if (end < 0) {
                if (start == 0) return emptyList()
                end = input.length
            }
            if (end > contentStart) input.substring(start, end).takeIf { UrlParser(it).isValid }?.let(result::add)
            cursor = end + 1
        }
        return result
    }

    fun search(template: String, query: String): String {
        if (template.isEmpty() || query.isEmpty()) return query
        val index = listOf("%@", "%s", "%S").map(template::indexOf).firstOrNull { it >= 0 } ?: -1
        if (index == 0 && template.length == 2) return query
        val encoded = encodeQuery(query.trim())
        return if (index >= 0) template.substring(0, index) + encoded + template.substring(index + 2) else template + encoded
    }

    fun translate(text: String): String = "$TRANSLATOR?text=${encodeQuery(text)}"

    fun unwrapErrorJump(value: String?): String? = value?.takeIf { it.startsWith(ERROR_JUMP) }?.let {
        val marker = "url="; val start = it.indexOf(marker); if (start < 0) null else runCatching { java.net.URLDecoder.decode(it.substring(start + marker.length), "UTF-8") }.getOrNull()
    }

    fun isHttpUrl(value: String?): Boolean = value?.startsWith("http://", true) == true || value?.startsWith("https://", true) == true

    /** Handles the scheme checks used by the original WebView client. */
    fun isLoadable(value: String?): Boolean {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return false
        if (text.startsWith("view-source:", ignoreCase = true)) return true
        if (text.startsWith("v://", ignoreCase = true) || text.startsWith("folder://", ignoreCase = true) ||
            text.startsWith("history://", ignoreCase = true)) return true
        if (runCatching { URLUtil.isValidUrl(text) }.getOrDefault(false)) return true
        val scheme = runCatching { text.toUri().scheme?.lowercase() }.getOrNull()
        return scheme in setOf("http", "https", "ftp", "file", "content", "javascript")
    }

    fun isInternal(value: String?): Boolean {
        val text = value.orEmpty()
        return text.startsWith("v://", ignoreCase = true) ||
            text.startsWith("folder://", ignoreCase = true) ||
            text.startsWith("history://", ignoreCase = true)
    }

    fun isExternalScheme(value: String?): Boolean {
        val scheme = runCatching { value.orEmpty().toUri().scheme?.lowercase() }.getOrNull()
        return !scheme.isNullOrEmpty() && scheme !in setOf("http", "https", "ftp", "file", "content", "about", "v", "view-source", "javascript")
    }

    fun host(value: String?): String? = runCatching { value.orEmpty().toUri().host?.lowercase() }.getOrNull()

    /** SiteConf keys include the port, as in i6.i0.e and the permission client e8.y0. */
    fun siteKey(value: String?): String? = runCatching {
        val uri = value.orEmpty().toUri()
        val host = uri.host?.lowercase() ?: return null
        if (uri.port == -1) host else "$host:${uri.port}"
    }.getOrNull()

    /** Extracts the payload used by common download-manager schemes. */
    fun unwrapDownloadScheme(value: String?): String? {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return null
        return when {
            text.startsWith("thunder://", ignoreCase = true) -> decodeBase64Payload(text.substring(10))?.removePrefix("AA")
                ?.removeSuffix("ZZ")?.trim()
            text.startsWith("qqdl://", ignoreCase = true) -> decodeBase64Payload(text.substring(7))
            text.startsWith("flashget://", ignoreCase = true) -> decodeBase64Payload(text.substring(11))
                ?.replace("[FLASHGET]", "", ignoreCase = true)
            else -> null
        }?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun decodeBase64Payload(payload: String): String? = runCatching {
        val decoder = android.util.Base64.decode(payload, android.util.Base64.DEFAULT)
        String(decoder, StandardCharsets.UTF_8)
    }.getOrNull()

    private fun encodeQuery(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

}
