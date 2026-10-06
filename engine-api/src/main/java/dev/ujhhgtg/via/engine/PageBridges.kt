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

    /** As [call]; asynchronous replies are evaluated with [reply] (a subframe), or in the page when null. */
    fun call(message: String?, secret: String?, reply: ((String) -> Unit)?): String? = call(message, secret)

    /** Initial synchronous GM replies for an asynchronous engine bridge. JSON, scoped to this URL. */
    fun snapshot(url: String): String? = null

    /** Persisted value changes, including changes made by another open page. Null removes the observer. */
    fun observeValues(observer: ((scriptId: String, name: String, value: String?, oldValue: String?, origin: Any?) -> Unit)?) = Unit
    fun valueChangeScript(scriptId: String, name: String, value: String?, oldValue: String?, remote: Boolean): String? = null
}
