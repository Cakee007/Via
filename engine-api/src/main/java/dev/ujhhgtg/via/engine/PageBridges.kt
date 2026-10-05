package dev.ujhhgtg.via.engine

/**
 * Native methods that page JavaScript reaches as `window.via` and `window.via_page`.
 * Backends expose them under those names and forward each call. Calls may arrive on a background thread.
 */
interface PageBridge {
    fun cmd(command: Int): Int
    fun download(token: String?, url: String?, data: String?)
    fun postMessage(token: String?, json: String?)
    fun record(url: String?, selector: String?)
    fun addon(id: String?)
    fun getInstalledAddonID(): String
    fun toast(text: String?)
}

/** The userscript channel page JavaScript reaches as `via_gm.call(message, secret)`; returns a JSON reply or null. */
fun interface ScriptChannel {
    fun call(message: String?, secret: String?): String?
}
