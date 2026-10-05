package dev.ujhhgtg.via.browser

import android.content.Context
import dev.ujhhgtg.via.browser.filter.FilterEngine
import dev.ujhhgtg.via.browser.script.ScriptManager
import dev.ujhhgtg.via.browser.script.ScriptRunAt
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.SiteConfiguration
import dev.ujhhgtg.via.engine.EnginePage

/**
 * c8.s6.L/va and i6.s/r: decides what each document phase injects. Engine-neutral: a backend
 * announces phases (WebView via bootstrap.js and action 101) and this class runs the scripts.
 */
internal class DocumentInjection(
    context: Context,
    private val preferences: BrowserPreferences,
    private val siteConfiguration: (String) -> SiteConfiguration?,
    private val filterEngine: FilterEngine?,
    private val scripts: ScriptManager?,
    private val secret: String,
    private val onReaderCheckRequested: () -> Unit,
) {
    private val documentScripts = DocumentScripts(context)
    private val documentReader by lazy { dev.ujhhgtg.via.reader.ReaderMode(context) }

    /** The phase announcer; [viewId] identifies the page in its action-101 messages. */
    fun installCoordinator(page: EnginePage, viewId: Int) {
        if (page.preloadsDocumentScripts) return
        val url = page.url ?: return
        if (!page.javaScriptEnabled || url.isEmpty() || url.startsWith("file://", true)) return
        page.evaluate(documentScripts.bootstrap(secret, viewId))
    }

    /** Removes the night CSS fallback from an already loaded page. */
    fun removeNightCss(page: EnginePage) = page.evaluate(documentScripts.night(false))
    fun applyNightCss(page: EnginePage, enabled: Boolean) = page.evaluate(documentScripts.night(enabled))

    /** runAt = 1 (head), 2 (DOMContentLoaded), 4 (load). */
    fun inject(page: EnginePage, runAt: Int): Boolean {
        if (!page.javaScriptEnabled) return false
        val url = page.url?.takeIf(String::isNotEmpty) ?: return false
        if (url.startsWith("file://", true)) return false
        if (!page.preloadsDocumentScripts) sources(url, runAt).forEach { page.evaluate(it) }
        if (runAt == 4) onReaderCheckRequested()
        if (runAt == 2) documentReader.prepare(page) { prepared -> if (prepared) onReaderCheckRequested() }
        return true
    }

    fun sources(url: String, runAt: Int, includeStartScripts: Boolean = true, inlineResources: Boolean = false): List<String> = buildList {
        if (url.startsWith("file://", true)) return@buildList
        val host = DocumentPolicy.host(url)
        if (host.isEmpty()) return@buildList
        val enabled = preferences.scriptsEnabled
        if (runAt == 4) {
            add(documentScripts.marker(secret))
            if (enabled) addAll(scripts?.phaseSources(url, ScriptRunAt.IDLE).orEmpty())
            return@buildList
        }
        if (runAt == 1) {
            val site = siteConfiguration(DocumentPolicy.authority(url))?.takeIf { it.isEnabled }
            val flags = preferences.webFlags
            val source = StringBuilder()
            val blocking = site?.adBlocking(flags) ?: (flags and 1 != 0)
            if (blocking && UrlResolver.isHttpUrl(url)) {
                if (!inlineResources) source.append(documentScripts.blockerLink(host))
                val css = documentScripts.blockerStyle(filterEngine?.cosmeticCss(url))
                if (css.isNotEmpty()) add(css)
            }
            val desktop = site?.desktopMode(flags) ?: (flags and 2048 != 0)
            if (host != "music.163.com" && host != "taobao.com" && desktop) source.append(documentScripts.desktopViewport())
            else if (flags and 524288 != 0) source.append(documentScripts.source("viewport-unlock"))
            val clipboard = site?.clipboardMode?.takeIf { it != 0 } ?: when {
                flags and 262144 != 0 -> 3
                flags and 131072 != 0 -> 2
                else -> 1
            }
            source.append(documentScripts.clipboard(clipboard))
            source.append(documentScripts.downloadLinks(secret))
            source.append(documentScripts.source("blob-cache"))
            source.append(documentScripts.source("print"))
            source.append(documentScripts.source("notification"))
            if (flags and 1073741824 == 0) source.append(documentScripts.source("vibration"))
            source.append(documentScripts.passwordCapture(secret))
            val font = preferences.uiFont
            if (font.isNotEmpty()) {
                if (inlineResources) {
                    val file = java.io.File(preferences.fontDirectory, font)
                    if (file.isFile) {
                        val mime = dev.ujhhgtg.via.downloads.DownloadMimeTypes.mime(file.extension, "font/ttf") ?: "font/ttf"
                        val data = android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
                        source.append(documentScripts.fontUri("data:$mime;base64,$data"))
                    }
                } else source.append(documentScripts.font(font))
            }
            if (inlineResources && preferences.isNightMode && preferences.nightCss) source.append(documentScripts.night(true))
            if (source.isNotEmpty()) add(source.toString())
            if (enabled && includeStartScripts) addAll(scripts?.phaseSources(url, ScriptRunAt.START).orEmpty())
            return@buildList
        }
        if (enabled) addAll(scripts?.phaseSources(url, ScriptRunAt.END).orEmpty())
    }
}
