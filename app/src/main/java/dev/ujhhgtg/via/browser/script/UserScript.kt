package dev.ujhhgtg.via.browser.script

import java.net.URL
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

enum class ScriptRunAt(val value: Int) { START(1), END(2), IDLE(4), CONTEXT_MENU(8) }

/** r5.a/b/c: content is the complete original script, including its metadata block. */
data class UserScript(
    val id: Int = 0,
    val scriptId: String,
    val name: String = scriptId,
    val namespace: String? = null,
    val version: String? = null,
    val description: String? = null,
    val content: String,
    val matches: List<String> = emptyList(),
    val excludeMatches: List<String> = emptyList(),
    val includes: List<String> = emptyList(),
    val excludes: List<String> = emptyList(),
    val runAt: ScriptRunAt = ScriptRunAt.IDLE,
    val requires: List<String> = emptyList(),
    val resources: Map<String, String> = emptyMap(),
    val enabled: Boolean = false,
    val grants: List<String> = emptyList(),
    val flags: Int = 0,
    val userOverrides: String? = null,
    val iconUrl: String? = null,
    val homepageUrl: String? = null,
    val supportUrl: String? = null,
    val downloadUrl: String? = null,
    val lastUpdatedAt: Long = 0,
    val createdAt: Long = 0,
) {
    /** r5.d.j merges override patterns first, removing conflicting original patterns. */
    fun appliesTo(url: String): Boolean {
        if (url.isEmpty()) return false
        val overrides = overrideObject()
        val overrideIncludes = jsonStrings(overrides?.optString("matches")) + jsonStrings(overrides?.optString("includes"))
        val overrideExcludes = jsonStrings(overrides?.optString("excludeMatches")) + jsonStrings(overrides?.optString("excludes"))
        val negative = overrideExcludes + (excludeMatches + excludes).filterNot { it in overrideIncludes }
        val positive = overrideIncludes + (matches + includes).filterNot { it in negative }
        return positive.isNotEmpty() && negative.none { patternMatches(it, url) } && positive.any { patternMatches(it, url) }
    }

    fun runsAt(phase: ScriptRunAt): Boolean {
        val override = overrideObject()?.optInt("runAt", 0) ?: 0
        val mask = if (override == 0) runAt.value else override
        return mask and phase.value != 0
    }

    fun source(): String = content

    /** `@noframes`: the script runs only in top-level documents. Read from the stored source. */
    val noFrames: Boolean by lazy {
        content.lineSequence().map(String::trim).dropWhile { !it.startsWith("// ==UserScript==") }
            .takeWhile { !it.startsWith("// ==/UserScript==") }
            .any { Regex("//[\\s\\p{Zs}\\u200B]*@noframes(?:\\s.*)?").matches(it) }
    }

    fun grantMask(): Int = grants.fold(0) { bits, grant -> bits or (GRANT_BITS[grant] ?: 0) }
    private fun overrideObject(): JSONObject? = userOverrides?.let { runCatching { JSONObject(it) }.getOrNull() }

    companion object {
        /** r5.a.f: case-sensitive metadata names, URL fallback, stable MD5(namespace + name). */
        fun parse(source: String, sourceUrl: String? = null, locale: Locale = Locale.getDefault()): UserScript? {
            if (source.isEmpty()) return null
            var name: String? = null
            var namespace: String? = null
            if (!sourceUrl.isNullOrEmpty()) {
                val slash = sourceUrl.lastIndexOf('/') + 1
                if (slash > 0 && slash < sourceUrl.length) name = runCatching {
                    URLDecoder.decode(sourceUrl.substring(slash).replace(".user.js", ""), "UTF-8")
                }.getOrDefault(sourceUrl.substring(slash).replace(".user.js", ""))
                val scheme = sourceUrl.indexOf("://")
                if (scheme >= 0) {
                    val hostStart = scheme + 3
                    val hostEnd = sourceUrl.indexOf('/', hostStart).let { if (it < 0) sourceUrl.length else it }
                    namespace = sourceUrl.substring(hostStart, hostEnd)
                }
            }
            val fields = linkedMapOf<String, MutableList<String>>()
            val fieldPattern = Regex("//[\\s\\p{Zs}\\u200B]*@([^\\s\\p{Zs}\\u200B]+)(?:[\\s\\p{Zs}\\u200B]+(.*))?")
            var started = false
            var ended = false
            var unwrap = false
            var localizedName: String? = null
            val localizedKey = "name:${locale.language}"
            for (line in source.lineSequence()) {
                val text = line.trim()
                if (!started) { if (text.startsWith("// ==UserScript==")) started = true; continue }
                if (text.startsWith("// ==/UserScript==")) { ended = true; break }
                val match = fieldPattern.matchEntire(text) ?: continue
                val key = match.groupValues[1]
                if (key == "unwrap") { unwrap = true; continue }
                val value = match.groupValues[2].trim().takeIf(String::isNotEmpty) ?: continue
                fields.getOrPut(key) { mutableListOf() }.add(value)
                when (key) {
                    "name" -> name = value
                    "namespace" -> namespace = value
                    else -> if (key.startsWith(localizedKey) &&
                        (localizedName == null || key.length == localizedKey.length || key.endsWith(locale.country))) localizedName = value
                }
            }
            if (!ended || name == null || namespace == null) return null
            fun one(key: String) = fields[key]?.lastOrNull()
            fun many(key: String) = fields[key].orEmpty().distinct()
            val timestamp = System.currentTimeMillis()
            return UserScript(
                scriptId = stableId(namespace, name), name = localizedName ?: name, namespace = namespace,
                version = one("version"), description = one("description"), content = source,
                matches = many("match"), excludeMatches = many("exclude-match"), includes = many("include"), excludes = many("exclude"),
                runAt = when (one("run-at")) { "document-start" -> ScriptRunAt.START; "document-end", "document-body" -> ScriptRunAt.END; "context-menu" -> ScriptRunAt.CONTEXT_MENU; else -> ScriptRunAt.IDLE },
                requires = fields["require"].orEmpty().mapNotNull { resolveResource(it, sourceUrl) },
                resources = buildMap { fields["resource"].orEmpty().forEach {
                    val pair = Regex("(\\S+)\\s+(.*)").matchEntire(it)
                    if (pair != null) resolveResource(pair.groupValues[2], sourceUrl)?.let { url -> put(pair.groupValues[1], url) }
                } },
                enabled = true, grants = many("grant"), flags = if (unwrap) 1 else 0,
                iconUrl = one("icon"), homepageUrl = one("homepageURL"), supportUrl = one("supportURL"),
                downloadUrl = one("downloadURL") ?: sourceUrl, lastUpdatedAt = timestamp, createdAt = timestamp,
            )
        }

        private fun resolveResource(value: String, sourceUrl: String?): String? {
            if (value.length > 5 && value.startsWith("data:", true)) return value
            return runCatching { URL(sourceUrl?.let(::URL), value).toURI().toString() }.getOrNull()
        }

        fun stableId(namespace: String, name: String): String = md5(namespace + name)

        private fun md5(value: String): String = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }

        internal fun jsonStrings(value: String?): List<String> = if (value.isNullOrEmpty()) emptyList() else runCatching {
            val array = JSONArray(value); List(array.length()) { array.optString(it) }
        }.getOrDefault(emptyList())

        /** s5.a: case-insensitive glob or slash-delimited regex, with wildcard-root fallback. */
        fun patternMatches(pattern: String, url: String): Boolean {
            if (pattern.isEmpty()) return true
            val p = pattern.lowercase(Locale.ROOT)
            val u = url.lowercase(Locale.ROOT)
            if (p.length >= 2 && p.first() == '/' && p.last() == '/') {
                val expression = (if (p[1] == '^') "" else ".*") + p.substring(1, p.length - 1) + (if (p[p.length - 2] == '$') "" else ".*")
                return runCatching { Regex(expression).matches(u) }.getOrDefault(false)
            }
            if (globMatches(p, u)) return true
            val index = p.indexOf("://*.")
            return index in 1..9 && globMatches(p.substring(0, index) + "://" + p.substring(index + 5), u)
        }

        private fun globMatches(pattern: String, value: String): Boolean {
            var index = 0; var at = 0; var star = -1; var matched = -1
            while (at < value.length) {
                when {
                    index < pattern.length && pattern[index] == value[at] -> { index++; at++ }
                    index < pattern.length && pattern[index] == '*' -> { star = index++; matched = at }
                    star >= 0 -> { index = star + 1; at = ++matched }
                    else -> return false
                }
            }
            while (index < pattern.length && pattern[index] == '*') index++
            return index == pattern.length
        }

        fun grantsForMask(mask: Int): List<String> = GRANT_BITS.filterValues { it != 0 && mask and it != 0 }.keys.toList()
        // All mappings are taken from r5.a.b; dotted GM methods have separate permission bits.
        private val GRANT_BITS = linkedMapOf(
            "GM_setValue" to 1, "GM_getValue" to 2, "GM_listValues" to 4, "GM_deleteValue" to 8,
            "GM_getResourceURL" to 16, "GM_getResourceText" to 32, "GM_addStyle" to 64, "GM_addElement" to 128,
            "GM_xmlhttpRequest" to 256, "GM_log" to 512, "GM_setClipboard" to 1024, "GM_download" to 2048,
            "GM_info" to 4096, "GM_addValueChangeListener" to 8192, "GM_removeValueChangeListener" to 16384,
            "GM_openInTab" to 32768, "GM_registerMenuCommand" to 65536, "GM_unregisterMenuCommand" to 131072,
            "GM_notification" to 262144, "GM.info" to 524288, "GM.deleteValue" to 1048576, "GM.getValue" to 2097152,
            "GM.listValues" to 4194304, "GM.setValue" to 8388608, "GM.addStyle" to 16777216, "GM.addElement" to 33554432,
            "GM.getResourceUrl" to 67108864, "GM.notification" to 134217728, "GM.openInTab" to 268435456,
            "GM.registerMenuCommand" to 536870912, "GM.setClipboard" to 1073741824, "GM.xmlHttpRequest" to Int.MIN_VALUE,
        )
    }
}
