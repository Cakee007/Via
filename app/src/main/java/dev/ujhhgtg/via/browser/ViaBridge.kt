package dev.ujhhgtg.via.browser

import dev.ujhhgtg.via.engine.EnginePage
import java.util.UUID

/**
 * c8.s6.u/e8.i: the page-facing `window.via` bridge. Engine-neutral: each backend exposes these
 * methods to page JavaScript under the names `via` and `via_page`, and forwards calls here.
 * Calls may arrive on a background thread.
 */
class ViaBridge(private val page: EnginePage, private val callbacks: Callbacks, val secret: String = UUID.randomUUID().toString()) {
    interface Callbacks {
        fun command(page: EnginePage, command: Int): Int = 0
        fun download(page: EnginePage, url: String, name: String?, mime: String?) = Unit
        fun message(page: EnginePage, token: String, json: String) = Unit
        fun record(page: EnginePage, url: String, mime: String?) = Unit
        fun toast(page: EnginePage, text: String) = Unit
        fun addon(page: EnginePage, id: String) = Unit
        fun installedAddonIds(page: EnginePage): String = "[]"
    }
    fun cmd(command: Int): Int = callbacks.command(page, command)
    /** c8.s6.u.download(secret, sourceUrl, downloadedData), not (url, filename, MIME). */
    fun download(token: String?, url: String?, data: String?) {
        if (token == secret && !url.isNullOrEmpty()) callbacks.download(page, url, null, data)
    }
    fun postMessage(token: String?, json: String?) { if (token == secret && !json.isNullOrBlank()) callbacks.message(page, token, json) }
    fun record(url: String?, selector: String?) { if (!url.isNullOrEmpty() && !url.startsWith("file://")) callbacks.record(page, url, selector) }
    fun addon(id: String?) { if (!id.isNullOrBlank()) callbacks.addon(page, id) }
    fun getInstalledAddonID(): String = callbacks.installedAddonIds(page)
    fun toast(text: String?) { if (!text.isNullOrBlank()) callbacks.toast(page, text) }
}
