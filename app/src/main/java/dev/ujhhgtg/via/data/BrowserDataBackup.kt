package dev.ujhhgtg.via.data

import dev.ujhhgtg.via.common.GeneratedDocumentState

import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupResult(
    val bookmarks: Int = 0,
    val history: Int = 0,
    val favorites: Int = 0,
    val tabs: Int = 0,
    val entries: Int = 0,
)

/** The original backup's ZIP entries; settings/assets are supplied by their owning subsystem. */
class BrowserDataBackup(database: BrowserDatabase) {
    private val bookmarks = BookmarkRepository(database)
    private val history = HistoryRepository(database)
    private val favorites = FavoritesRepository(database)
    private val sessions = SessionRepository(database)

    fun exportZip(output: OutputStream, extraEntries: Map<String, ByteArray> = emptyMap(), sections: Int = 31): BackupResult {
        val entries = linkedMapOf<String, ByteArray>()
        val bookmarkItems = if (sections and 2 != 0) bookmarks.listItems() else emptyList()
        val bookmarkFolders = if (sections and 2 != 0) bookmarks.listFolders() else emptyList()
        if (bookmarkItems.isNotEmpty() || bookmarkFolders.isNotEmpty()) {
            val html = ByteArrayOutputStream()
            BookmarkHtml.write(html, BookmarkCollection(bookmarkFolders, bookmarkItems))
            entries["bookmarks.html"] = html.toByteArray()
        }
        val historyItems = if (sections and 8 != 0) history.list() else emptyList()
        if (historyItems.isNotEmpty()) entries["history.txt"] = jsonLines(historyItems.map {
            JSONObject().put("title", it.title).put("url", it.url).put("updated_at", it.updatedAt)
        })
        val favoriteItems = if (sections and 4 != 0) favorites.list() else emptyList()
        if (favoriteItems.isNotEmpty()) entries["favorites.txt"] = jsonLines(favoriteItems.map {
            JSONObject().put("title", it.title).put("url", it.url).put("order", it.order)
        })
        val closedTabs = if (sections and 16 != 0) sessions.listClosed() else emptyList()
        if (closedTabs.isNotEmpty()) entries["tabs.txt"] = jsonLines(closedTabs.map {
            JSONObject().put("title", it.title).put("url", it.url).put("updated_at", it.lastVisitedAt)
        })
        require(extraEntries.keys.none { it in entries }) { "Duplicate backup entry" }
        entries.putAll(extraEntries)
        val zip = ZipOutputStream(output)
        entries.forEach { (name, content) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(content)
            zip.closeEntry()
        }
        zip.finish()
        zip.flush()
        return BackupResult(bookmarkItems.size, historyItems.size, favoriteItems.size, closedTabs.size, entries.size)
    }

    fun importZip(
        input: InputStream,
        replace: Boolean = false,
        onEntry: (String, ByteArray) -> Unit = { _, _ -> },
    ): BackupResult {
        val zip = ZipInputStream(input)
        var result = BackupResult()
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory) {
                // Read entries directly. External archive paths are never used as filesystem paths.
                val content = zip.readBytes()
                result = when (entry.name) {
                    "bookmarks.html" -> result.copy(bookmarks = bookmarks.importHtml(content.inputStream(), replace))
                    "history.txt" -> {
                        val items = readJsonLines(content).mapNotNull { json ->
                            val url = json.optString("url")
                            if (url.isEmpty()) null else HistoryEntry(0, url, json.optString("title"), json.optLong("updated_at"))
                        }
                        result.copy(history = history.importEntries(items, replace))
                    }
                    "favorites.txt" -> {
                        val items = readJsonLines(content).mapNotNull { json ->
                            val url = json.optString("url")
                            if (url.isEmpty()) null else Favorite(url = url, title = json.optString("title"), order = json.optInt("order"))
                        }
                        result.copy(favorites = favorites.importEntries(items, replace))
                    }
                    "tabs.txt" -> {
                        val tabs = readJsonLines(content).mapNotNull { json ->
                            val url = json.optString("url")
                            if (url.isEmpty()) null else SessionTab(UUID.randomUUID().toString(), url, json.optString("title"), null, 0, json.optLong("updated_at"))
                        }
                        result.copy(tabs = sessions.importClosed(tabs))
                    }
                    else -> { onEntry(entry.name, content); result }
                }
                result = result.copy(entries = result.entries + 1)
            }
            zip.closeEntry()
        }
        require(result.entries > 0) { "No backup entries found" }
        if (result.bookmarks > 0) GeneratedDocumentState.mark(GeneratedDocumentState.BOOKMARKS)
        if (result.history > 0) GeneratedDocumentState.mark(GeneratedDocumentState.HISTORY)
        return result
    }

    private fun jsonLines(items: List<JSONObject>): ByteArray = items.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8)
    private fun readJsonLines(bytes: ByteArray): List<JSONObject> = bytes.inputStream().bufferedReader(Charsets.UTF_8).useLines { lines ->
        lines.filter(String::isNotEmpty).mapNotNull { line ->
            try { JSONObject(line) } catch (_: JSONException) { null }
        }.toList()
    }
}
