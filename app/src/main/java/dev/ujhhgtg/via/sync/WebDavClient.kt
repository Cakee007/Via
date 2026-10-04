package dev.ujhhgtg.via.sync

import java.io.FileNotFoundException
import java.io.IOException
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader

/** qb.h and k4.a: HEAD, depth-1 PROPFIND, GET, MKCOL and PUT; no unrequested deletion. */
class WebDavClient(private val configuration: SyncConfiguration) {
    data class Resource(val path: String, val modified: Long)
    private var lastNonce: String? = null
    private var nonceCount = 0
    private var clientNonce: String? = null
    private val client = OkHttpClient.Builder().authenticator(object : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            val request = response.request
            if (!configuration.digestAuth) {
                if (request.header("Authorization")?.startsWith("Basic") == true) return null
                return request.newBuilder().header("Authorization", Credentials.basic(configuration.username, configuration.password)).build()
            }
            val challenges = response.headers("WWW-Authenticate")
            val challenge = challenges.firstOrNull { it.startsWith("Digest") }
                ?: throw IllegalArgumentException("unsupported auth scheme: $challenges")
            val parts = WebDavAuthentication.challenge(challenge).toMutableMap().apply {
                // DigestAuthenticator.c retains exact header names and lets matching fields override the challenge.
                val headers = response.headers
                for (index in 0 until headers.size) put(headers.name(index), headers.value(index))
            }
            val nonce = parts["nonce"] ?: throw IllegalArgumentException("missing nonce in challenge header: $challenge")
            if (parts["realm"] == null) return null
            // n() checks whether a Digest attempt already failed; only stale=true permits another retry.
            if (request.header("Authorization")?.startsWith("Digest") == true && !parts["stale"].equals("true", true)) return null
            if (lastNonce == nonce) nonceCount++ else {
                nonceCount = 1; lastNonce = nonce; clientNonce = WebDavAuthentication.randomNonce()
            }
            val url = request.url
            val path = url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: "")
            val authorization = WebDavAuthentication.digestHeader(request.method, path, configuration.username, configuration.password,
                parts, nonceCount, requireNotNull(clientNonce), request.header("http.auth.credential-charset"), request.body != null)
            return request.newBuilder().header("Authorization", authorization).build()
        }
    }).build()

    private fun url(path: String): String = configuration.baseUrl + path.removePrefix("/")
    private fun request(method: String, path: String, body: ByteArray? = null, type: String? = null, headers: Map<String, String> = emptyMap()): Response {
        val payload = body?.let { it.toRequestBody(type?.toMediaTypeOrNull()) }
        val builder = Request.Builder().url(url(path)).method(method, payload)
        headers.forEach { (key, value) -> builder.header(key, value) }
        return client.newCall(builder.build()).execute()
    }

    fun exists(path: String, directory: Boolean = false): Boolean = request("HEAD", path).use { response ->
        when (response.code) {
            404 -> false
            403 -> true // l4.a regards a forbidden resource as present.
            405 -> if (directory) true else { requireSuccess(response); true } // Only qb.h.m's directory check tolerates this.
            else -> { requireSuccess(response); true }
        }
    }

    fun get(path: String): ByteArray {
        if (!exists(path)) throw FileNotFoundException("File not found at $path")
        return request("GET", path).use { requireSuccess(it); it.body.bytes() }
    }

    fun list(path: String): List<Resource> = request("PROPFIND", path,
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><d:propfind xmlns:d=\"DAV:\"><d:allprop/></d:propfind>".toByteArray(Charsets.UTF_8),
        "text/xml", mapOf("Depth" to "1")).use { response ->
        requireSuccess(response)
        parseResources(response.body.bytes(), configuration.baseUrl)
    }

    fun modified(path: String): Long = list(path).firstOrNull { it.path == path }?.modified ?: 0L

    fun put(path: String, bytes: ByteArray, mime: String) {
        var slash = path.indexOf('/')
        while (slash > 0) {
            val directory = path.substring(0, slash + 1)
            if (!exists(directory, directory = true)) request("MKCOL", directory).use(::requireSuccess)
            slash = path.indexOf('/', slash + 1)
        }
        request("PUT", path, bytes, mime).use(::requireSuccess)
    }

    private fun requireSuccess(response: Response) {
        if (!response.isSuccessful) throw IOException("Error contacting ${response.request.url} (${response.code} ${response.message})")
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
