package dev.ujhhgtg.via.browser.script

import dev.ujhhgtg.via.engine.EnginePage
import dev.ujhhgtg.via.data.BrowserPreferences
import java.util.UUID
import org.json.JSONObject

/** Parser/store plus the original n5.a injection and o5.a native-call contracts. */
class ScriptManager(
    private val store: ScriptStore,
    private val resources: ScriptResources = ScriptResources(store.context),
) {
    val secret: String = UUID.randomUUID().toString()

    /** The `via_gm` native endpoint for [page]; the backend exposes it to page JavaScript. */
    fun bridge(page: EnginePage, callbacks: ScriptBridge.Callbacks) = ScriptBridge(this, page, callbacks)

    /** Call on a worker after user-approved installation; network never runs during injection. */
    suspend fun ensureDependencies(script: UserScript): Boolean = resources.ensure(script)
    suspend fun fetchSource(url: String): String? = resources.fetchSource(url)
    fun update(script: UserScript): UserScript = store.find(store.save(script)) ?: script
    fun remove(id: Int): Boolean {
        val script = store.find(id)
        if (!store.remove(id)) return false
        script?.let { store.clearValues(it.scriptId) }
        return true
    }
    fun all(): List<UserScript> = store.list()
    fun findByScriptId(id: String): UserScript? = store.findByScriptId(id)
    fun getValue(id: String, name: String): String? = store.getValue(id, name)
    private val valueObservers = java.util.concurrent.CopyOnWriteArraySet<(String, String, String?) -> Unit>()
    internal fun addValueObserver(observer: (String, String, String?) -> Unit) { valueObservers.add(observer) }
    internal fun removeValueObserver(observer: (String, String, String?) -> Unit) { valueObservers.remove(observer) }
    fun setValue(id: String, name: String, value: String?): Boolean = store.setValue(id, name, value).also { saved ->
        if (saved) valueObservers.forEach { it(id, name, value) }
    }
    fun deleteValue(id: String, name: String): Boolean = store.deleteValue(id, name).also { deleted ->
        if (deleted) valueObservers.forEach { it(id, name, null) }
    }
    fun listValues(id: String): List<String> = store.listValues(id)
    fun resourceText(script: UserScript, name: String): String? = resources.resourceText(script, name)
    fun resourceUrl(script: UserScript, name: String): String? = resources.resourceUrl(script, name)
    fun cleanupResources(): Boolean = resources.cleanup(all())

    /** sa.d1.j4: refresh saved source/metadata, preserving enabled state and user overrides. Worker-only. */
    suspend fun updateFromNetwork(id: Int): UserScript? {
        val current = store.find(id) ?: return null
        val url = current.downloadUrl?.takeIf(String::isNotEmpty) ?: return null
        val source = resources.fetchSource(url) ?: return null
        val parsed = UserScript.parse(source, url) ?: return null
        val updated = update(parsed.copy(id = id, enabled = current.enabled, userOverrides = current.userOverrides))
        resources.ensure(updated)
        return updated
    }

    /** sb.q: call during the app's update pass on a worker; interval 0 disables checks. */
    suspend fun updateDue(preferences: BrowserPreferences, now: Long = System.currentTimeMillis()): Int {
        val interval = preferences.scriptUpdateInterval
        val before = now - interval
        if (interval < 3_600_000L || preferences.getLong("updated_scripts", 0L) >= before) return 0
        val eligible = all().filter { it.enabled && !it.downloadUrl.isNullOrEmpty() && it.lastUpdatedAt < before }
        val updated = eligible.count { updateFromNetwork(it.id) != null }
        preferences.putLong("updated_scripts", System.currentTimeMillis())
        return updated
    }

    fun scriptsFor(url: String, runAt: ScriptRunAt? = null): List<UserScript> = (store.list() + listOfNotNull(BuiltinExpandScript.forRuntime(store.context, store))).filter {
        it.enabled && it.appliesTo(url) && (runAt == null || it.runsAt(runAt))
    }.sortedBy { it.content.length } // p5.b.s orders loaded patterns by LENGTH(content) ASC.

    /** n5.a.b: installation API and every matched userscript are separate page evaluations. */
    fun phaseSources(url: String, runAt: ScriptRunAt): List<String> = buildList {
        if (url.isEmpty() || url.startsWith("file://")) return@buildList
        if (runAt == ScriptRunAt.START || runAt == ScriptRunAt.END) {
            // n5.a.e deliberately takes the network URL authority verbatim, including its port.
            val scheme = url.indexOf("://")
            val authority = if (android.webkit.URLUtil.isNetworkUrl(url) && scheme >= 0) {
                val start = scheme + 3
                val end = url.indexOf('/', start).let { if (it < 0) url.length else it }
                url.substring(start, end).lowercase(java.util.Locale.ROOT)
            } else ""
            if (arrayOf("greasyfork.org", "userscript.zone", "openuserjs.org", "sleazyfork.org").any(authority::contains)) {
                add(GmApiSource.installationApi(secret))
            }
        }
        scriptsFor(url, runAt).forEach { script ->
            if (script.content.isNotEmpty()) {
                val wrap = script.flags and 1 == 0
                val source = buildString {
                    if (wrap) append("(function(){\n")
                    append(GmApiSource.scriptApi(script.scriptId, secret, script.grantMask()))
                    script.requires.forEach { append(resources.text(it)); append("\n\n") }
                    append(script.content)
                    if (wrap) append("\n})();")
                }
                add(source)
            }
        }
    }

    fun menuStateSource(): String = GmApiSource.menuState(secret)
    /** sa.i1.b: the original executes the registered callback directly. */
    fun executeMenuSource(scriptId: String, name: String): String =
        "javascript:window[${JSONObject.quote(secret)}]['gm_menus'][${JSONObject.quote(scriptId)}][${JSONObject.quote(name)}]();"

    fun close() = store.close()
}
