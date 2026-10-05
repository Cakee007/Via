package dev.ujhhgtg.via.browser.filter

import android.webkit.MimeTypeMap
import dev.ujhhgtg.via.engine.ResourceRequest

/** b5.c.j/l: one request can carry document/WebSocket/XHR and content-type bits. */
internal fun ResourceRequest.toFilterRequest(topUrl: String?): FilterRequest {
    val target = url
    val main = isMainFrame && target == topUrl
    var mask = if (main) 512 else 0
    if (target.startsWith("ws")) mask = mask or 8192
    if (headers["X-Requested-With"] == "XMLHttpRequest") mask = mask or 16384
    val content = type?.let(::engineTypeMask) ?: inferredContentMask(target, main)
    mask = mask or content
    val resourceType = ResourceType.entries.firstOrNull { it.mask == content } ?: if (main) ResourceType.DOCUMENT else ResourceType.OTHER
    val sourceHost = FilterRule.host(topUrl)
    val resourceHost = FilterRule.host(target)
    return FilterRequest(target, topUrl, resourceType, isMainFrame,
        sourceHost != null && resourceHost != null && resourceHost != sourceHost && !resourceHost.contains(FilterRule.baseDomain(sourceHost)), headers, mask)
}

/** Content bits for an engine-classified request; documents use the generic content bit, as b5.c.l does for main frames. */
private fun engineTypeMask(type: ResourceRequest.Type): Int = when (type) {
    ResourceRequest.Type.SCRIPT -> ResourceType.SCRIPT.mask
    ResourceRequest.Type.STYLESHEET -> ResourceType.STYLESHEET.mask
    ResourceRequest.Type.IMAGE -> ResourceType.IMAGE.mask
    ResourceRequest.Type.MEDIA -> ResourceType.MEDIA.mask
    ResourceRequest.Type.FONT -> ResourceType.FONT.mask
    ResourceRequest.Type.SUBDOCUMENT -> ResourceType.SUBDOCUMENT.mask
    ResourceRequest.Type.DOCUMENT, ResourceRequest.Type.XHR, ResourceRequest.Type.WEBSOCKET, ResourceRequest.Type.OTHER -> ResourceType.OTHER.mask
}

/** WebView reports no request type, so b5.c.l guesses it from the file extension, then the Accept header. */
private fun ResourceRequest.inferredContentMask(target: String, main: Boolean): Int {
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
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)?.let(::mimeMask)
    }
    return known ?: if (main) 16 else headers["Accept"]?.takeUnless { it == "*/*" }?.substringBefore(',')?.trim()?.let(::mimeMask) ?: 3312
}
