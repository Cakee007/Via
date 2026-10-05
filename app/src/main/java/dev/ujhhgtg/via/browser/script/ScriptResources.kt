package dev.ujhhgtg.via.browser.script

import android.content.Context
import android.util.Base64
import android.webkit.MimeTypeMap
import dev.ujhhgtg.via.common.httpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PushbackInputStream
import java.net.URLDecoder
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.Locale

/** p5.a/p5.b resource cache. Network methods suspend; cancelling the caller aborts the transfer. */
class ScriptResources(private val directory: File) {
    constructor(context: Context) : this(File(context.applicationContext.getExternalFilesDir("gm")
        ?: File(context.applicationContext.filesDir, "gm"), "resources"))

    init { if (!directory.exists()) directory.mkdirs() }

    suspend fun ensure(script: UserScript): Boolean {
        var complete = true
        for (url in script.requires + script.resources.values) if (!ensure(url)) complete = false
        return complete
    }

    /** p5.a.K only downloads HTTP resources; inline data requires no cache file. */
    suspend fun ensure(url: String): Boolean {
        if (!url.startsWith("http", true)) return true
        val target = cacheFile(url) ?: return false
        // Confirmed in smali p5/b.W: this is intentionally the original '<' comparison.
        if (target.exists() && target.length() > 0 && target.lastModified() < System.currentTimeMillis() - FIFTEEN_DAYS) return true
        val temporary = File(directory, target.name + ".tmp")
        val downloaded = try { download(url, temporary) || download(fallbackUrl(url) ?: url, temporary) }
            catch (error: CancellationException) { temporary.delete(); throw error }
        if (!downloaded) { temporary.delete(); return false }
        if (target.exists() && !target.deleteRecursively()) return false
        return temporary.renameTo(target)
    }

    /** Cached dependency source; file reads retain s5.b.i's newline normalization. */
    fun text(url: String): String? {
        val inline = dataText(url)
        val value = inline ?: cacheFile(url)?.takeIf { it.exists() }?.let { file ->
            runCatching { file.bufferedReader(Charset.defaultCharset()).use { reader ->
                buildString { reader.forEachLine { append(it).append('\n') } }
            } }.getOrDefault("")
        } ?: return null
        return if (value.isEmpty() || valid(url, value.toByteArray(Charsets.UTF_8))) value else ""
    }

    /** Null means missing, while an empty array also represents an integrity mismatch. */
    fun bytes(url: String): ByteArray? {
        val inline = dataBytes(url)
        val value = inline?.takeIf { it.isNotEmpty() }
            ?: cacheFile(url)?.takeIf { it.exists() }?.let { runCatching { it.readBytes() }.getOrDefault(byteArrayOf()) }
            ?: return null
        return if (value.isEmpty() || valid(url, value)) value else byteArrayOf()
    }

    fun resourceText(script: UserScript, name: String): String? {
        val url = script.resources[name] ?: return null
        return bytes(url)?.toString(Charsets.UTF_8)
    }

    fun resourceUrl(script: UserScript, name: String): String? {
        val url = script.resources[name] ?: return null
        val value = bytes(url) ?: return null
        if (value.isEmpty()) return ""
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        val mime = extension?.takeIf { it.isNotEmpty() }?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
            ?: "application/octet-stream"
        return "data:$mime;base64," + Base64.encodeToString(value, Base64.NO_WRAP)
    }

    /** Original p5.b.w also exposes the cache path even before it exists. */
    fun path(url: String): String? = if (url.startsWith("http", true)) cacheFile(url)?.absolutePath else null

    fun cleanup(allScripts: List<UserScript>): Boolean {
        val urls = allScripts.flatMap { it.requires + it.resources.values }.toSet()
        if (urls.isEmpty()) {
            val deleted = !directory.exists() || directory.deleteRecursively()
            directory.mkdirs()
            return deleted
        }
        val keep = urls.map(::cacheName).toSet()
        val files = directory.listFiles() ?: return false
        if (files.isEmpty()) return false
        var complete = true
        for (file in files) if (file.name !in keep && !file.deleteRecursively()) complete = false
        return complete
    }

    /** s5.b.d/e: download a script source for installation/update, with CDN fallback. */
    suspend fun fetchSource(url: String): String? {
        val result = fetchText(url)
        if (!result.isNullOrEmpty()) return result
        return fallbackUrl(url)?.let { fetchText(it) }
    }

    private fun cacheFile(url: String): File? = url.takeIf { it.isNotEmpty() }?.let { File(directory, cacheName(it)) }

    private suspend fun download(url: String, target: File): Boolean {
        if (!directory.exists() && !directory.mkdirs()) return false
        return try {
            httpClient.prepareGet(url) {
                header("User-Agent", RESOURCE_USER_AGENT)
                timeout { connectTimeoutMillis = 10_000; socketTimeoutMillis = 30_000 }
            }.execute { response ->
                if (response.status != HttpStatusCode.OK) false
                else { target.outputStream().use { output -> response.bodyAsChannel().copyTo(output) }; true }
            }
        } catch (error: CancellationException) { throw error } catch (_: Exception) { false }
    }

    private suspend fun fetchText(url: String): String? = try {
        val response = httpClient.get(url) { timeout { socketTimeoutMillis = 5_000 } }
        // HttpURLConnection.getInputStream threw for error statuses.
        if (response.status.value >= 400) null
        else bomReader(response.bodyAsBytes().inputStream(), response.headers["Content-Encoding"]).use { it.readText() }
    } catch (error: CancellationException) { throw error } catch (_: Exception) { null }


    companion object {
        fun bomReader(input: InputStream, encoding: String?): InputStreamReader {
            val stream = PushbackInputStream(input, 4)
            val prefix = ByteArray(4)
            val count = stream.read(prefix)
            fun byte(index: Int) = prefix[index].toInt() and 255
            val marker = when {
                byte(0) == 0xef && byte(1) == 0xbb && byte(2) == 0xbf -> 3 to "UTF-8"
                byte(0) == 0xfe && byte(1) == 0xff -> 2 to "UTF-16BE"
                byte(0) == 0xff && byte(1) == 0xfe -> 2 to "UTF-16LE"
                byte(0) == 0 && byte(1) == 0 && byte(2) == 0xfe && byte(3) == 0xff -> 4 to "UTF-32BE"
                byte(0) == 0xff && byte(1) == 0xfe && byte(2) == 0 && byte(3) == 0 -> 4 to "UTF-32LE"
                else -> 0 to encoding
            }
            if (count > marker.first) stream.unread(prefix, marker.first, count - marker.first)
            return if (marker.second == null) InputStreamReader(stream) else InputStreamReader(stream, marker.second)
        }
        private const val FIFTEEN_DAYS = 1_296_000_000L
        private const val RESOURCE_USER_AGENT = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        fun cacheName(url: String): String = hex(MessageDigest.getInstance("MD5").digest(url.toByteArray(Charset.defaultCharset())))

        fun fallbackUrl(url: String): String? = when {
            url.startsWith("https://cdn.jsdelivr.net/") && url.length > 25 -> "https://fastly.jsdelivr.net/" + url.substring(25)
            url.startsWith("https://raw.githubusercontent.com/") && url.length > 34 -> {
                val owner = url.indexOf('/', 34)
                val repo = if (owner < 0) -1 else url.indexOf('/', owner + 1)
                if (repo <= 0) null else "https://fastly.jsdelivr.net/gh/" + url.substring(34, repo) + '@' + url.substring(repo + 1)
            }
            url.startsWith("https://github.com/") && url.length > 19 -> {
                val blob = url.indexOf("/blob/", 19)
                if (blob < 0 || blob >= url.length - 6) null else "https://fastly.jsdelivr.net/gh/" + url.substring(19, blob) + '@' + url.substring(blob + 6)
            }
            url.startsWith("https://unpkg.com/") && url.length > 18 -> "https://fastly.jsdelivr.net/npm/" + url.substring(18)
            else -> null
        }

        private fun dataBytes(url: String): ByteArray? {
            if (url.length <= 5 || !url.substring(0, 4).equals("data", true)) return null
            val comma = url.indexOf(',')
            if (comma < 0) return null
            return runCatching {
                val decoded = URLDecoder.decode(url.substring(comma + 1), "UTF-8")
                if (url.lastIndexOf(";base64", comma) > 0) Base64.decode(decoded, Base64.DEFAULT) else decoded.toByteArray(Charsets.UTF_8)
            }.getOrNull()
        }

        private fun dataText(url: String): String? = dataBytes(url)?.toString(Charsets.UTF_8)

        private fun valid(url: String, bytes: ByteArray): Boolean {
            val integrity = integrity(url) ?: return true
            return runCatching { hex(MessageDigest.getInstance(integrity.first).digest(bytes)).equals(integrity.second, true) }.getOrDefault(true)
        }

        private fun integrity(url: String): Pair<String, String>? {
            val hash = url.lastIndexOf('#')
            if (hash <= 0 || !url.contains("://")) return null
            for ((name, algorithm, length) in listOf(Triple("md5", "MD5", 32), Triple("sha256", "SHA-256", 64))) {
                val index = url.indexOf(name, hash + 1)
                if (index < 0) continue
                val after = index + name.length
                if (after >= url.length || url[after] != '=' && url[after] != '-') continue
                val start = after + 1
                val end = url.indexOf(',', start).takeIf { it >= 0 } ?: url.indexOf(';', start).takeIf { it >= 0 } ?: url.length
                if (end <= start + 1) return null
                var digest = url.substring(start, end)
                if (digest.length != length) digest = runCatching { hex(Base64.decode(digest.toByteArray(Charsets.UTF_8), Base64.DEFAULT)) }.getOrDefault(digest)
                return algorithm to digest
            }
            return null
        }

        private fun hex(value: ByteArray): String = value.joinToString("") { String.format(Locale.ROOT, "%02x", it.toInt() and 255) }
    }
}
