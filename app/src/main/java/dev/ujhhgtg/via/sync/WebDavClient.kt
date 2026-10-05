package dev.ujhhgtg.via.sync

import dev.ujhhgtg.via.common.basicAuthorization
import dev.ujhhgtg.via.common.finalUrl
import dev.ujhhgtg.via.common.httpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.isSuccess
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader

/** qb.h and k4.a: HEAD, depth-1 PROPFIND, GET, MKCOL and PUT; no unrequested deletion. */
class WebDavClient(private val configuration: SyncConfiguration) {
    data class Resource(val path: String, val modified: Long)
    private var lastNonce: String? = null
    private var nonceCount = 0
    private var clientNonce: String? = null

    /** Former OkHttp Authenticator: returns the Authorization header for a retry, or null to give up. */
    private fun authenticate(method: String, path: String, previous: String?, hasBody: Boolean, credentialCharset: String?, response: HttpResponse): String? {
        if (!configuration.digestAuth) {
            if (previous?.startsWith("Basic") == true) return null
            return basicAuthorization(configuration.username, configuration.password)
        }
        val challenges = response.headers.getAll("WWW-Authenticate").orEmpty()
        val challenge = challenges.firstOrNull { it.startsWith("Digest") }
            ?: throw IllegalArgumentException("unsupported auth scheme: $challenges")
        val parts = WebDavAuthentication.challenge(challenge).toMutableMap().apply {
            // DigestAuthenticator.c retains exact header names and lets matching fields override the challenge.
            response.headers.forEach { name, values -> values.forEach { put(name, it) } }
        }
        val nonce = parts["nonce"] ?: throw IllegalArgumentException("missing nonce in challenge header: $challenge")
        if (parts["realm"] == null) return null
        // n() checks whether a Digest attempt already failed; only stale=true permits another retry.
        if (previous?.startsWith("Digest") == true && !parts["stale"].equals("true", true)) return null
        if (lastNonce == nonce) nonceCount++ else {
            nonceCount = 1; lastNonce = nonce; clientNonce = WebDavAuthentication.randomNonce()
        }
        return WebDavAuthentication.digestHeader(method, path, configuration.username, configuration.password,
            parts, nonceCount, requireNotNull(clientNonce), credentialCharset, hasBody)
    }

    private fun endpoint(path: String): String = configuration.baseUrl + path.removePrefix("/")
    private suspend fun request(method: String, path: String, body: ByteArray? = null, type: String? = null, headers: Map<String, String> = emptyMap()): HttpResponse {
        var authorization: String? = null
        // OkHttp's RetryAndFollowUpInterceptor allowed at most 20 follow-ups.
        repeat(21) {
            val response = httpClient.request {
                url(endpoint(path))
                this.method = HttpMethod.parse(method)
                headers.forEach { (key, value) -> header(key, value) }
                authorization?.let { header("Authorization", it) }
                if (body != null) setBody(ByteArrayContent(body, type?.let { runCatching { ContentType.parse(it) }.getOrNull() }))
            }
            if (response.status != HttpStatusCode.Unauthorized) return response
            val url = io.ktor.http.Url(response.finalUrl)
            val target = url.encodedPath + (url.encodedQuery.takeIf(String::isNotEmpty)?.let { "?$it" } ?: "")
            authorization = authenticate(method, target, authorization, body != null, headers["http.auth.credential-charset"], response)
                ?: return response
        }
        throw IOException("Too many follow-up requests: 21")
    }

    suspend fun exists(path: String, directory: Boolean = false): Boolean = request("HEAD", path).let { response ->
        when (response.status.value) {
            404 -> false
            403 -> true // l4.a regards a forbidden resource as present.
            405 -> if (directory) true else { requireSuccess(response); true } // Only qb.h.m's directory check tolerates this.
            else -> { requireSuccess(response); true }
        }
    }

    suspend fun get(path: String): ByteArray {
        if (!exists(path)) throw FileNotFoundException("File not found at $path")
        return request("GET", path).let { requireSuccess(it); it.bodyAsBytes() }
    }

    suspend fun list(path: String): List<Resource> = request("PROPFIND", path,
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><d:propfind xmlns:d=\"DAV:\"><d:allprop/></d:propfind>".toByteArray(Charsets.UTF_8),
        "text/xml", mapOf("Depth" to "1")).let { response ->
        requireSuccess(response)
        parseResources(response.bodyAsBytes(), configuration.baseUrl)
    }

    suspend fun modified(path: String): Long = list(path).firstOrNull { it.path == path }?.modified ?: 0L

    suspend fun put(path: String, bytes: ByteArray, mime: String) {
        var slash = path.indexOf('/')
        while (slash > 0) {
            val directory = path.substring(0, slash + 1)
            if (!exists(directory, directory = true)) requireSuccess(request("MKCOL", directory))
            slash = path.indexOf('/', slash + 1)
        }
        requireSuccess(request("PUT", path, bytes, mime))
    }

    private fun requireSuccess(response: HttpResponse) {
        if (!response.status.isSuccess()) throw IOException("Error contacting ${response.request.url} (${response.status.value} ${response.status.description})")
    }

    companion object {
        fun parseResources(bytes: ByteArray, baseUrl: String): List<Resource> {
            if (bytes.isEmpty()) return emptyList()
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val parser = factory.newDocumentBuilder().apply { setEntityResolver { _, _ -> InputSource(StringReader("")) } }
            val document = parser.parse(bytes.inputStream())
            val responses = document.getElementsByTagNameNS("*", "response")
            val basePath = URI(baseUrl).path.orEmpty()
            return buildList {
                for (index in 0 until responses.length) {
                    val response = responses.item(index) as? Element ?: continue
                    fun value(name: String): String? = response.getElementsByTagNameNS("*", name).item(0)?.textContent
                    val href = value("href") ?: continue
                    val path = runCatching { URI(href).path }.getOrNull() ?: continue
                    val relative = if (path.startsWith(basePath)) path.substring(basePath.length) else path
                    val modified = parseDate(value("getlastmodified")) ?: parseDate(value("creationdate")) ?: 0L
                    add(Resource(relative, modified))
                }
            }
        }

        private fun parseDate(value: String?): Long? {
            if (value.isNullOrEmpty()) return null
            for (pattern in listOf("EEE, dd MMM yyyy HH:mm:ss zzz", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")) {
                val result = runCatching { SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("GMT") }.parse(value)?.time }.getOrNull()
                if (result != null) return result
            }
            return null
        }
    }
}
