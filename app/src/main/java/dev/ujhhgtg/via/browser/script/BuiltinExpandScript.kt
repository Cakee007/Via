package dev.ujhhgtg.via.browser.script

import android.content.Context
import dev.ujhhgtg.via.data.BrowserPreferences
import java.util.UUID

/** z8.j0/c8.lb: built-in -99 is memory-only; an installed replacement takes precedence. */
object BuiltinExpandScript {
    const val REPLACEMENT_ID = "fa6f3153b591e06fbf0170d935ad0afe"
    private val runtimeId = UUID.randomUUID().toString().replace("-", "")
    private var cached: UserScript? = null

    fun enabled(context: Context): Boolean = ScriptStore(context).use { store ->
        store.findByScriptId(REPLACEMENT_ID)?.enabled ?: (BrowserPreferences(context).appFlags and 1024 != 0)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val preferences = BrowserPreferences(context)
        preferences.appFlags = if (enabled) preferences.appFlags or 1024 else preferences.appFlags and 1024.inv()
        ScriptStore(context).use { store ->
            store.findByScriptId(REPLACEMENT_ID)?.let { store.setEnabled(it.id, enabled) }
        }
    }

    /** Append to the normal monkey runtime candidates, never to ScriptStore.list or its database. */
    fun forRuntime(context: Context, store: ScriptStore): UserScript? {
        if (BrowserPreferences(context).appFlags and 1024 == 0 || store.findByScriptId(REPLACEMENT_ID) != null) return null
        cached?.let { return it }
        return UserScript(
            id = -99, scriptId = runtimeId, name = "",
            content = context.assets.open("browser/injection/expand-content.js").bufferedReader().use { it.readText() },
            matches = UserScript.jsonStrings(context.assets.open("browser/injection/expand-content-matches.json").bufferedReader().use { it.readText() }),
            runAt = ScriptRunAt.START, enabled = true,
        ).also { cached = it }
    }
}
