package dev.ujhhgtg.via.passwords

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.core.content.edit
import dev.ujhhgtg.via.data.transaction
import java.io.File
import java.io.Reader
import java.io.StringWriter
import java.io.Writer
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

/** v9/e database and v9/d encryption boundary; metadata listing does not decrypt passwords. */
class PasswordRepository private constructor(context: Context) {
    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val keys = PasswordKeyStore(this.context)
    private val database = object : SQLiteOpenHelper(this.context, "pass", null, 1) {
        override fun onCreate(db: SQLiteDatabase) = db.execSQL("CREATE TABLE IF NOT EXISTS pass (id TEXT PRIMARY KEY NOT NULL, name TEXT, url TEXT, username TEXT, password TEXT, note TEXT, updated_at INTEGER DEFAULT 0, created_at INTEGER DEFAULT 0);")
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val ignoredFile = File(this.context.getExternalFilesDir("pass") ?: File(this.context.filesDir, "pass"), "ignored_sites.txt")

    var offerToSave: Boolean
        get() = preferences.getInt("appflag", 1250) and 131072 == 0
        set(enabled) {
            val flags = preferences.getInt("appflag", 1250)
            preferences.edit {
                putInt(
                    "appflag",
                    if (enabled) flags and 131072.inv() else flags or 131072
                )
            }
        }

    fun <T> async(work: () -> T, done: (Result<T>) -> Unit) {
        executor.execute { val result = runCatching(work); main.post { done(result) } }
    }

    fun list(query: String = ""): List<PasswordRecord> {
        val selection = if (query.isEmpty()) null else "name LIKE ? OR username LIKE ?"
        val args = if (query.isEmpty()) null else arrayOf("%$query%", "%$query%")
        return database.readableDatabase.query("pass", null, selection, args, null, null, "created_at ASC").use { c ->
            buildList { while (c.moveToNext()) add(c.record()) }
        }
    }

    fun get(id: String, decrypt: Boolean = false): PasswordRecord? {
        val record = database.readableDatabase.query("pass", null, "id = ?", arrayOf(id), null, null, null, "1").use { if (it.moveToFirst()) it.record() else null } ?: return null
        return if (decrypt && record.password != null) record.copy(password = decryptPassword(record.password, keys.load())) else record
    }

    fun findId(name: String, username: String): String? = database.readableDatabase.query("pass", arrayOf("id"), "name = ? AND username = ?", arrayOf(name, username), null, null, null, "1").use { if (it.moveToFirst()) it.getString(0) else null }

    fun save(record: PasswordRecord): Boolean {
        val cipher = record.password?.let { encryptPassword(it, keys.load()) }
        val values = values(record.copy(password = cipher))
        val db = database.writableDatabase
        return if (get(record.id) != null) {
            values.remove("id")
            db.update("pass", values, "id = ?", arrayOf(record.id)) > 0
        } else db.transaction {
            db.delete("pass", "name = ? AND username = ?", arrayOf(record.name, record.username))
            db.insertOrThrow("pass", null, values) > 0
        }
    }

    fun delete(id: String): Boolean = database.writableDatabase.delete("pass", "id = ?", arrayOf(id)) > 0

    fun importCsv(reader: Reader): Int = importEntries(PasswordCsv.read(reader))
    fun exportCsv(writer: Writer): Int {
        val key = keys.load()
        val clear = list().map { it.copy(password = it.password?.let { encoded -> decryptPassword(encoded, key) }) }
        return PasswordCsv.write(writer, clear)
    }

    fun importEntries(entries: List<PasswordRecord>): Int {
        if (entries.isEmpty()) return 0
        val key = keys.load()
        val db = database.writableDatabase
        return db.transaction {
            entries.forEach { record ->
                val encoded = record.copy(password = record.password?.let { encryptPassword(it, key) })
                db.delete("pass", "name = ? AND username = ?", arrayOf(record.name, record.username))
                db.insertOrThrow("pass", null, values(encoded))
            }
            entries.size
        }
    }

    fun exportEncrypted(password: String): Map<String, ByteArray> {
        val salt = UUID.randomUUID().toString().replace("-", "")
        val csv = StringWriter().also(::exportCsv).toString().toByteArray(Charsets.UTF_8)
        return linkedMapOf("info.enc" to PasswordCrypto.transformBackupInfo(salt.toByteArray(Charsets.UTF_8)),
            "pass.enc" to PasswordCrypto.encrypt(PasswordCrypto.deriveKey(password, salt), csv))
    }

    fun importEncrypted(info: ByteArray, encrypted: ByteArray, password: String): Int {
        val salt = PasswordCrypto.transformBackupInfo(info).toString(Charsets.UTF_8)
        val clear = PasswordCrypto.decrypt(PasswordCrypto.deriveKey(password, salt), encrypted)
        return importCsv(clear.toString(Charsets.UTF_8).reader())
    }

    fun ignoredSites(): List<String> = if (ignoredFile.isFile) ignoredFile.readLines().filter(String::isNotEmpty) else emptyList()
    fun setSavingAllowed(url: String, allowed: Boolean) {
        val name = PasswordCsv.host(if (url.contains("://")) url else "https://$url")
        if (name.isEmpty()) return
        val sites = ignoredSites().toMutableSet()
        if (allowed) sites.remove(name) else sites.add(name)
        ignoredFile.parentFile?.mkdirs()
        ignoredFile.writeText(sites.joinToString("\n", postfix = if (sites.isEmpty()) "" else "\n"))
    }
    fun canOfferSaving(url: String): Boolean = offerToSave && PasswordCsv.host(url) !in ignoredSites()

    /** Domain ranking in v9/d.e/t, including the original i6/i0.b suffix heuristic. */
    fun forUrl(url: String): List<PasswordRecord> {
        val host = PasswordCsv.host(url)
        if (host.isEmpty()) return emptyList()
        val base = baseDomain(host.substringBefore(':'))
        return list().filter { it.name == host || it.name == base || it.name.endsWith(".$base") }
            .sortedWith(compareByDescending<PasswordRecord> { when { it.name.equals(host, true) -> 3; it.name.equals(base, true) -> 2; else -> 1 } }.thenBy { it.createdAt })
    }

    private fun baseDomain(host: String): String {
        if (host.all { it.isDigit() || it == '.' } || ':' in host) return host
        val labels = host.split('.')
        if (labels.size < 3) return host
        val last = labels.last().lowercase(Locale.ROOT)
        return if (last in listOf("com", "net", "org", "gov", "co", "edu")) labels.takeLast(2).joinToString(".") else labels.takeLast(3).joinToString(".")
    }

    private fun encryptPassword(password: String, key: ByteArray) = Base64.encodeToString(PasswordCrypto.encrypt(key, password.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    private fun decryptPassword(encoded: String, key: ByteArray): String? = runCatching { PasswordCrypto.decrypt(key, Base64.decode(encoded, Base64.NO_WRAP)).toString(Charsets.UTF_8) }.getOrNull()
    private fun Cursor.record() = PasswordRecord(getString(getColumnIndexOrThrow("id")), getString(getColumnIndexOrThrow("name")).orEmpty(), getString(getColumnIndexOrThrow("url")), getString(getColumnIndexOrThrow("username")).orEmpty(), getString(getColumnIndexOrThrow("password")), getString(getColumnIndexOrThrow("note")), getLong(getColumnIndexOrThrow("updated_at")), getLong(getColumnIndexOrThrow("created_at")))
    private fun values(record: PasswordRecord) = ContentValues().apply {
        put("id", record.id); put("name", record.name); put("url", record.url); put("username", record.username)
        if (record.password != null) put("password", record.password)
        put("note", record.note); put("updated_at", record.updatedAt); put("created_at", record.createdAt)
    }

    companion object {
        // Only ever holds the application context.
        @SuppressLint("StaticFieldLeak")
        private var instance: PasswordRepository? = null
        fun get(context: Context): PasswordRepository = instance ?: PasswordRepository(context).also { instance = it }
    }
}
