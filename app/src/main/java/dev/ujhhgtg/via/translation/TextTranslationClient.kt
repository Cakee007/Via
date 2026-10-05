package dev.ujhhgtg.via.translation

import dev.ujhhgtg.via.common.httpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

/** oa.b/d: the original language identity and translation response fields. */
internal data class TranslationLanguage(val code: String, var name: String)
internal data class TranslationResult(val source: String, val target: String, val from: String, val to: String)

/** oa.a + oa.c. These are the original endpoint, headers, language order and cache semantics. */
internal class TextTranslationClient(private val locale: Locale) {
    private val targets: List<TranslationLanguage> by lazy { languageCodes.map { (code, fallback) -> language(code, fallback) } }
    val sourceLanguages: List<TranslationLanguage> by lazy { listOf(TranslationLanguage("auto", "Auto")) + targets.map { it.copy() } }
    val targetLanguages: List<TranslationLanguage> get() = targets
    private val results = ArrayList<TranslationResult>()

    fun sourceLanguage(code: String?): TranslationLanguage = sourceLanguages.firstOrNull { it.code == code } ?: sourceLanguages[0]
    fun targetLanguage(code: String?): TranslationLanguage = targetLanguages.firstOrNull { it.code == code } ?: targetLanguages[0]

    suspend fun translate(query: String, source: String?, target: String): TranslationResult? {
        val text = query.trim { it <= ' ' }
        if (text.isEmpty() || target.isEmpty()) return null
        // oa.c.g deliberately does not include source language in its cache lookup.
        results.firstOrNull { it.source == text && it.to == target }?.let { return it }
        return request(text, source?.takeIf(String::isNotEmpty) ?: "auto", target)?.also(results::add)
    }

    private suspend fun request(query: String, source: String, target: String): TranslationResult? {
        return try {
            val response = httpClient.get(requestUrl(query, source, target)) { header("User-Agent", USER_AGENT) }
            if (response.status != HttpStatusCode.OK) return null
            val body = response.bodyAsBytes().toString(Charsets.UTF_8).lineSequence()
                .joinToString("") { it.trim { char -> char <= ' ' } }
            parseResponse(query, source, target, body)
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            error.printStackTrace()
            null
        }
    }

    private fun language(code: String, fallback: String): TranslationLanguage {
        val translated = if (code == "zh-TW") {
            if (locale.language == Locale("zh").language) "繁體中文" else "Chinese (Traditional)"
        } else Locale(code).getDisplayLanguage(locale)
        return TranslationLanguage(code, if (!code.equals(translated, true) && translated.isNotEmpty()) translated else fallback)
    }

    companion object {
        internal const val USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.116 Safari/537.36"
        internal fun requestUrl(query: String, source: String, target: String): String =
            "https://translate.google.com/translate_a/single?dj=1&sl=$source&tl=$target" +
                "&ie=UTF-8&oe=UTF-8&client=at&dt=t&otf=2&q=" + URLEncoder.encode(query, "UTF-8").replace("+", "%20")

        /** oa.a.j reads exactly the first sentence and retains the requested source code. */
        internal fun parseResponse(query: String, source: String, target: String, body: String): TranslationResult? {
            val sentences = JSONObject(body).optJSONArray("sentences") ?: return null
            if (sentences.length() == 0) return null
            val translated = sentences.getJSONObject(0).optString("trans")
            return translated.takeIf(String::isNotEmpty)?.let { TranslationResult(query, it, source, target) }
        }

        private val languageCodes = listOf(
            "ar" to "Arabic", "az" to "Azerbaijani", "be" to "Belarusian", "bn" to "Bengali",
            "cs" to "Czech", "de" to "German", "en" to "English", "es" to "Spanish", "fa" to "Persian",
            "tl" to "Filipino", "fr" to "French", "hi" to "Hindi", "hr" to "Croatian", "hu" to "Hungarian",
            "id" to "Indonesian", "it" to "Italian", "ja" to "Japanese", "ko" to "Korean", "lt" to "Lithuanian",
            "ne" to "Nepali", "pl" to "Polish", "pt" to "Portuguese", "ro" to "Romanian", "ru" to "Russian",
            "sl" to "Slovenian", "ta" to "Tamil", "tr" to "Turkish", "uk" to "Ukrainian", "uz" to "Uzbek",
            "vi" to "Vietnamese", "zh" to "Chinese", "zh-TW" to "Chinese (Traditional)",
        )
    }
}
