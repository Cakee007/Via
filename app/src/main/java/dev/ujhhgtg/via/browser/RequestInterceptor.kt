package dev.ujhhgtg.via.browser

import android.content.Context
import androidx.core.net.toUri
import dev.ujhhgtg.via.browser.filter.FilterEngine
import dev.ujhhgtg.via.browser.filter.FilterRequest
import dev.ujhhgtg.via.browser.filter.description
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.SiteConfiguration
import dev.ujhhgtg.via.engine.InterceptDecision
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Locale

/**
 * p4.j / c8.ua.E0: Via's request policy, independent of the engine. Runs on the engine's network
 * thread: virtual resources, app-private file protection, ad blocking, and the resource log.
 */
internal class RequestInterceptor(
    private val context: Context,
    private val preferences: BrowserPreferences,
    private val siteConfiguration: (String) -> SiteConfiguration?,
    private val filterEngine: FilterEngine?,
    private val allowBlockedPage: (String) -> Boolean,
    private val onRequestBlocked: (String) -> Unit,
    /** Called after a request is logged, with whether the page now has media resources. */
    private val onResourceRecorded: (Boolean) -> Unit,
) {
    val resourceLog = ResourceLog()

    /**
     * [request] builds the filter request lazily, since most requests never reach the filter.
     * [rangeFromStart] marks a media range request beginning at byte 0.
     */
    fun intercept(url: String, topUrl: String?, rangeFromStart: Boolean, request: () -> FilterRequest): InterceptDecision {
        val intercepted = injectedResource(url) ?: localFileResponse(url)
        val blocked = intercepted == null && filteringEnabled(topUrl) && !url.startsWith("file://", true) &&
            filterEngine?.shouldBlock(request()) == true
        // ua.U1 counts matched requests before E0 checks the session's continue-loading hosts.
        if (blocked) onRequestBlocked(url)
        // ua.E0 consults ua.q only for the top URL, after the filter has matched; subresources still block normally.
        val bypass = blocked && (topUrl == null || url == topUrl) && allowBlockedPage(url)
        val decision = intercepted ?: if (blocked && !bypass) {
            if (topUrl == null || url == topUrl) blockedPage(url) else blockedResource(url)
        } else InterceptDecision.Allow
        // e8.j.F records the result of the entire interception chain, including
        // virtual resources, and excludes only generated documents/blocker.css.
        record(url, decision != InterceptDecision.Allow, rangeFromStart)
        return decision
    }

    /** c8.ua.E0/s9.d.f: the custom UI font and the cosmetic blocker stylesheet are virtual resources. */
    fun injectedResource(url: String): InterceptDecision? {
        val fontName = preferences.uiFont
        val encoded = if (fontName.isEmpty()) "" else java.net.URLEncoder.encode(fontName, "UTF-8").replace("+", "%20")
        if (encoded.isNotEmpty() && url.endsWith(encoded)) {
            val file = File(preferences.fontDirectory, fontName)
            if (!file.isFile) return null
            val mime = dev.ujhhgtg.via.downloads.DownloadMimeTypes.mime(file.extension, "font/ttf") ?: "font/ttf"
            return InterceptDecision.Serve(mime, mapOf("Cache-Control" to "immutable", "Access-Control-Allow-Origin" to "*", "Content-Type" to mime)) { file.inputStream() }
        }
        if (!url.endsWith("via_inject_blocker.css")) return null
        val css = filterEngine?.cosmeticCss(url).orEmpty()
        if (css.isEmpty()) return InterceptDecision.BlockEmpty
        return InterceptDecision.Serve("text/css", mapOf("Cache-Control" to "no-cache", "Access-Control-Allow-Origin" to "*", "Content-Type" to "text/css")) {
            ByteArrayInputStream(css.toByteArray(Charsets.UTF_8))
        }
    }

    /** ua.U1/b1 reads the live global and per-site switch for the actual top document. */
    private fun filteringEnabled(url: String?): Boolean {
        val file = url?.startsWith("file://", true) == true
        val site = if (file) null else url?.let(DocumentPolicy::authority)?.let(siteConfiguration)?.takeIf { it.isEnabled }
        return !file && (site?.adBlocking(preferences.webFlags) ?: (preferences.webFlags and 1 != 0))
    }

    /** e8.e0 permits page assets, but refuses other app-private file types. */
    private fun localFileResponse(url: String): InterceptDecision? {
        if (!url.startsWith("file://", true)) return null
        val path = url.toUri().path ?: return null
        val extension = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension in setOf("html", "htm", "css", "png", "js", "mht", "pdf", "jpg", "jpeg", "ttf", "otf", "woff", "woff2")) return null
        fun canonical(file: File): String = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        return if (canonical(File(path)).startsWith(canonical(context.dataDir)))
            InterceptDecision.Serve("text/plain") { ByteArrayInputStream(byteArrayOf()) } else null
    }

    /** ua.E0 queries the display rule with b5.c.k(url, null), separately from the request match. */
    private fun blockedPage(url: String): InterceptDecision {
        val rule = filterEngine?.matchingRule(ResourceDocumentActions.filterRequest(url))?.description().orEmpty()
        val html = BlockedPageDocument.html(context, url, rule).toByteArray(Charsets.UTF_8)
        // E0 uses the same headers as its blocker stylesheet response.
        return InterceptDecision.Serve("text/html", mapOf("Cache-Control" to "no-cache", "Access-Control-Allow-Origin" to "*", "Content-Type" to "text/css")) {
            ByteArrayInputStream(html)
        }
    }

    private fun blockedResource(url: String): InterceptDecision = blockedResourceDecision(url)

    private fun record(url: String, blocked: Boolean, rangeFromStart: Boolean) {
        if (ResourceDocument.isInternalPage(context, url) || UrlResolver.isInternal(url) || url.endsWith("via_inject_blocker.css")) return
        onResourceRecorded(resourceLog.add(url, blocked, rangeFromStart))
    }
}

/** A blocked subresource gets an empty body for documents, scripts and styles, else a 1×1 GIF. */
internal fun blockedResourceDecision(url: String): InterceptDecision {
    // z8.b0.F retains the leading dot; preserve E0's literal comparison as well.
    val filename = url.substringBeforeLast('?').substringAfterLast('/')
    val dot = filename.lastIndexOf('.')
    val extension = if (dot >= 0) filename.substring(dot).takeIf { it.length in 2..6 }.orEmpty() else ""
    return if (extension.length > 1 && "html|htm|css|js".contains(extension)) InterceptDecision.BlockEmpty else InterceptDecision.BlockImage
}
