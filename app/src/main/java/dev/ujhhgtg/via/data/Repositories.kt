package dev.ujhhgtg.via.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.InputStream
import java.io.OutputStream
import java.util.TimeZone
import java.util.UUID

@Suppress("UnusedReceiverParameter")
private fun SQLiteDatabase.inMarks(count: Int) = List(count) { "?" }.joinToString(",", "(", ")")

class BookmarkRepository(private val db: BrowserDatabase) {
    private val itemColumns = arrayOf("_id", "url", "title", "folder_id", "ordering", "last_updated_at", "created_at")
    private val folderColumns = arrayOf("_id", "title", "parent_folder_id", "ordering", "created_at", "last_updated_at")
    private fun Cursor.item() = BookmarkItem(getString(0), getString(1), getString(2), getString(3).orEmpty(), getInt(4), getLong(5), getLong(6))
    private fun Cursor.folder() = BookmarkFolder(getString(0), getString(1), getString(2).orEmpty(), getInt(3), getLong(4), getLong(5))

    fun listItems(folderId: String? = null, limit: Int = 0): List<BookmarkItem> = db.readableDatabase.query("bookmark_items", itemColumns, folderId?.let { "folder_id = ?" }, folderId?.let { arrayOf(it) }, null, null, "ordering", if (limit > 0) limit.toString() else null).use { c -> buildList { while (c.moveToNext()) add(c.item()) } }
    fun listFolders(parentFolderId: String? = null): List<BookmarkFolder> = db.readableDatabase.query("bookmark_folders", folderColumns, parentFolderId?.let { "parent_folder_id = ?" }, parentFolderId?.let { arrayOf(it) }, null, null, "ordering", null).use { c -> buildList { while (c.moveToNext()) add(c.folder()) } }
    fun findItem(id: String): BookmarkItem? = db.readableDatabase.query("bookmark_items", itemColumns, "_id = ?", arrayOf(id), null, null, null, "1").use { if (it.moveToFirst()) it.item() else null }
    fun findFolder(id: String): BookmarkFolder? = db.readableDatabase.query("bookmark_folders", folderColumns, "_id = ?", arrayOf(id), null, null, null, "1").use { if (it.moveToFirst()) it.folder() else null }
    fun findByUrl(url: String): BookmarkItem? {
        if (url.isEmpty() || url.length > 1048576) return null
        val variants = urlLookupVariants(url)
        return db.readableDatabase.query("bookmark_items", itemColumns, "url IN ${db.readableDatabase.inMarks(variants.size)}", variants, null, null, "ordering", "1").use { if (it.moveToFirst()) it.item() else null }
    }

    fun save(item: BookmarkItem): Boolean {
        if (item.id.isEmpty() || item.url.isEmpty() || item.url.length > 1048576) return false
        val now = System.currentTimeMillis() / 1000
        val v = ContentValues().apply {
            put("_id", item.id); put("url", item.url); put("title", item.title); put("folder_id", item.folderId); put("ordering", item.ordering)
            put("last_updated_at", item.lastUpdatedAt.takeIf { it != 0L } ?: now); put("created_at", item.createdAt.takeIf { it != 0L } ?: now)
        }
        return db.writableDatabase.insertWithOnConflict("bookmark_items", null, v, SQLiteDatabase.CONFLICT_REPLACE) != -1L
    }

    fun saveFolder(folder: BookmarkFolder): Boolean {
        if (folder.id.isEmpty()) return false
        val now = System.currentTimeMillis() / 1000
        val v = ContentValues().apply {
            put("_id", folder.id); put("title", folder.title); put("parent_folder_id", folder.parentFolderId); put("ordering", folder.ordering)
            put("created_at", folder.createdAt.takeIf { it != 0L } ?: now); put("last_updated_at", folder.lastUpdatedAt.takeIf { it != 0L } ?: now)
        }
        return db.writableDatabase.insertWithOnConflict("bookmark_folders", null, v, SQLiteDatabase.CONFLICT_REPLACE) != -1L
    }

    fun addFolder(title: String, parentFolderId: String = "", ordering: Int = nextFolderOrder(parentFolderId)): BookmarkFolder {
        val now = System.currentTimeMillis() / 1000
        return BookmarkFolder(UUID.randomUUID().toString(), title, parentFolderId, ordering, now, now).also { saveFolder(it) }
    }

    fun deleteItems(vararg ids: String): Int = if (ids.isEmpty()) 0 else db.writableDatabase.delete("bookmark_items", "_id IN ${db.writableDatabase.inMarks(ids.size)}", ids)
    fun deleteByUrl(url: String): Boolean = db.writableDatabase.delete("bookmark_items", "url = ?", arrayOf(url)) > 0

    /** Folder removal includes descendants and their bookmarks (o9/e.p). */
    fun deleteFolders(vararg ids: String): Int {
        val descendants = ids.flatMap { descendantFolderIds(it) }.distinct().filter(String::isNotEmpty)
        if (descendants.isEmpty()) return 0
        return db.writableDatabase.transaction {
            val args = descendants.toTypedArray()
            val where = db.writableDatabase.inMarks(args.size)
            db.writableDatabase.delete("bookmark_items", "folder_id IN $where", args)
            db.writableDatabase.delete("bookmark_folders", "_id IN $where", args)
        }
    }

    fun descendantFolderIds(folderId: String): List<String> {
        val remaining = listFolders().toMutableList()
        val result = mutableListOf(folderId)
        var index = 0
        while (index < result.size) {
            val parent = result[index++]
            val children = remaining.filter { it.parentFolderId == parent }
            result.addAll(children.map { it.id })
            remaining.removeAll(children.toSet())
        }
        return result
    }

    fun search(query: String, folderId: String? = null, limit: Int = 0): List<BookmarkItem> {
        val q = query.trim()
        if (q.isEmpty()) return listItems(folderId, limit)
        val (search, args) = searchSelection(q)
        val folders = folderId?.takeIf(String::isNotEmpty)?.let { descendantFolderIds(it).toTypedArray() }
        val where = if (folders == null) search else "folder_id IN ${db.readableDatabase.inMarks(folders.size)} AND ($search)"
        return db.readableDatabase.query("bookmark_items", itemColumns, where, if (folders == null) args else folders + args, null, null, "ordering", if (limit > 0) limit.toString() else null).use { c -> buildList { while (c.moveToNext()) add(c.item()) } }
    }

    fun searchFolders(query: String, parentFolderId: String = ""): List<BookmarkFolder> {
        val descendants = descendantFolderIds(parentFolderId).toSet()
        return listFolders().filter { it.parentFolderId in descendants && it.title.orEmpty().contains(query, true) }
    }

    fun moveItem(id: String, folderId: String): Boolean {
        val item = findItem(id) ?: return false
        return save(item.copy(folderId = folderId, ordering = nextItemOrder(folderId), lastUpdatedAt = System.currentTimeMillis() / 1000))
    }

    fun moveFolder(id: String, parentFolderId: String): Boolean {
        val folder = findFolder(id) ?: return false
        return parentFolderId !in descendantFolderIds(id) &&
                saveFolder(folder.copy(parentFolderId = parentFolderId, ordering = nextFolderOrder(parentFolderId), lastUpdatedAt = System.currentTimeMillis() / 1000))
    }

    fun reorderItems(ids: List<String>) = db.writableDatabase.transaction {
        ids.forEachIndexed { index, id -> db.writableDatabase.update("bookmark_items", ContentValues().apply { put("ordering", index) }, "_id = ?", arrayOf(id)) }
    }
    fun reorderFolders(ids: List<String>) = db.writableDatabase.transaction {
        ids.forEachIndexed { index, id -> db.writableDatabase.update("bookmark_folders", ContentValues().apply { put("ordering", index) }, "_id = ?", arrayOf(id)) }
    }
    fun childCounts(): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        listItems().forEach { counts[it.folderId] = (counts[it.folderId] ?: 0) + 1 }
        listFolders().forEach { counts[it.parentFolderId] = (counts[it.parentFolderId] ?: 0) + 1 }
        return counts
    }
    fun clear(): Int = db.writableDatabase.transaction {
        db.writableDatabase.delete("bookmark_items", null, null) + db.writableDatabase.delete("bookmark_folders", null, null)
    }
    fun exportHtml(output: OutputStream): Int = BookmarkHtml.write(output, BookmarkCollection(listFolders(), listItems()))
    fun importHtml(input: InputStream, replace: Boolean = false): Int = importCollection(BookmarkHtml.read(input), replace)

    /** Merge folders by title under the same parent, replacing bookmarks with matching URLs. */
    fun importCollection(bookmarks: BookmarkCollection, replace: Boolean = false): Int {
        if (bookmarks.folders.isEmpty() && bookmarks.items.isEmpty()) return 0
        return db.writableDatabase.transaction {
            if (replace) clear()
            val existing = listFolders().toMutableList()
            val remap = mutableMapOf("" to "")
            val remaining = bookmarks.folders.toMutableList()
            var count = 0
            while (remaining.isNotEmpty()) {
                val folder = remaining.firstOrNull { it.parentFolderId in remap } ?: break
                remaining.remove(folder)
                val parent = remap[folder.parentFolderId].orEmpty()
                val id = existing.firstOrNull { it.parentFolderId == parent && it.title == folder.title }?.id ?: folder.id
                remap[folder.id] = id
                val mapped = folder.copy(id = id, parentFolderId = parent)
                if (saveFolder(mapped)) count++
                existing.removeAll { it.id == id }
                existing.add(mapped)
            }
            bookmarks.items.forEach { item ->
                if (item.url.isNotEmpty() && item.url.length <= 1048576) {
                    deleteByUrl(item.url)
                    if (save(item.copy(folderId = remap[item.folderId] ?: item.folderId))) count++
                }
            }
            count
        }
    }
    private fun nextItemOrder(folderId: String) = (listItems(folderId).maxOfOrNull { it.ordering } ?: 0) + 1
    private fun nextFolderOrder(parent: String) = (listFolders(parent).maxOfOrNull { it.ordering } ?: 0) + 1
}

class HistoryRepository(private val db: BrowserDatabase) {
    private val columns = arrayOf("id", "url", "title", "updated_at")
    private fun Cursor.entry() = HistoryEntry(getInt(0), getString(1), getString(2), getLong(3))
    fun list(limit: Int = 0): List<HistoryEntry> = page(0, limit)
    fun page(offset: Int, limit: Int): List<HistoryEntry> = db.readableDatabase.query("history", columns, null, null, null, null, "updated_at DESC", if (limit > 0) "${offset.coerceAtLeast(0)},$limit" else null).use { c -> buildList { while (c.moveToNext()) add(c.entry()) } }
    fun record(url: String, title: String?): Boolean {
        return url.isNotBlank() && importEntries(listOf(HistoryEntry(0, url, title, System.currentTimeMillis() / 1000))) > 0
    }
    fun importEntries(entries: List<HistoryEntry>, replace: Boolean = false): Int = db.writableDatabase.transaction {
        if (replace && entries.isNotEmpty()) clear()
        var count = 0
        var timestamp = System.currentTimeMillis() / 1000 - entries.size
        entries.forEach { entry ->
            if (!entry.url.isNullOrEmpty()) {
                deleteByUrl(entry.url)
                val values = ContentValues().apply { put("url", entry.url); put("title", entry.title); put("updated_at", entry.updatedAt.takeIf { it != 0L } ?: timestamp) }
                if (db.writableDatabase.insert("history", null, values) > 0) count++
            }
            timestamp++
        }
        count
    }
    fun search(query: String, limit: Int = 0): List<HistoryEntry> {
        if (query.trim().isEmpty()) return list(limit)
        val (selection, args) = searchSelection(query.trim())
        return db.readableDatabase.query("history", columns, selection, args, null, null, "updated_at DESC", if (limit > 0) limit.toString() else null).use { c -> buildList { while (c.moveToNext()) add(c.entry()) } }
    }
    /** The deletion picker starts with the last hour, followed by local calendar days. */
    fun ranges(nowSeconds: Long = System.currentTimeMillis() / 1000): List<HistoryRange> {
        val result = mutableListOf<HistoryRange>()
        val hour = nowSeconds - 3600
        db.readableDatabase.rawQuery("SELECT count(id) FROM history WHERE updated_at >= ?", arrayOf(hour.toString())).use { if (it.moveToFirst()) result.add(HistoryRange(hour, it.getInt(0))) }
        val offset = TimeZone.getDefault().rawOffset / 1000
        db.readableDatabase.rawQuery("SELECT (updated_at + $offset) / 86400 AS range_id, count(id) FROM history GROUP BY range_id", null).use { while (it.moveToNext()) result.add(HistoryRange(it.getLong(0) * 86400 - offset, it.getInt(1))) }
        return result
    }
    fun deleteIds(ids: List<Int>): Int = if (ids.isEmpty()) 0 else db.writableDatabase.delete("history", "id IN ${db.writableDatabase.inMarks(ids.size)}", ids.map(Int::toString).toTypedArray())
    fun deleteByUrl(url: String) = db.writableDatabase.delete("history", "url = ?", arrayOf(url)) > 0
    fun clear() = db.writableDatabase.delete("history", null, null)
    fun clearSince(epochSeconds: Long) = db.writableDatabase.delete("history", "updated_at >= ?", arrayOf(epochSeconds.toString()))
}

class FavoritesRepository(private val db: BrowserDatabase) {
    fun list(): List<Favorite> = db.readableDatabase.query("favorites", null, null, null, null, null, "f_order").use { c -> buildList { while (c.moveToNext()) add(Favorite(c.getInt(0), c.getString(1), c.getString(2), c.getInt(3), c.getLong(4), c.getLong(5))) } }
    fun findByUrl(url: String): Favorite? {
        if (url.trim().isEmpty()) return null
        val variants = urlLookupVariants(url.trim())
        return db.readableDatabase.query("favorites", null, "f_url IN ${db.readableDatabase.inMarks(variants.size)}", variants, null, null, null, "1").use { c -> if (!c.moveToFirst()) null else Favorite(c.getInt(0), c.getString(1), c.getString(2), c.getInt(3), c.getLong(4), c.getLong(5)) }
    }

    /** fa.b.s (smali): an edit preserves created_at, updates updated_at, and never becomes an insert. */
    fun save(favorite: Favorite): Int {
        if (favorite.url.isEmpty()) return -1
        return try {
            db.writableDatabase.transaction {
                val database = db.writableDatabase
                val inserting = favorite.id <= 0
                if (inserting) database.delete("favorites", "f_url = ?", arrayOf(favorite.url))
                var order = favorite.order
                if (inserting && order == -1) database.rawQuery("select max(f_order) from favorites", null).use {
                    if (it.moveToNext()) order = it.getInt(0) + 1
                }
                val now = System.currentTimeMillis() / 1000
                val values = ContentValues().apply {
                    put("f_url", favorite.url); put("f_title", favorite.title); put("f_order", order)
                    put("updated_at", now)
                }
                if (inserting) {
                    values.put("created_at", now)
                    database.insert("favorites", null, values).toInt()
                } else if (database.update("favorites", values, "f_id = ?", arrayOf(favorite.id.toString())) > 0) favorite.id else -1
            }
        } catch (error: Exception) {
            android.util.Log.w("ViaFavorites", "Cannot save favorite", error)
            -1
        }
    }

    fun deleteByUrl(url: String): Boolean {
        if (url.trim().isEmpty()) return false
        val variants = urlLookupVariants(url.trim())
        return db.writableDatabase.delete("favorites", "f_url IN ${db.writableDatabase.inMarks(variants.size)}", variants) > 0
    }
    fun deleteById(id: Int) = db.writableDatabase.delete("favorites", "f_id = ?", arrayOf(id.toString())) > 0
    fun clear() = db.writableDatabase.delete("favorites", null, null)
    fun search(query: String, limit: Int = 0): List<Favorite> {
        if (query.trim().isEmpty()) return list().let { if (limit > 0) it.take(limit) else it }
        val (selection, args) = searchSelection(query.trim(), "f_title", "f_url")
        return db.readableDatabase.query("favorites", null, selection, args, null, null, "f_id DESC", if (limit > 0) limit.toString() else null).use { c -> buildList { while (c.moveToNext()) add(Favorite(c.getInt(0), c.getString(1), c.getString(2), c.getInt(3), c.getLong(4), c.getLong(5))) } }
    }
    fun reorder(ids: List<Int>) = db.writableDatabase.transaction {
        ids.forEachIndexed { index, id -> db.writableDatabase.update("favorites", ContentValues().apply { put("f_order", index) }, "f_id = ?", arrayOf(id.toString())) }
    }
    fun importEntries(entries: List<Favorite>, replace: Boolean = false): Int = db.writableDatabase.transaction {
        if (replace && entries.isNotEmpty()) clear()
        var count = 0
        entries.forEach { entry ->
            if (entry.url.isNotEmpty()) {
                db.writableDatabase.delete("favorites", "f_url = ?", arrayOf(entry.url))
                val values = ContentValues().apply { put("f_url", entry.url); put("f_title", entry.title); put("f_order", entry.order) }
                if (db.writableDatabase.insert("favorites", null, values) > 0) count++
            }
        }
        count
    }

}

class SiteConfigurationRepository(private val db: BrowserDatabase) {
    /** ca/b looks up the exact domain key; temporary overrides are not subdomain inheritance. */
    fun get(domain: String, persistentOnly: Boolean = false): SiteConfiguration? {
        val saved = db.readableDatabase.query("conf", arrayOf("domain", "data"), "domain = ?", arrayOf(domain), null, null, null, "1").use { c -> if (!c.moveToFirst()) null else SiteConfiguration(c.getString(0), c.getString(1)) }
        if (persistentOnly) return saved
        val temporary = overrides[domain] ?: return saved
        if (temporary.expiresAt > 0 && System.currentTimeMillis() > temporary.expiresAt) { overrides.remove(domain); return saved }
        return when (temporary.mode) { 0 -> temporary.configuration; 1 -> merge(saved, temporary.configuration); else -> saved }
    }
    fun all(): List<SiteConfiguration> = db.readableDatabase.query("conf", arrayOf("domain", "data"), null, null, null, null, null).use { c -> buildList { while (c.moveToNext()) add(SiteConfiguration(c.getString(0), c.getString(1))) } }
    fun put(config: SiteConfiguration): Boolean { val d = db.writableDatabase; d.delete("conf", "domain = ?", arrayOf(config.domain)); return d.insert("conf", null, ContentValues().apply { put("domain", config.domain); put("data", config.data) }) != -1L }
    fun remove(domain: String) = db.writableDatabase.delete("conf", "domain = ?", arrayOf(domain)) > 0
    fun rename(domain: String, newDomain: String): Boolean {
        return !(domain.isEmpty() || newDomain.isEmpty() || domain == newDomain || get(newDomain, true) != null) && db.writableDatabase.update("conf", ContentValues().apply { put("domain", newDomain) }, "domain = ?", arrayOf(domain)) > 0
    }
    fun setTemporary(configuration: SiteConfiguration, expiresAt: Long = 0, mode: Int = 0) {
        if (configuration.domain.isNotEmpty() && (expiresAt <= 0 || System.currentTimeMillis() <= expiresAt)) overrides[configuration.domain] = Temporary(configuration, expiresAt, mode)
    }
    fun clearTemporary(domain: String) { overrides.remove(domain) }

    companion object {
        private data class Temporary(val configuration: SiteConfiguration, val expiresAt: Long, val mode: Int)
        private val overrides = mutableMapOf<String, Temporary>()
        /** ba/c.a merges this exact subset of older fields, with persistent overrides first. */
        fun merge(primary: SiteConfiguration?, fallback: SiteConfiguration?): SiteConfiguration? {
            if (primary?.isEnabled != true) return fallback
            if (fallback?.isEnabled != true) return primary
            var result: SiteConfiguration = primary
            if (primary.userAgentChoice == -1000) result = result.withUserAgent(fallback.userAgentChoice, fallback.customUserAgent)
            for (bit in listOf(2, 4, 8, 16)) if (!result.overrides(bit) && fallback.overrides(bit)) result = result.withBoolean(bit, fallback.booleanOverride(bit))
            // The original ba/c.a calls R (desktop), not Y, for this incognito fallback; confirmed in smali.
            if (!result.overrides(32) && fallback.overrides(32)) result = result.withBoolean(8, fallback.booleanOverride(32))
            if (result.clipboardMode == 0 && fallback.clipboardMode != 0) result = result.withPermission(64, 128, fallback.clipboardMode)
            if (primary.textZoomOverride == 0) result = result.withTextZoom(fallback.textZoomOverride)
            return result
        }
    }
}

class SessionRepository(private val db: BrowserDatabase) {
    fun list(): List<SessionTab> = db.readableDatabase.query("tabs", null, null, null, null, null, null).use { c -> buildList { while (c.moveToNext()) add(SessionTab(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4), c.getLong(5))) } }
    fun listOpen(includeIncognito: Boolean = false): List<SessionTab> = list().filter { it.isOpen && (includeIncognito || !it.isIncognito) }
    fun listClosed(): List<SessionTab> = list().filter { !it.isOpen && !it.isIncognito }.sortedByDescending { it.lastVisitedAt }
    fun clearClosed(): Int = db.writableDatabase.delete("tabs", "(flags & 2) = 0", null)
    fun markClosed(id: String): Boolean {
        val tab = find(id) ?: return false
        if (tab.isIncognito) return delete(id)
        return db.writableDatabase.transaction {
            db.writableDatabase.delete("tabs", "url = ? AND (flags & 2) = 0", arrayOf(tab.url.orEmpty()))
            save(tab.copy(flags = tab.flags and 7.inv(), lastVisitedAt = System.currentTimeMillis()))
        }
    }
    /** Persist the live set while retaining the original's separately addressable closed tabs. */
    fun replaceOpen(tabs: List<SessionTab>): Boolean = db.writableDatabase.transaction {
        val incoming = tabs.map { it.id }.toSet()
        listOpen(true).filter { it.id !in incoming }.forEach { markClosed(it.id) }
        // c8.ua.N1 stores private open tabs too; restoration decides whether to include bit 1.
        tabs.forEach { save(it.copy(flags = it.flags or 2)) }
        true
    }
    fun importClosed(tabs: List<SessionTab>): Int = db.writableDatabase.transaction {
        val urls = list().mapNotNull { it.url }.toMutableSet()
        var count = 0
        tabs.forEach { tab -> if (!tab.url.isNullOrEmpty() && urls.add(tab.url) && save(tab.copy(flags = 0))) count++ }
        count
    }

    fun find(id: String): SessionTab? = db.readableDatabase.query("tabs", null, "_id = ?", arrayOf(id), null, null, null, "1").use { c -> if (!c.moveToFirst()) null else SessionTab(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4), c.getLong(5)) }
    fun save(tab: SessionTab): Boolean { val d = db.writableDatabase; d.delete("tabs", "_id = ?", arrayOf(tab.id)); return d.insert("tabs", null, ContentValues().apply { put("_id", tab.id); put("url", tab.url); put("title", tab.title); put("file_path", tab.filePath); put("flags", tab.flags); put("last_visited_at", tab.lastVisitedAt) }) != -1L }
    fun delete(id: String) = db.writableDatabase.delete("tabs", "_id = ?", arrayOf(id)) > 0
}
