package dev.ujhhgtg.via.data

import android.content.ContentValues
import android.database.Cursor
import org.json.JSONObject

/** Custom user agents (type 1) and search providers (type 2); timestamps are milliseconds. */
data class SettingsData(
    val id: Int = 0,
    val title: String? = null,
    val content: String? = null,
    val note: String? = null,
    val flag: Int = 0,
    val type: Int = 0,
    val lastUpdatedAt: Long = 0,
    val createdAt: Long = 0,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("title", title).put("content", content)
        .put("note", note).put("flag", flag).put("type", type)
        .put("lastUpdatedAt", lastUpdatedAt).put("createdAt", createdAt)

    companion object {
        const val USER_AGENT = 1
        const val SEARCH_ENGINE = 2
        fun fromJson(json: JSONObject): SettingsData = SettingsData(
            json.optInt("id"), json.optString("title").ifEmpty { null }, json.optString("content").ifEmpty { null },
            json.optString("note").ifEmpty { null }, json.optInt("flag"), json.optInt("type"),
            json.optLong("lastUpdatedAt"), json.optLong("createdAt"),
        )
    }
}

/** ja/a.java; import deduplication is confirmed by decoded/smali/ja/a.smali method g. */
class SettingsDataRepository(private val database: BrowserDatabase) {
    private val columns = arrayOf("id", "title", "content", "note", "flag", "type", "updated_at", "created_at")
    private fun Cursor.item() = SettingsData(getInt(0), getString(1), getString(2), getString(3), getInt(4), getInt(5), getLong(6), getLong(7))

    fun list(type: Int? = null): List<SettingsData> = database.readableDatabase.query(
        "settings", columns, type?.let { "type = ?" }, type?.let { arrayOf(it.toString()) }, null, null, null,
    ).use { c -> buildList { while (c.moveToNext()) add(c.item()) } }

    fun find(id: Int): SettingsData? = database.readableDatabase.query(
        "settings", columns, "id = ?", arrayOf(id.toString()), null, null, null, "1",
    ).use { if (it.moveToFirst()) it.item() else null }

    fun findByContent(content: String?, type: Int): SettingsData? {
        if (content.isNullOrEmpty()) return null
        return database.readableDatabase.query(
            "settings", columns, "type = ? AND content = ?", arrayOf(type.toString(), content), null, null, null, "1",
        ).use { if (it.moveToFirst()) it.item() else null }
    }

    fun save(item: SettingsData): Int {
        val now = System.currentTimeMillis()
        val values = values(item.copy(
            lastUpdatedAt = item.lastUpdatedAt.takeIf { it != 0L } ?: now,
            createdAt = item.createdAt.takeIf { it != 0L } ?: now,
        ))
        if (item.id > 0) return if (database.writableDatabase.update("settings", values, "id = ?", arrayOf(item.id.toString())) > 0) item.id else 0
        return database.writableDatabase.insert("settings", null, values).toInt()
    }

    fun delete(id: Int): Boolean = database.writableDatabase.delete("settings", "id = ?", arrayOf(id.toString())) > 0

    fun importEntries(items: List<SettingsData>): Int = database.writableDatabase.transaction {
        var count = 0
        for (item in items) {
            if (item.type == SettingsData.USER_AGENT || item.type == SettingsData.SEARCH_ENGINE) {
                if (findByContent(item.content, item.type) != null) continue
                database.writableDatabase.delete("settings", "content = ?", arrayOf(item.content.orEmpty()))
            }
            if (database.writableDatabase.insert("settings", null, values(item.copy(id = 0))) > 0) count++
        }
        count
    }

    private fun values(item: SettingsData) = ContentValues().apply {
        put("title", item.title); put("content", item.content); put("note", item.note)
        put("flag", item.flag); put("type", item.type); put("updated_at", item.lastUpdatedAt); put("created_at", item.createdAt)
    }
}
