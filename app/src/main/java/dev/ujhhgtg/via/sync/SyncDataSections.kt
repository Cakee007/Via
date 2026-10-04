package dev.ujhhgtg.via.sync

import android.content.Context
import android.util.Log
import dev.ujhhgtg.via.common.GeneratedDocumentState
import dev.ujhhgtg.via.data.BrowserPreferences
import org.json.JSONException
import dev.ujhhgtg.via.data.BookmarkCollection
import dev.ujhhgtg.via.data.BookmarkHtml
import dev.ujhhgtg.via.data.BookmarkRepository
import dev.ujhhgtg.via.data.BrowserDatabase
import dev.ujhhgtg.via.data.Favorite
import dev.ujhhgtg.via.data.FavoritesRepository
import dev.ujhhgtg.via.settings.SettingsRecordsCodec
import java.io.ByteArrayOutputStream
import org.json.JSONObject

/** m9.d/f/l and rb.a/b/c: section formats and merge/replace strategies. */
class SyncDataSections(context: Context, database: BrowserDatabase) {
    private val bookmarks = BookmarkRepository(database)
    private val favorites = FavoritesRepository(database)
    private val settings = SettingsRecordsCodec(context, database)

    init { GeneratedDocumentState.initialize(BrowserPreferences(context)) }

    fun export(name: String): ByteArray? = when (name) {
        BOOKMARKS -> if (bookmarks.listItems().isEmpty() && bookmarks.listFolders().isEmpty()) null else
            ByteArrayOutputStream().also { bookmarks.exportHtml(it) }.toByteArray()
        FAVORITES -> favorites.list().takeIf { it.isNotEmpty() }?.joinToString("\n", postfix = "\n") {
            JSONObject().put("title", it.title.orEmpty()).put("url", it.url).put("order", it.order).toString()
        }?.toByteArray(Charsets.UTF_8)
        SETTINGS -> settings.export(sync = true).toByteArray(Charsets.UTF_8)
        else -> throw IllegalArgumentException("Unknown sync section: $name")
    }

    fun importSection(name: String, bytes: ByteArray, strategy: Int, lastSynced: Long = 0) {
        when (name) {
            BOOKMARKS -> importBookmarks(BookmarkHtml.read(bytes.inputStream()), strategy, lastSynced)
            FAVORITES -> importFavorites(bytes, strategy)
            SETTINGS -> {
                val source = bytes.toString(Charsets.UTF_8)
                settings.importRecords(source, strategy)
                // m9.l.b marks p(true).q(true) only when it processed a nonempty
                // "settings" object; settingsData/siteConf/script alone do not set all bits.
                val importedSettings = source.lineSequence().any { line ->
                    runCatching {
                        val record = JSONObject(line)
                        record.keys().asSequence().firstOrNull() == "settings" &&
                            (record.optJSONObject("settings")?.length() ?: 0) > 0
                    }.getOrDefault(false)
                }
                if (importedSettings) GeneratedDocumentState.mark(GeneratedDocumentState.SETTINGS_IMPORT)
            }
            else -> throw IllegalArgumentException("Unknown sync section: $name")
        }
    }

    /** m9.f.b: parse the complete stream before changing the repository. */
    private fun importFavorites(bytes: ByteArray, strategy: Int) {
        val remote = mutableListOf<Favorite>()
        try {
            bytes.inputStream().bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    // An actual blank line aborts the entire import; a normal trailing
                    // newline is followed by EOF and does not produce a blank record.
                    if (line.isEmpty()) return
                    val json = JSONObject(line)
                    val title = json.getString("title")
                    val url = json.getString("url")
                    val order = json.getInt("order")
                    if (url.isNotEmpty()) remote += Favorite(url = url, title = title, order = order)
                }
            }
        } catch (error: JSONException) {
            // m9.f catches JSON errors itself. No partial prefix is written and
            // the enclosing rb.b operation does not turn this into a sync error.
            Log.w("ViaSync", "Cannot import favorites", error)
            return
        }
        if (remote.isNotEmpty()) favorites.importEntries(remote, shouldReplace(strategy, favorites.list().size, remote.size))
        // m9.f also invalidates an existing homepage after a successful empty file.
        GeneratedDocumentState.mark(GeneratedDocumentState.HOME_CONTENT)
    }

    private fun importBookmarks(remote: BookmarkCollection, strategy: Int, lastSynced: Long) {
        if (remote.items.isEmpty() && remote.folders.isEmpty()) return
        val local = bookmarks.listItems()
        val replace = shouldReplace(strategy, local.size, remote.items.size)
        val retained = if (strategy == 2 && replace && lastSynced != 0L) local.filter { it.lastUpdatedAt >= lastSynced / 1000 } else emptyList()
        val folderIds = linkedSetOf<String>()
        for (item in retained) {
            var id = item.folderId
            while (id.isNotEmpty() && folderIds.add(id)) id = bookmarks.findFolder(id)?.parentFolderId.orEmpty()
        }
        val localFolders = bookmarks.listFolders().filter { it.id in folderIds }
        bookmarks.importCollection(remote, replace)
        if (retained.isNotEmpty()) bookmarks.importCollection(BookmarkCollection(localFolders, retained))
        GeneratedDocumentState.mark(GeneratedDocumentState.BOOKMARKS)
    }

    companion object {
        const val BOOKMARKS = "bookmarks.html"
        const val FAVORITES = "favorites.txt"
        const val SETTINGS = "settings.txt"
        fun names(flags: Int): List<String> = buildList {
            if (flags and 16 != 0) add(BOOKMARKS)
            if (flags and 64 != 0) add(FAVORITES)
            if (flags and 32 != 0) add(SETTINGS)
        }
        fun shouldReplace(strategy: Int, localCount: Int, remoteCount: Int): Boolean =
            strategy == 1 || strategy == 2 && (remoteCount > localCount || localCount - remoteCount < localCount / 2)
    }
}
