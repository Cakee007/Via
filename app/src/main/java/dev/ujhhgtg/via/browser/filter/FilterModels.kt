package dev.ujhhgtg.via.browser.filter

import android.webkit.WebResourceRequest
import java.net.URI

enum class ResourceType(val mask: Int) {
    OTHER(16), SCRIPT(32), IMAGE(64), STYLESHEET(128), SUBDOCUMENT(256), DOCUMENT(512), MEDIA(1024), FONT(2048), XHR(16384), WEBSOCKET(8192)
}

data class FilterRequest(
    val url: String,
    val topUrl: String? = null,
    val type: ResourceType = ResourceType.OTHER,
    val isMainFrame: Boolean = false,
    val isThirdParty: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val typeMask: Int = type.mask,
)

data class FilterRule(
    val raw: String,
    val pattern: String,
    val domains: List<String> = emptyList(),
    val excludedDomains: List<String> = emptyList(),
    val mask: Int = 0,
    val thirdPartyOnly: Boolean = false,
    val thirdPartyExcluded: Boolean = false,
    val exception: Boolean = false,
    val domainRules: List<String> = domains + excludedDomains.map { "~$it" },
) {
    companion object {
        fun host(url: String?): String? = runCatching { URI(url ?: "").host?.lowercase() }.getOrNull()
        /** w4.d.h with b5.c.u/w: case-sensitive glob, | matches / or ., ^ a separator. */
        fun wildcardMatch(pattern: String, value: String): Boolean {
            if (pattern.isEmpty()) return true
            if (pattern.none { it == '*' || it == '^' || it == '|' }) return value.contains(pattern)
            val glob = (if (pattern.startsWith('*')) "" else "*") + pattern + if (pattern.endsWith('*')) "" else "*"
            var target = 0; var rule = 0; var star = -1; var retry = -1
            while (target < value.length) {
                val character = value[target]
                if (rule < glob.length && (glob[rule] == character || glob[rule] == '|' && (character == '/' || character == '.') || glob[rule] == '^' && separator(character))) {
                    target++; rule++
                } else if (rule < glob.length && glob[rule] == '*') {
                    star = rule++; retry = target
                } else if (star >= 0) { rule = star + 1; target = ++retry }
                else return false
            }
            while (rule < glob.length && glob[rule] == '*') rule++
            return rule == glob.length
        }

        private fun separator(c: Char) = c !in '0'..'9' && c !in 'A'..'Z' && c !in 'a'..'z' && c !in "_-.%"
        fun baseDomain(host: String): String {
            val last = host.lastIndexOf('.')
            if (last < 0) return host
            var previous = host.lastIndexOf('.', last - 1)
            if (previous < 0) return host
            if (host.substring(previous + 1, last) in setOf("com", "net", "org", "gov", "co")) previous = host.lastIndexOf('.', previous - 1)
            return host.substring(previous + 1)
        }

    }
}

/** b5.c.j/l: one request can carry document/WebSocket/XHR and content-type bits. */
fun WebResourceRequest.toFilterRequest(topUrl: String? = null): FilterRequest {
    val target = url.toString()
    val headers = requestHeaders ?: emptyMap()
    val main = isForMainFrame && target == topUrl
    var mask = if (main) 512 else 0
    if (target.startsWith("ws")) mask = mask or 8192
    if (headers["X-Requested-With"] == "XMLHttpRequest") mask = mask or 16384
    val filename = target.substringBefore('?').substringAfterLast('/')
    val extension = filename.indexOf('.').takeIf { it > 0 }?.let { filename.substring(it + 1).lowercase() }?.takeIf { it.length <= 8 }
    fun mimeMask(mime: String?): Int = when {
        mime in listOf("application/javascript", "application/x-javascript", "text/javascript", "application/json") -> 32
        mime == "text/css" -> 128
        mime?.startsWith("image/") == true -> 64
        mime?.startsWith("video/") == true || mime?.startsWith("audio/") == true -> 1024
        mime?.startsWith("font/") == true -> 2048
        else -> 16
    }
    val known = when (extension) {
        "js", "json" -> 32
        "css" -> 128
        "otf", "ttf", "ttc", "woff", "woff2" -> 2048
        "php", null -> null
        else -> android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)?.let(::mimeMask)
    }
    val content = known ?: if (main) 16 else headers["Accept"]?.takeUnless { it == "*/*" }?.substringBefore(',')?.trim()?.let(::mimeMask) ?: 3312
    mask = mask or content
    val type = ResourceType.entries.firstOrNull { it.mask == content } ?: if (main) ResourceType.DOCUMENT else ResourceType.OTHER
    val sourceHost = FilterRule.host(topUrl)
    val resourceHost = FilterRule.host(target)
    return FilterRequest(target, topUrl, type, isForMainFrame,
        sourceHost != null && resourceHost != null && resourceHost != sourceHost && !resourceHost.contains(FilterRule.baseDomain(sourceHost)), headers, mask)
}
