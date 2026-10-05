package dev.ujhhgtg.via.browser

import androidx.core.net.toUri
import dev.ujhhgtg.via.common.GeneratedDocumentState

import android.content.Context
import android.net.Uri
import android.util.Log
import dev.ujhhgtg.via.browser.script.ScriptResources
import dev.ujhhgtg.via.browser.script.ScriptStore
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.BrowserPreferences
import dev.ujhhgtg.via.data.FavoritesRepository
import dev.ujhhgtg.via.data.HistoryRepository
import dev.ujhhgtg.via.data.SessionRepository
import dev.ujhhgtg.via.home.HomeDesign
import dev.ujhhgtg.via.home.HomeIcons
import dev.ujhhgtg.via.common.applicationIoScope
import kotlinx.coroutines.launch
import java.io.File

/** z8.h0/b0/o0: exact browsing-data categories and the original startup cleanup policy. */
class BrowserDataCleaner(context: Context, private val database: BrowserDatabase = BrowserDatabase.shared(context)) {
    private val context = context.applicationContext
    private val preferences = BrowserPreferences(context)
    private val engine = dev.ujhhgtg.via.engine.Engines.backend

    /**
     * c8.s6.V1 -> W8 -> ua.r0 -> h0.c. Called on the UI thread as the browser view is
     * created, before its update pass. Despite the setting's label, the APK does not
     * call this from finish/onDestroy, and never waits for the worker jobs to finish.
     */
    fun clearOnBrowserCreated(now: Long = System.currentTimeMillis()) {
        var mask = preferences.clearDataOnExit
        if (now - preferences.getLong("lastcleantime", now) >= CACHE_CLEAN_INTERVAL) mask = mask or CACHE
        clear(mask, manual = false, now = now)
    }

    /** h0.a: persists the selection and returns whether the original completion toast is due. */
    fun clearManual(mask: Int, onHistoryChanged: () -> Unit = {}): Boolean {
        clear(mask, manual = true, now = System.currentTimeMillis())
        if (mask and HISTORY != 0) {
            GeneratedDocumentState.mark(GeneratedDocumentState.HISTORY)
            onHistoryChanged() // w9.n.u(true), manual clearing only.
        }
        preferences.clearData = mask
        return mask and ALL != 0
    }

    private fun clear(mask: Int, manual: Boolean, now: Long) {
        if (mask and CACHE != 0) {
            engine.clearCache(context)
            preferences.putLong("lastcleantime", now)
            // h0.c receives a null monkey repository at startup; h0.a receives it from both manual entry points.
            if (manual) ScriptStore(context).use { ScriptResources(context).cleanup(it.list()) }
        }
        if (mask and FORM_DATA != 0) engine.clearFormData(context)
        if (mask and HISTORY != 0) background {
            HistoryRepository(database).clear()
            externalDirectory("favicons").deleteRecursively()
            externalDirectory("opened").deleteRecursively()
            HomeIcons.clearMemory()
        }
        if (mask and CLOSED_TABS != 0) background { clearClosedTabs() }
        if (mask and STORAGE != 0) engine.clearStorage(context)
        if (mask and COOKIES != 0) background { engine.clearCookies(context) }
        if (mask and APP_CACHE != 0) {
            background {
                listOfNotNull(context.cacheDir, context.externalCacheDir, context.codeCacheDir,
                    File(context.filesDir, "chunks"), context.getExternalFilesDir("chunks")).forEach { it.deleteRecursively() }
                HomeIcons.clearMemory()
                clearUnusedContent()
            }
            retainFavoriteIcons()
        }
    }

    /** na.a.h/l removes closed tab rows, then files not referenced by any remaining tab. */
    private fun clearClosedTabs() {
        val sessions = SessionRepository(database)
        if (sessions.clearClosed() == 0) return
        val keep = sessions.list().mapNotNull { it.filePath?.takeIf(String::isNotEmpty)?.let { path -> File(path).name } }.toSet()
        externalDirectory("tabs").listFiles()?.filterNot { it.name in keep }?.forEach { it.delete() }
    }

    /** z8.o0.b protects the chosen local homepage, background and image in the custom logo markup. */
    private fun clearUnusedContent() {
        val files = externalDirectory("content").listFiles() ?: return
        val keep = mutableSetOf<String?>()
        val home = preferences.home
        if (home.startsWith("file://", true)) {
            val uri = home.toUri()
            if (uri.path != null) keep += uri.lastPathSegment
        }
        val background = preferences.backgroundHome
        val hasBackground = !background.isNullOrEmpty()
        if (!background.isNullOrEmpty()) keep += File(background).name
        val logo = preferences.homeTag
        if (!logo.isNullOrEmpty()) {
            val image = logo.indexOf("<img")
            val src = if (image >= 0) logo.indexOf(" src=\"", image) else -1
            val end = if (src > 0) logo.indexOf('"', src + 6) else -1
            if (end > src && src > 0) {
                // Preserve the literal substring bounds in o0.b (also verified in smali).
                val uri = logo.substring(src, end).toUri()
                if (uri.path != null) keep += uri.lastPathSegment
            }
        }
        files.filterNot { it.name in keep || hasBackground && it.name.startsWith("background-") }.forEach { it.deleteRecursively() }
    }

    /** h0.d/w3.f retains favorite icon files; an empty favorites list leaves this directory alone. */
    private fun retainFavoriteIcons() {
        val favorites = FavoritesRepository(database).list()
        if (favorites.isEmpty()) return
        val keep = favorites.map { HomeDesign.iconName(it.url) + ".png" }.toSet()
        File(context.filesDir, "icon").listFiles()?.filter { it.isFile && it.name !in keep }?.forEach { it.delete() }
    }

    private fun externalDirectory(name: String) = context.getExternalFilesDir(name) ?: File(context.filesDir, name)

    /** z8.h.b uses the IO scheduler and reports worker failures without blocking the caller. */
    private fun background(work: () -> Unit) {
        applicationIoScope.launch { runCatching(work).onFailure { Log.w("ViaDataCleaner", "Cannot clear browsing data", it) } }
    }

    companion object {
        const val CACHE = 1
        const val FORM_DATA = 2
        const val HISTORY = 4
        const val STORAGE = 8
        const val COOKIES = 16
        const val APP_CACHE = 32
        const val CLOSED_TABS = 64
        private const val ALL = 127
        private const val CACHE_CLEAN_INTERVAL = 432_000_000L
    }
}
