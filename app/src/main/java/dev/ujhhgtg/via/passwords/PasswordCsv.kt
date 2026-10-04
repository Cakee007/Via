package dev.ujhhgtg.via.passwords

import java.io.Reader
import java.io.Writer
import java.net.IDN
import java.util.Locale
import java.util.UUID

data class PasswordRecord(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String? = null,
    val username: String = "",
    val password: String? = null,
    val note: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
)

/** Seven CSV layouts recognized by va/d1, including Firefox and both Bitwarden layouts. */
object PasswordCsv {
    const val HEADER = "name,url,username,password,note"
    private val headers = mapOf(
        HEADER to 1,
        "\"url\",\"username\",\"password\",\"httprealm\",\"formactionorigin\",\"guid\",\"timecreated\",\"timelastused\",\"timepasswordchanged\"" to 2,
        "title,url,username,password,notes,otpauth" to 3,
        "title,url,username,password" to 4,
        "name,url,username,password" to 4,
        "collections,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp" to 5,
        "folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp" to 6,
        "title,username,password,url,notes,totp" to 7,
    )

    fun read(reader: Reader): List<PasswordRecord> {
        val input = reader.buffered()
        val header = input.readLine().orEmpty().filterNot { it.isWhitespace() || it == '\u00a0' }.lowercase(Locale.ROOT)
        val format = headers[header] ?: throw IllegalArgumentException("Unsupported password CSV header")
        val records = mutableListOf<PasswordRecord>()
        var quotes = 0
        val row = StringBuilder()
        input.forEachLine { line ->
            if (row.isNotEmpty()) row.append('\n')
            row.append(line)
            quotes += line.count { it == '"' }
            if (quotes % 2 == 0) {
                parseRecord(parseRow(row.toString()), format)?.let(records::add)
                row.setLength(0); quotes = 0
            }
        }
        return records
    }

    fun write(writer: Writer, records: List<PasswordRecord>): Int {
        writer.write("$HEADER\n")
        var count = 0
        records.forEach { record ->
            if (record.name.isNotEmpty() && !record.password.isNullOrEmpty()) {
                val url = record.url?.takeIf { it.contains(record.name) } ?: "https://${record.name}/"
                writer.write(listOf(record.name, url, record.username, record.password, record.note).joinToString(",", transform = ::escape))
                count++
            }
            writer.write("\n")
        }
        writer.flush()
        return count
    }

    fun host(url: String): String {
        val separator = url.indexOf("://")
        if (separator < 0) return ""
        return url.substring(separator + 3).substringBefore('/')
    }

    fun normalizeName(value: String): String {
        val trimmed = value.trim()
        val name = if (trimmed.contains("://")) host(trimmed) else trimmed
        return runCatching { name.split('.').joinToString(".") { IDN.toASCII(it) } }.getOrDefault(name)
    }

    fun parseRow(row: String): List<String> {
        val result = mutableListOf<String>()
        val value = StringBuilder()
        var quoted = false
        var index = 0
        while (index < row.length) {
            when (val c = row[index++]) {
                '"' if quoted && index < row.length && row[index] == '"' -> { value.append('"'); index++ }
                '"' -> quoted = !quoted
                ',' if !quoted -> { result.add(value.toString()); value.setLength(0) }
                else -> value.append(c)
            }
        }
        result.add(value.toString())
        return result
    }

    private fun parseRecord(fields: List<String>, format: Int): PasswordRecord? {
        var name: String? = null
        val url: String
        val username: String
        val password: String
        val note: String?
        when (format) {
            1 -> {
                if (fields.size < 4) return null
                url = fields[1]; username = fields[2]; password = fields[3]; note = fields.getOrNull(4)
                name = fields[0].let { if (it.contains('/')) host(url.ifEmpty { it }) else it }
            }
            2 -> { if (fields.size != 9) return null; url = fields[0]; username = fields[1]; password = fields[2]; note = null }
            3 -> { if (fields.size != 6) return null; url = fields[1]; username = fields[2]; password = fields[3]; note = fields[4] }
            4 -> { if (fields.size != 4) return null; url = fields[1]; username = fields[2]; password = fields[3]; note = null }
            5 -> {
                if (fields.size != 10 || fields[1] != "login") return null
                url = fields[6].split(',').firstOrNull(::isHttp).orEmpty(); username = fields[7]; password = fields[8]; note = fields[3]
            }
            6 -> {
                if (fields.size != 11 || fields[2] != "login") return null
                url = fields[7].split(',').firstOrNull(::isHttp).orEmpty(); username = fields[8]; password = fields[9]; note = fields[4]
            }
            7 -> { if (fields.size != 6) return null; url = fields[3]; username = fields[1]; password = fields[2]; note = fields[4] }
            else -> return null
        }
        if (name == null) name = if (isHttp(url)) host(url) else ""
        if (name.isEmpty() || password.isEmpty()) return null
        return PasswordRecord(name = name, url = url, username = username, password = password, note = note)
    }

    private fun isHttp(url: String) = url.startsWith("http://", true) || url.startsWith("https://", true)
    private fun escape(value: String?): String {
        if (value == null) return ""
        return if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${value.replace("\"", "\"\"")}\"" else value
    }
}
