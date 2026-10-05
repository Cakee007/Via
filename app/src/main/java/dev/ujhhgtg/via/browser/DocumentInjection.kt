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
        val url = page.url ?: return
        if (!page.javaScriptEnabled || url.isEmpty() || url.startsWith("file://", true)) return
        page.evaluate(documentScripts.bootstrap(secret, viewId))
    }

    /** Removes the night CSS fallback from an already loaded page. */
    fun removeNightCss(page: EnginePage) = page.evaluate(documentScripts.night(false))

    /** runAt = 1 (head), 2 (DOMContentLoaded), 4 (load). */
    fun inject(page: EnginePage, runAt: Int): Boolean {
        if (!page.javaScriptEnabled) return false
        val url = page.url?.takeIf(String::isNotEmpty) ?: return false
        if (url.startsWith("file://", true)) return false
        val host = DocumentPolicy.host(url)
        if (host.isEmpty()) return false
        val enabled = preferences.scriptsEnabled
        if (runAt == 4) {
            page.evaluate(documentScripts.marker(secret))
            onReaderCheckRequested()
            return !enabled || scripts?.injectPhase(page, url, ScriptRunAt.IDLE) == true
        }
        if (runAt == 1) {
            val site = siteConfiguration(DocumentPolicy.authority(url))?.takeIf { it.isEnabled }
            val flags = preferences.webFlags
            val source = StringBuilder()
            val blocking = site?.adBlocking(flags) ?: (flags and 1 != 0)
            if (blocking && UrlResolver.isHttpUrl(url)) {
                source.append(documentScripts.blockerLink(host))
                val css = documentScripts.blockerStyle(filterEngine?.cosmeticCss(url))
                if (css.isNotEmpty()) page.evaluate(css)
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
            if (font.isNotEmpty()) source.append(documentScripts.font(font))
            if (source.isNotEmpty()) page.evaluate(source.toString())
            return !enabled || scripts?.injectPhase(page, url, ScriptRunAt.START) == true
        }
        val injected = enabled && scripts?.injectPhase(page, url, ScriptRunAt.END) == true
        documentReader.prepare(page) { prepared -> if (prepared) onReaderCheckRequested() }
        return injected
    }
}
