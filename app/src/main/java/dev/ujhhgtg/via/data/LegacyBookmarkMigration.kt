package dev.ujhhgtg.via.data

import android.content.ContentValues
import android.util.Log
import java.util.UUID

/** c8.ua.p0/E/g, z8.p.e/p and o9.g.x: the data_version<2 legacy-bookmark conversion. */
object LegacyBookmarkMigration {
    private data class Legacy(val url: String, val title: String, val folder: String, val order: Int,
        val updatedAt: Long, val createdAt: Long)

    /** Run on the existing startup IO worker. Schema and the old bookmarks table are left intact. */
    fun migrate(database: BrowserDatabase, preferences: BrowserPreferences): Int {
        if (preferences.dataVersion >= 2) return 0
        return try {
            val legacy = database.readableDatabase.query("bookmarks", arrayOf("id", "url", "title", "folder", "clickTimes", "updated_at", "created_at"),
                null, null, null, null, null).use { cursor ->
                buildList { while (cursor.moveToNext()) add(Legacy(cursor.getString(1).orEmpty(), cursor.getString(2).orEmpty(),
                    cursor.getString(3).orEmpty(), cursor.getInt(4), cursor.getLong(5), cursor.getLong(6))) }
            }
            if (legacy.isEmpty()) {
                preferences.dataVersion = 2
                return 1 // ua.E returns 1 for an empty old table so ua.g still records completion.
            }
            val folders = mutableListOf<BookmarkFolder>()
            val items = mutableListOf<BookmarkItem>()
            val paths = hashMapOf("" to "")
            var folderOrder = 0
            var timestamp = System.currentTimeMillis() / 1000 - legacy.size
            for (entry in legacy) {
                if (entry.url.isEmpty() || entry.url.length > 0x100000) continue
                val created = if (entry.createdAt == 0L) ++timestamp else entry.createdAt
                if (entry.folder !in paths) {
                    var start = 0
                    while (start < entry.folder.length) {
                        var slash = entry.folder.indexOf('/', start)
                        while (slash > 0 && entry.folder[slash - 1] == '\\') slash = entry.folder.indexOf('/', slash + 1)
                        val end = if (slash == -1) entry.folder.length else slash
                        val path = entry.folder.substring(0, end)
                        if (path !in paths) {
                            val parent = paths[entry.folder.substring(0, maxOf(0, start - 1))].orEmpty()
                            val title = entry.folder.substring(start, end).replace("\\/", "/")
                            val now = System.currentTimeMillis() / 1000
                            val folder = BookmarkFolder(UUID.randomUUID().toString(), title, parent, ++folderOrder, now, now)
                            folders += folder
                            paths[path] = folder.id
                        }
                        start = end + 1
                    }
                }
                items += BookmarkItem(UUID.randomUUID().toString(), entry.url, normalizeTitle(entry.title), paths[entry.folder].orEmpty(),
                    entry.order, entry.updatedAt.takeIf { it != 0L } ?: created, created)
            }
            mergeFolderIds(folders, items, BookmarkRepository(database).listFolders(), "")
            val count = importConverted(database, folders, items)
            if (count > 0) preferences.dataVersion = 2
            count
        } catch (error: Exception) {
            Log.w("ViaBookmarks", "Cannot migrate legacy bookmarks", error)
            0
        }
    }

    /** z8.p.p recursively maps equally named folders under the same parent to existing IDs. */
    private fun mergeFolderIds(folders: MutableList<BookmarkFolder>, items: MutableList<BookmarkItem>, existing: List<BookmarkFolder>, parent: String) {
        val names = mutableMapOf<String, String>()
        existing.filter { it.parentFolderId == parent }.forEach { folder -> folder.title?.let { names[it] = folder.id } }
        if (names.isEmpty()) return
        val mapping = mutableMapOf<String, String>()
        folders.filter { it.parentFolderId == parent }.forEach { folder -> folder.title?.let(names::get)?.let { mapping[folder.id] = it } }
        if (mapping.isEmpty()) return
        items.replaceAll { item -> mapping[item.folderId]?.let { item.copy(folderId = it) } ?: item }
        val changed = mutableListOf<String>()
        for (index in folders.indices) {
            val folder = folders[index]
            val id = mapping[folder.id]
            val parentId = mapping[folder.parentFolderId]
            if (id == null && parentId == null) continue
            val next = folder.copy(id = id ?: folder.id, parentFolderId = parentId ?: folder.parentFolderId)
            folders[index] = next
            changed += next.id
        }
        changed.forEach { mergeFolderIds(folders, items, existing, it) }
    }

    private fun importConverted(database: BrowserDatabase, folders: List<BookmarkFolder>, items: List<BookmarkItem>): Int {
        if (folders.isEmpty() && items.isEmpty()) return 0
        val db = database.writableDatabase
        return db.transaction {
            var count = 0
            folders.forEach { folder ->
                db.delete("bookmark_folders", "_id = ?", arrayOf(folder.id))
                db.insert("bookmark_folders", null, ContentValues().apply {
                    put("_id", folder.id); put("title", folder.title); put("parent_folder_id", folder.parentFolderId)
                    put("ordering", folder.ordering); put("last_updated_at", folder.lastUpdatedAt); put("created_at", folder.createdAt)
                })
                count++
            }
            items.forEach { item ->
                db.delete("bookmark_items", "url = ?", arrayOf(item.url))
                db.insert("bookmark_items", null, ContentValues().apply {
                    put("_id", item.id); put("url", item.url); put("title", item.title); put("folder_id", item.folderId)
                    put("ordering", item.ordering); put("last_updated_at", item.lastUpdatedAt); put("created_at", item.createdAt)
                })
                count++
            }
            count
        }
    }

    /** g6.p.l maps whitespace to spaces but drops carriage returns. */
    private fun normalizeTitle(title: String): String = buildString {
        title.forEach { character ->
            when {
                Character.isWhitespace(character) && character != '\r' -> append(' ')
                Character.isWhitespace(character) -> Unit
                character == '\u00a0' -> append(' ')
                else -> append(character)
            }
        }
    }
}
