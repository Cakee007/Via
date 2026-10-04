package dev.ujhhgtg.via.data

import android.database.sqlite.SQLiteDatabase
import java.net.IDN
import java.net.URLDecoder

internal inline fun <T> SQLiteDatabase.transaction(block: () -> T): T {
    beginTransaction()
    return try { block().also { setTransactionSuccessful() } } finally { endTransaction() }
}

/** URL lookup alternatives from z8/i0.f: a root slash, IDN host and decoded path. */
internal fun urlLookupVariants(url: String): Array<String> {
    if (url.isEmpty()) return arrayOf(url)
    val values = linkedSetOf(url)
    val scheme = url.indexOf("://")
    if (scheme <= 0) return values.toTypedArray()
    val hostStart = scheme + 3
    val pathStart = url.indexOf('/', hostStart)
    val rootSlash = pathStart == url.lastIndex
    if (rootSlash) values.add(url.dropLast(1))
    val hostEnd = if (pathStart < 0) url.length else pathStart
    var unicode: String? = null
    if (url.indexOf("xn--", hostStart) > 0) {
        unicode = url.substring(0, hostStart) + IDN.toUnicode(url.substring(hostStart, hostEnd)) + url.substring(hostEnd)
        values.add(unicode)
        if (rootSlash) values.add(unicode.dropLast(1))
    }
    if (pathStart > 0 && !rootSlash && url.substring(pathStart).contains('%')) {
        val path = try { URLDecoder.decode(url.substring(pathStart), "UTF-8") } catch (_: IllegalArgumentException) { url.substring(pathStart) }
        values.add(url.substring(0, pathStart) + path)
        unicode?.let { values.add(it.substring(0, it.indexOf('/', hostStart)) + path) }
    }
    return values.toTypedArray()
}

/** The original distinguishes complete HTTP URLs from title/address substrings. */
internal fun searchSelection(query: String, titleColumn: String = "title", urlColumn: String = "url"): Pair<String, Array<String>> {
    val escaped = query.replace("%", "\\%").replace("_", "\\_")
    val suffix = if (escaped != query) " ESCAPE '\\'" else ""
    return if (query.startsWith("http://", true) || query.startsWith("https://", true)) {
        "$urlColumn LIKE ?$suffix" to arrayOf("$escaped%")
    } else {
        "$urlColumn LIKE ?$suffix OR $titleColumn LIKE ?$suffix OR $titleColumn LIKE ?$suffix" to arrayOf("%_$escaped%", "%_$escaped%", "$escaped%")
    }
}
