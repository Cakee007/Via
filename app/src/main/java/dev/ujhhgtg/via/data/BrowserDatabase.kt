package dev.ujhhgtg.via.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.text.isDigitsOnly

/**
 * Via's actual database: filename `via`, schema version 11. The connection is
 * resident for the process lifetime (pa.r's repositories hold one open
 * helper); [shared] hands out that instance instead of opening per page.
 */
class BrowserDatabase(context: Context) : SQLiteOpenHelper(context.applicationContext, "via", null, 11) {
    private val appContext = context.applicationContext
    companion object {
        @Volatile private var shared: BrowserDatabase? = null

        /** pa.r.j(): one resident connection, opened lazily on first use. */
        fun shared(context: Context): BrowserDatabase = shared ?: synchronized(this) {
            shared ?: BrowserDatabase(context).also { shared = it }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE bookmarks(id INTEGER PRIMARY KEY,url TEXT,title TEXT,folder TEXT,clickTimes INTEGER,updated_at INTEGER DEFAULT 0,created_at INTEGER DEFAULT 0)")
        db.execSQL("CREATE TABLE history(id INTEGER PRIMARY KEY,url TEXT,title TEXT,updated_at INTEGER DEFAULT 0)")
        db.execSQL("CREATE TABLE favorites(f_id INTEGER PRIMARY KEY,f_url TEXT,f_title TEXT,f_order INTEGER,updated_at INTEGER DEFAULT 0,created_at INTEGER DEFAULT 0)")
        db.execSQL("CREATE TABLE addons(id INTEGER PRIMARY KEY,oid INTEGER,name TEXT,author TEXT,url TEXT,info TEXT,code LONGTEXT,flag INTEGER DEFAULT 0)")
        db.execSQL("CREATE TABLE conf(domain TEXT NOT NULL PRIMARY KEY,data TEXT)")
        db.execSQL("CREATE TABLE settings(id INTEGER PRIMARY KEY,title TEXT,content TEXT,note TEXT,flag INTEGER DEFAULT 0,type INTEGER DEFAULT 0,updated_at INTEGER DEFAULT 0,created_at INTEGER DEFAULT 0)")
        db.execSQL("CREATE TABLE bookmark_items(_id TEXT PRIMARY KEY,url TEXT,title TEXT,folder_id TEXT,ordering INTEGER,last_updated_at INTEGER DEFAULT 0,created_at INTEGER DEFAULT 0)")
        db.execSQL("CREATE TABLE bookmark_folders(_id TEXT PRIMARY KEY,title TEXT,parent_folder_id TEXT,ordering INTEGER,created_at INTEGER DEFAULT 0,last_updated_at INTEGER DEFAULT 0)")
        db.execSQL("CREATE TABLE tabs(_id TEXT PRIMARY KEY,url TEXT,title TEXT,file_path TEXT,flags INTEGER DEFAULT 0,last_visited_at INTEGER DEFAULT 0)")
    }
    /** q9.b delegates these gates to ea/ga/fa/ia/da/ja/o9/na in this exact order. */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion <= 2) db.execSQL("CREATE TABLE bookmarks(id INTEGER PRIMARY KEY,url TEXT,title TEXT,folder TEXT,clickTimes INTEGER,updated_at INTEGER DEFAULT 0,created_at INTEGER DEFAULT 0)")
        if (oldVersion < 8) {
            db.execSQL("ALTER TABLE bookmarks ADD COLUMN updated_at INTEGER DEFAULT 0;")
            db.execSQL("ALTER TABLE bookmarks ADD COLUMN created_at INTEGER DEFAULT 0;")
            backfillTimestamps(db, "bookmarks", includeCreated = true)
        }
        if (oldVersion < 8) {
            db.execSQL("ALTER TABLE history ADD COLUMN updated_at INTEGER DEFAULT 0;")
            backfillTimestamps(db, "history", includeCreated = false)
        }
        if (oldVersion <= 3) db.execSQL("CREATE TABLE favorites(f_id INTEGER PRIMARY KEY,f_url TEXT,f_title TEXT,f_order INTEGER,updated_at INTEGER DEFAULT 0,created_at INTEGER DEFAULT 0)")
        if (oldVersion < 8) {
            db.execSQL("ALTER TABLE favorites ADD COLUMN updated_at INTEGER DEFAULT 0;")
            db.execSQL("ALTER TABLE favorites ADD COLUMN created_at INTEGER DEFAULT 0;")
            backfillTimestamps(db, "favorites", includeCreated = true)
        }
        if (oldVersion <= 1) db.execSQL("CREATE TABLE addons(id INTEGER PRIMARY KEY,oid INTEGER,name TEXT,author TEXT,url TEXT,info TEXT,code LONGTEXT)")
        if (oldVersion <= 5) {
            db.execSQL("ALTER TABLE addons ADD COLUMN flag INTEGER DEFAULT 0")
            val preferences = BrowserPreferences(appContext)
            val disabled = preferences.disabledAddons.split(',').filter { it.isNotEmpty() && it.isDigitsOnly() }.map(String::toInt)
            if (disabled.isNotEmpty()) {
                val placeholders = disabled.joinToString(",", "(", ")") { "?" }
                db.update("addons", ContentValues().apply { put("flag", 1) }, "id in $placeholders", disabled.map(Int::toString).toTypedArray())
                preferences.disabledAddons = ""
            }
        }
        if (oldVersion <= 6) db.execSQL("CREATE TABLE conf(domain TEXT NOT NULL PRIMARY KEY,data TEXT)")
        if (oldVersion <= 8) db.execSQL("CREATE TABLE settings(id INTEGER PRIMARY KEY,title TEXT,content TEXT,note TEXT,flag INTEGER DEFAULT 0,type INTEGER DEFAULT 0,updated_at INTEGER DEFAULT 0,created_at INTEGER DEFAULT 0)")
        if (oldVersion <= 9) {
            db.execSQL("CREATE TABLE bookmark_items(_id TEXT PRIMARY KEY,url TEXT,title TEXT,folder_id TEXT,ordering INTEGER,last_updated_at INTEGER DEFAULT 0,created_at INTEGER DEFAULT 0)")
            db.execSQL("CREATE TABLE bookmark_folders(_id TEXT PRIMARY KEY,title TEXT,parent_folder_id TEXT,ordering INTEGER,created_at INTEGER DEFAULT 0,last_updated_at INTEGER DEFAULT 0)")
        }
        if (oldVersion <= 10) db.execSQL("CREATE TABLE tabs(_id TEXT PRIMARY KEY,url TEXT,title TEXT,file_path TEXT,flags INTEGER DEFAULT 0,last_visited_at INTEGER DEFAULT 0)")
    }

    private fun backfillTimestamps(db: SQLiteDatabase, table: String, includeCreated: Boolean) {
        val now = System.currentTimeMillis() / 1000
        db.update(table, ContentValues().apply {
            put("updated_at", now)
            if (includeCreated) put("created_at", now)
        }, null, null)
    }

}
