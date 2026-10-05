package dev.ujhhgtg.via.search

import dev.ujhhgtg.via.common.httpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.Charset

/** ma/a..e and ka/i: four original suggestion protocols, injectable transport for local tests. */
class RemoteSuggestions(private val fetch: suspend (url: String, charset: String) -> String? = ::httpGet) {
    private val cache = object : LinkedHashMap<String, List<String>>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean = size > 128
    }
    /** Cancelling the calling coroutine aborts the in-flight request. */
    suspend fun query(text: String, provider: Int, enabled: Boolean, languageTag: String): List<String> {
        val query = text.trim()
        if (!enabled || query.isEmpty() || query.length > 200 || query.startsWith("http://", true) || query.startsWith("https://", true)) return emptyList()
        val key = "$provider:$query"
        synchronized(cache) { cache[key] }?.let { return it }
        val endpoint = endpoint(provider, query, languageTag) ?: return emptyList()
        val response = fetch(endpoint, if (provider == 2) "GB2312" else "UTF-8") ?: return emptyList()
        val values = parse(provider, response)
        if (values.isNotEmpty()) synchronized(cache) { cache[key] = values }
        return values
    }
    fun clearCache() = synchronized(cache) { cache.clear() }

    companion object {
        fun endpoint(provider: Int, query: String, languageTag: String): String? {
            val encoded = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
            val language = languageTag.substringBefore('-').substringBefore('_').let {
                if (it == "zh" && (languageTag.endsWith("TW") || languageTag.endsWith("HK"))) "zh-hant" else it
            }
            return when (provider) {
                1 -> "https://clients1.google.com/complete/search?hl=$language&output=toolbar&q=$encoded"
                2 -> "https://suggestion.baidu.com/su?wd=$encoded&cb=suggestion"
                3 -> "https://api.bing.com/qsonhs.aspx?type=cb&cb=s&q=$encoded"
                4 -> "https://sug.so.360.cn/suggest?encodein=utf-8&encodeout=utf-8&format=json&word=$encoded"
                else -> null
            }
        }
        fun parse(provider: Int, response: String, limit: Int = 5): List<String> = runCatching {
            when (provider) {
                1 -> Regex("<suggestion data=\"(.*?)\"/>").findAll(response).take(limit).map { decodeEntities(it.groupValues[1]) }.toList()
                2 -> {
                    if (!response.startsWith("suggestion({")) return emptyList()
                    val end = response.lastIndexOf(')').takeIf { it > 0 } ?: response.length
                    val array = JSONObject(response.substring(11, end)).optJSONArray("s") ?: return emptyList()
                    (0 until minOf(array.length(), limit)).map { decodeEntities(array.optString(it)) }.filter(String::isNotEmpty)
                }
                3 -> {
                    val start = response.indexOf(",\"Suggests\":[")
                    val end = if (start < 0) -1 else response.indexOf("]}]}}", start + 13)
                    if (start < 0 || end <= start + 13) return emptyList()
                    val array = JSONArray(response.substring(start + 12, end + 1))
                    (0 until minOf(array.length(), limit)).mapNotNull { array.optJSONObject(it)?.optString("Txt")?.takeIf(String::isNotEmpty) }
                }
                4 -> {
                    val array = JSONObject(response).optJSONArray("result") ?: return emptyList()
                    (0 until minOf(array.length(), limit)).mapNotNull { array.optJSONObject(it)?.optString("word")?.takeIf(String::isNotEmpty) }
                }
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
        private fun decodeEntities(value: String): String {
            val named = value.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'")
            return Regex("&#(x[0-9A-Fa-f]+|[0-9]+);").replace(named) { match ->
                runCatching { String(Character.toChars(if (match.groupValues[1].startsWith('x')) match.groupValues[1].drop(1).toInt(16) else match.groupValues[1].toInt())) }.getOrDefault(match.value)
            }
        }
    }
}

private suspend fun httpGet(url: String, charset: String): String? = try {
    val response = httpClient.get(url) {
        header("User-Agent", "Mozilla/5.0")
        timeout { connectTimeoutMillis = 3000 }
    }
    if (response.status != HttpStatusCode.OK) null
    else response.bodyAsBytes().toString(Charset.forName(charset)).lineSequence().joinToString("") { it.trim() }
} catch (error: CancellationException) { throw error } catch (_: Exception) { null }
