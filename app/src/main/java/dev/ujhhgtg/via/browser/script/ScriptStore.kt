package dev.ujhhgtg.via.browser.script

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

/** Persistent userscript store matching Via's `monkey` database schema (version 3). */
class ScriptStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "monkey", null, 3) {
    internal val context = context.applicationContext
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE scripts (_id INTEGER PRIMARY KEY, script_id TEXT NOT NULL, name TEXT, version TEXT, content TEXT NOT NULL, matches TEXT, exclude_matches TEXT, includes TEXT, excludes TEXT, run_at INTEGER DEFAULT 0, requires TEXT, resources TEXT, enabled INTEGER DEFAULT 0, flags INTEGER DEFAULT 0, grant INTEGER DEFAULT 0, icon_url TEXT, homepage_url TEXT, support_url TEXT, download_url TEXT, user_overrides TEXT, last_updated_at INTEGER DEFAULT 0, created_at INTEGER DEFAULT 0)")
        db.execSQL("CREATE TABLE variables (_id INTEGER PRIMARY KEY, script_id TEXT NOT NULL, name TEXT NOT NULL, value TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) { runCatching { db.execSQL("ALTER TABLE scripts ADD COLUMN grant INTEGER DEFAULT 268435455") } }
        if (oldVersion < 3) { runCatching { db.execSQL("ALTER TABLE scripts ADD COLUMN user_overrides TEXT") } }
    }

    fun list(): List<UserScript> = readableDatabase.query("scripts", null, null, null, null, null, "created_at DESC").use { cursor ->
        buildList { while (cursor.moveToNext()) add(read(cursor)) }
    }

    fun find(id: Int): UserScript? = readableDatabase.query("scripts", null, "_id = ?", arrayOf(id.toString()), null, null, null, "1").use { if (it.moveToFirst()) read(it) else null }
    fun findByScriptId(scriptId: String): UserScript? = readableDatabase.query("scripts", null, "script_id = ?", arrayOf(scriptId), null, null, null, "1").use { if (it.moveToFirst()) read(it) else null }

    /** p5.b.i matches either the stable script ID or its download URL on updates. */
    fun findByIdentity(scriptId: String, downloadUrl: String?): UserScript? {
        if (downloadUrl.isNullOrEmpty()) return findByScriptId(scriptId)
        return readableDatabase.query("scripts", null, "script_id = ? OR download_url = ?", arrayOf(scriptId, downloadUrl), null, null, null, "1")
            .use { if (it.moveToFirst()) read(it) else null }
    }

    fun save(script: UserScript): Int {
        val now = System.currentTimeMillis()
        val values = values(script, now)
        val existing = if (script.id > 0) find(script.id) else findByIdentity(script.scriptId, script.downloadUrl)
        if (existing != null) {
            values.remove("created_at")
            if (script.userOverrides == null) values.remove("user_overrides")
            writableDatabase.update("scripts", values, "_id = ?", arrayOf(existing.id.toString()))
            return existing.id
        }
        return writableDatabase.insertOrThrow("scripts", null, values).toInt()
    }

    /** p5.b.h: explicit insertion after the caller has applied its own identity lookup. */
    fun insert(script: UserScript): Int = writableDatabase.insertOrThrow("scripts", null,
        values(script, System.currentTimeMillis())).toInt()

    fun setEnabled(id: Int, enabled: Boolean): Boolean = writableDatabase.update("scripts", ContentValues().apply { put("enabled", if (enabled) 1 else 0) }, "_id = ?", arrayOf(id.toString())) > 0
    fun remove(id: Int): Boolean = writableDatabase.delete("scripts", "_id = ?", arrayOf(id.toString())) > 0

    fun setUserOverrides(id: Int, overrides: String?): Boolean = writableDatabase.update("scripts", ContentValues().apply {
        put("user_overrides", overrides)
    }, "_id = ?", arrayOf(id.toString())) > 0

    // p5.b stores serialized GM values as strings, scoped by script_id and name.
    fun getValue(scriptId: String, name: String): String? = if (scriptId.isEmpty() || name.isEmpty()) null else readableDatabase.query("variables", arrayOf("value"),
        "script_id = ? AND name = ?", arrayOf(scriptId, name), null, null, null, "1").use { if (it.moveToFirst()) it.getString(0) else null }
    fun listValues(scriptId: String): List<String> = if (scriptId.isEmpty()) emptyList() else readableDatabase.query("variables", arrayOf("name"),
        "script_id = ?", arrayOf(scriptId), null, null, null).use { c -> buildList { while (c.moveToNext()) c.getString(0)?.takeIf(String::isNotEmpty)?.let(::add) } }
    fun setValue(scriptId: String, name: String, value: String?): Boolean {
        if (scriptId.isEmpty() || name.isEmpty()) return false
        if (value.isNullOrEmpty()) return deleteValue(scriptId, name)
        val db = writableDatabase
        db.beginTransaction()
        return try {
            db.delete("variables", "script_id = ? AND name = ?", arrayOf(scriptId, name))
            val inserted = db.insert("variables", null, ContentValues().apply { put("script_id", scriptId); put("name", name); put("value", value) }) > 0
            db.setTransactionSuccessful(); inserted
        } finally { db.endTransaction() }
    }
    fun deleteValue(scriptId: String, name: String): Boolean = writableDatabase.delete("variables", "script_id = ? AND name = ?", arrayOf(scriptId, name)) > 0
    fun clearValues(scriptId: String): Boolean = writableDatabase.delete("variables", "script_id = ?", arrayOf(scriptId)) > 0

    private fun jsonList(items: List<String>): String? = if (items.isEmpty()) null else JSONArray(items).toString()
    private fun values(script: UserScript, now: Long) = ContentValues().apply {
        put("script_id", script.scriptId); put("name", script.name); put("version", script.version); put("content", script.content)
        put("matches", jsonList(script.matches)); put("exclude_matches", jsonList(script.excludeMatches)); put("includes", jsonList(script.includes)); put("excludes", jsonList(script.excludes)); put("run_at", script.runAt.value); put("requires", jsonList(script.requires)); put("resources", if (script.resources.isEmpty()) null else JSONObject(script.resources).toString()); put("enabled", if (script.enabled) 1 else 0); put("flags", script.flags); put("grant", script.grantMask()); put("icon_url", script.iconUrl); put("homepage_url", script.homepageUrl); put("support_url", script.supportUrl); put("download_url", script.downloadUrl); put("user_overrides", script.userOverrides); put("last_updated_at", if (script.lastUpdatedAt == 0L) now else script.lastUpdatedAt); put("created_at", if (script.createdAt == 0L) now else script.createdAt)
    }

    private fun read(c: android.database.Cursor): UserScript {
        fun value(name: String): String? = c.getString(c.getColumnIndexOrThrow(name))
        fun number(name: String) = c.getInt(c.getColumnIndexOrThrow(name))
        fun lines(name: String): List<String> {
            val text = value(name) ?: return emptyList()
            // Read original JSON arrays; preserve pre-restoration newline records until re-saved.
            return if (text.startsWith("[")) UserScript.jsonStrings(text) else text.split('\n').filter(String::isNotBlank)
        }
        val source = value("content").orEmpty()
        val parsed = UserScript.parse(source, value("download_url"))
        val mask = number("grant")
        val grants = if (parsed != null && parsed.grantMask() == mask) parsed.grants else UserScript.grantsForMask(mask)
        val resources = runCatching {
            val json = JSONObject(value("resources") ?: "{}")
            json.keys().asSequence().associateWith { json.optString(it) }
        }.getOrElse { lines("resources").mapNotNull { line -> line.split('\t', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap() }
        return UserScript(
            id = number("_id"), scriptId = value("script_id").orEmpty(), name = value("name").orEmpty(), namespace = parsed?.namespace,
            version = value("version"), description = parsed?.description, content = source,
            matches = lines("matches"), excludeMatches = lines("exclude_matches"), includes = lines("includes"), excludes = lines("excludes"),
            runAt = ScriptRunAt.entries.firstOrNull { it.value == number("run_at") } ?: ScriptRunAt.IDLE,
            requires = lines("requires"), resources = resources, enabled = number("enabled") == 1, grants = grants,
            flags = number("flags"), userOverrides = value("user_overrides"), iconUrl = value("icon_url"),
            homepageUrl = value("homepage_url"), supportUrl = value("support_url"), downloadUrl = value("download_url"),
            lastUpdatedAt = c.getLong(c.getColumnIndexOrThrow("last_updated_at")), createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
        )
    }
}
