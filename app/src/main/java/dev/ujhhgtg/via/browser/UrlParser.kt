package dev.ujhhgtg.via.browser

import java.util.Locale

/** Literal i6.h0 parser: component ranges and the original validity predicates. */
class UrlParser(input: String?) {
    val value = input?.trim()
    var schemeRange: IntArray? = null; private set
    var userInfoRange: IntArray? = null; private set
    var hostRange: IntArray? = null; private set
    var portRange: IntArray? = null; private set
    var pathRange: IntArray? = null; private set
    var queryRange: IntArray? = null; private set
    var fragmentRange: IntArray? = null; private set
    var isValid = false; private set
    val scheme get() = component(schemeRange)?.lowercase(Locale.ROOT)
    val userInfo get() = component(userInfoRange)
    val host get() = component(hostRange)?.lowercase(Locale.ROOT)
    val port get() = component(portRange)
    val path get() = component(pathRange)
    val query get() = component(queryRange)
    val fragment get() = component(fragmentRange)
    val hasScheme get() = schemeRange != null
    val lastPathComponent get() = component(lastPathRange())
    init { parse() }

    private fun range(start: Int, end: Int): IntArray? = if (start !in 0..end) null else intArrayOf(start, end)
    private fun component(range: IntArray?): String? = range?.takeIf { it.size >= 2 && it[1] >= it[0] }?.let { value!!.substring(it[0], it[1]) }
    fun lastPathRange(): IntArray? {
        val path = pathRange ?: return null
        val text = value!!
        var end = path[1]
        if (text[end - 1] == '/') end--
        return range(maxOf(path[0], text.lastIndexOf('/', end - 1) + 1), end)
    }
    private fun parse() {
        val text = value
        if (text.isNullOrEmpty()) return
        var end = text.length
        schemeRange = schemeRange(0, text.indexOf(':'))
        val scheme = schemeRange
        val start = if (scheme == null) 0 else {
            val colon = scheme[1]
            if (colon + 2 >= end || text[colon + 1] != '/' || text[colon + 2] != '/') {
                pathRange = range(colon + 1, end)
                isValid = valid()
                return
            }
            colon + 3
        }
        if (start >= end) { isValid = valid(); return }
        fragmentRange = range(text.lastIndexOf('#', end), end)
        fragmentRange?.let { end = it[0]; it[0]++ }
        queryRange = range(text.indexOf('?', start), end)
        queryRange?.let { end = it[0]; it[0]++ }
        pathRange = range(text.indexOf('/', start), end)
        if (pathRange != null) end = pathRange!![0] else pathRange = intArrayOf(end, end)
        val colon = text.lastIndexOf(':', end)
        if (colon in 1..<end) {
            val after = colon + 1
            if (after == end || asciiOnly(text.substring(after, end), 4)) {
                portRange = range(after, end)
                if (portRange != null) end = portRange!![0] - 1
            }
        }
        val at = text.indexOf('@', start)
        var hostStart = start
        if (at in 1..end) {
            userInfoRange = range(start, at)
            if (userInfoRange != null) hostStart = userInfoRange!![1] + 1
        }
        hostRange = range(hostStart, end)
        isValid = valid()
    }
    private fun valid(): Boolean {
        val text = value
        if (text.isNullOrEmpty()) return false
        val whitespace = text.indexOfFirst { it.isWhitespace() || it == '\u00a0' }
        val scheme = scheme
        if (scheme == null) {
            val path = pathRange ?: return false
            return !(whitespace > 0 && whitespace < path[0]) && validBareHost(host)
        }
        if (scheme == "http" || scheme == "https" || scheme == "ftp") {
            val host = hostRange ?: return false
            return (whitespace > host[0] || whitespace < 0) && validHostCharacters(this.host, true)
        }
        val path = pathRange
        if (path != null && path[0] == schemeRange!![1] + 1) {
            if (scheme == "javascript" || scheme == "data") return true
            if (scheme == "about" && (path[0] == path[1] || asciiOnly(this.path, 3, "-"))) return true
            // The decompiled Java drops these OR branches; the smali has if-nez for each.
            return scheme in setOf("view-source", "magnet", "sms", "tel", "mailto", "geo", "tg")
        }
        return whitespace < 0
    }
    private fun validHostCharacters(host: String?, withScheme: Boolean): Boolean {
        val forbidden = if (withScheme) "!#$&\"'()*+,/;<=>?@\\^`{|}~" else "!#$%&\"'()*+,/;<=>?@\\^_`{|}~"
        return host?.any { it in forbidden } != true
    }
    private fun validBareHost(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        val normalized = normalizeDots(host)
        if (normalized == "localhost" || hasScheme && asciiOnly(normalized, 6)) return true
        if (normalized.first() == '[' && normalized.last() == ']') {
            return normalized.length > 3 && asciiOnly(normalized.substring(1, normalized.length - 1), 8, ":")
        }
        val dot = normalized.lastIndexOf('.')
        if (dot == normalized.lastIndex || normalized[0] == '.' || dot > 0 && ".." in normalized || !validHostCharacters(normalized, false)) return false
        if (path == "/") return true
        if (dot < 0) return false
        val suffix = normalized.substring(dot + 1)
        return if (asciiOnly(suffix, 4)) ipv4(normalized) else TopLevelDomains.contains(suffix)
    }
    private fun ipv4(host: String): Boolean {
        if (host.length < 7) return false
        var parts = 1
        var value = 0
        for (character in host) {
            if (character in '0'..'9') { value = value * 10 + (character - '0'); if (value > 255) return false }
            else { if (character != '.' || ++parts > 4) return false; value = 0 }
        }
        return parts == 4
    }
    private fun schemeRange(start: Int, end: Int): IntArray? {
        if (start !in 0..<end) return null
        val text = value!!
        if (!asciiCharacter(text[start], 3) || !asciiOnly(text.substring(start, end), 7, "+.-")) return null
        // Retain the source's literal length check even though localhost has nine characters.
        if (end - start == 7 && text.substring(start, end).lowercase(Locale.ROOT) == "localhost") return null
        if (text.indexOf('.', start) < end && UrlParser(text.substring(start, end)).isValid) return null
        return intArrayOf(start, end)
    }
    override fun toString() = "UrlParser{url='$value', scheme=$scheme, userInfo=$userInfo, host=$host, port=$port, path=$path, lastPathComponent=$lastPathComponent, query=$query, fragment=$fragment, validUrl=$isValid}"

    companion object {
        fun normalizeDots(host: String): String = host.map { if (it == '\u3002' || it == '\uff0e' || it == '\uff61') '.' else it }.joinToString("")
        private fun asciiCharacter(character: Char, flags: Int): Boolean =
            flags and 1 != 0 && character in 'A'..'Z' || flags and 2 != 0 && character in 'a'..'z' || flags and 4 != 0 && character in '0'..'9' ||
                flags and 8 != 0 && (character in '0'..'9' || character in 'a'..'f' || character in 'A'..'F')
        private fun asciiOnly(value: String?, flags: Int, extra: String = ""): Boolean = !value.isNullOrEmpty() && value.all { asciiCharacter(it, flags) || it in extra }
    }
}
