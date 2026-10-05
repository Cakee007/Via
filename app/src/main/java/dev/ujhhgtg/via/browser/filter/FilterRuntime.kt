package dev.ujhhgtg.via.browser.filter

import android.content.Context
import android.util.Log
import dev.ujhhgtg.via.data.BrowserPreferences
import java.util.concurrent.Executors

/** pa.c.c/pa.r.c: one process filter repository, initially loaded by z8.h.b(m9.k). */
internal object FilterRuntime {
    private val filters = FilterEngine(dev.ujhhgtg.via.engine.Engines.backend.engineMajorVersion)
    private val loader = Executors.newSingleThreadExecutor()
    @Volatile private var builtIn: Boolean? = null
    private var customRules: CustomFilterRules? = null

    fun get(context: Context, includeBuiltIn: Boolean): FilterEngine {
        // u4.a.p reloads only when the built-in-list choice actually changes.
        if (builtIn != includeBuiltIn) {
            builtIn = includeBuiltIn
            reload(context)
        }
        return filters
    }

    /** A saved custom/subscription list changes the repository, unlike ordinary WebSettings. */
    fun listsChanged(context: Context) {
        if (builtIn != null) reload(context) else customRules = null
    }

    private fun custom(store: FilterStore): CustomFilterRules = customRules
        ?: CustomFilterRules(store.customFile).also { customRules = it }

    /** u4.a.a/n -> x4.a.e/d: a single custom change keeps resident token assignments intact. */
    fun addCustom(store: FilterStore, raw: String): Boolean {
        if (filters.isLoading) return false
        val parsed = custom(store).add(raw) ?: return false
        if (builtIn != null) filters.addRule(parsed)
        return true
    }

    fun removeCustom(store: FilterStore, raw: String): Boolean {
        if (filters.isLoading) return false
        val parsed = custom(store).remove(raw) ?: return false
        if (builtIn != null) filters.removeRule(parsed)
        return true
    }

    private fun reload(context: Context) {
        val app = context.applicationContext
        loader.execute {
            val enabled = BrowserPreferences(app).appFlags and 64 != 0
            try {
                val store = FilterStore(app)
                val lists = store.readLists(enabled)
                custom(store).reload(lists.first())
                filters.replaceLists(lists)
            } catch (error: Exception) {
                Log.w("ViaFilters", "Cannot load filter lists", error)
            }
        }
    }
}
