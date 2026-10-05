package dev.ujhhgtg.via.browser.script

import android.util.Base64
import android.webkit.WebSettings
import android.webkit.WebView
import dev.ujhhgtg.via.common.FINAL_URL_HEADER
import dev.ujhhgtg.via.common.applicationIoScope
import dev.ujhhgtg.via.common.finalUrl
import dev.ujhhgtg.via.common.httpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.content.ByteArrayContent
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.SocketTimeoutException
import java.util.Locale

/** o5.b/c GM request transport; callbacks use the original i6.j0 conversion code. */
class ScriptHttpRequest(private val view: WebView, details: String) {
    private val request = runCatching { JSONObject(details) }.getOrNull()
    private val defaultAgent = WebSettings.getDefaultUserAgent(view.context)

    fun start() {
        val details = request ?: return
        if (!details.has("url")) return
        // A synchronous request blocks the calling JavaScript bridge thread, as the original did.
        if (details.optBoolean("synchronous", false)) runBlocking { execute(details) }
        else applicationIoScope.launch { execute(details) }
    }

    private suspend fun execute(details: JSONObject) {
        val response = JSONObject()
        details.optJSONObject("context")?.let { response.put("context", it) }
        try {
            val data = details.optString("data").takeIf(String::isNotEmpty)?.toByteArray(Charsets.UTF_8)
            // HttpURLConnection turned a GET with a request body into a POST.
            val method = details.optString("method", "GET").uppercase(Locale.ROOT).let { if (data != null && it == "GET") "POST" else it }
            val target = details.getString("url")
            response.put("readyState", 1)
            dispatch(details, "onloadstart", response, readyState = true)
            val timeoutMillis = details.optInt("timeout", 0).toLong().takeIf { it > 0 } ?: HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            val statement = httpClient.prepareRequest {
                url(target)
                this.method = HttpMethod.parse(method)
                timeout { connectTimeoutMillis = timeoutMillis; socketTimeoutMillis = timeoutMillis }
                val user = details.optString("user")
                val password = details.optString("password")
                if (user.isNotEmpty() && password.isNotEmpty()) header("Authorization", "Basic " + Base64.encodeToString("$user:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
                var cookie: String? = null
                var agent: String? = null
                var contentType: String? = if (data == null) null
                    else if (details.optString("data").indexOf('=') > 0) "application/x-www-form-urlencoded;charset=UTF-8" else "text/plain;charset=UTF-8"
                details.optJSONObject("headers")?.let { headers ->
                    headers.keys().forEach { key ->
                        when {
                            key.equals("cookie", true) -> cookie = headers.getString(key)
                            key.equals("user-agent", true) -> agent = headers.getString(key)
                            key.equals("content-type", true) -> contentType = headers.getString(key)
                            // OkHttp computes the body length itself.
                            key.equals("content-length", true) -> Unit
                            else -> header(key, headers.getString(key))
                        }
                    }
                }
                header("User-Agent", agent ?: defaultAgent)
                if (!details.optBoolean("anonymous", false)) {
                    val cookies = listOfNotNull(cookie, details.optString("cookie").takeIf(String::isNotEmpty)).filter(String::isNotEmpty)
                    if (cookies.isNotEmpty()) header("Cookie", cookies.joinToString(";"))
                }
                details.optString("overrideMimeType").takeIf(String::isNotEmpty)?.let { contentType = it }
                val type = contentType?.let { runCatching { ContentType.parse(it) }.getOrNull() }
                if (data != null) setBody(ByteArrayContent(data, type))
                else contentType?.let { header("Content-Type", it) }
            }
            statement.execute { http ->
                if (data != null) details.optJSONObject("upload")?.optString("onprogress")?.takeIf(String::isNotEmpty)?.let { callback ->
                    deliver(arrayOf(callback), JSONObject().put("lengthComputable", true).put("loaded", data.size).put("total", data.size))
                }
                val status = http.status.value
                response.put("status", status).put("statusText", http.status.description).put("finalUrl", http.finalUrl).put("readyState", 2)
                dispatch(details, "onreadystatechange", response)
                // The original reports non-2xx responses through onerror, rather than onload.
                if (status !in 200..299) { dispatch(details, "onerror", response, readyState = true); return@execute }
                response.put("responseHeaders", buildString {
                    http.headers.forEach { name, values ->
                        if (!name.equals(FINAL_URL_HEADER, true)) append(name.lowercase(Locale.ROOT)).append(": ").append(values.joinToString("; ")).append("\r\n")
                    }
                })
                val length = http.headers["Content-Length"]?.toIntOrNull() ?: -1
                response.put("readyState", 3)
                dispatch(details, "onreadystatechange", response)
                val responseType = details.optString("responseType")
                if (method != "HEAD") {
                    val binary = responseType in setOf("blob", "stream", "arraybuffer")
                    var loaded = 0
                    var lastProgress = 0
                    fun progress() {
                        val event = JSONObject().put("lengthComputable", length > 0).put("loaded", loaded).put("total", if (length > 0) length else -1)
                        arrayOf("finalUrl", "readyState", "status", "statusText", "responseHeaders").forEach { key -> if (response.has(key)) event.put(key, response.get(key)) }
                        dispatch(details, "onprogress", event)
                    }
                    val contentType = http.headers["Content-Type"]
                    val channel = http.bodyAsChannel()
                    if (binary) {
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(6144)
                        while (true) {
                            val count = channel.readAvailable(buffer)
                            if (count <= 0) break
                            if (loaded >= MAX_RESPONSE) throw ResponseTooLarge()
                            output.write(buffer, 0, count); loaded += count
                            if (length > 0 && loaded - lastProgress > 32768) { progress(); lastProgress = loaded }
                        }
                        val type = contentType?.substringBefore(';') ?: "text/plain"
                        response.put("responseDataUrl", "data:$type;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP))
                    } else {
                        val encoding = http.headers["Content-Encoding"] ?: Regex("charset=([^;]+)", RegexOption.IGNORE_CASE).find(contentType.orEmpty())?.groupValues?.get(1)
                        val output = StringBuilder()
                        // s5.f consumes Unicode BOMs before choosing the explicit/default encoding.
                        ScriptResources.bomReader(channel.toInputStream(currentCoroutineContext().job), encoding).use { reader ->
                            val buffer = CharArray(4096)
                            while (true) {
                                val count = reader.read(buffer)
                                if (count <= 0) break
                                if (loaded >= MAX_RESPONSE) throw ResponseTooLarge()
                                output.appendRange(buffer, 0, count); loaded += count
                                if (loaded - lastProgress > 32768) { progress(); lastProgress = loaded }
                            }
                        }
                        response.put("responseText", output.toString())
                    }
                    progress()
                    response.put("responseType", responseType.ifEmpty { "undefined" })
                }
                response.put("readyState", 4)
                dispatch(details, "onload", response, readyState = true)
            }
        } catch (error: CancellationException) { throw error }
        catch (_: SocketTimeoutException) { dispatch(details, "ontimeout", null) }
        catch (_: ConnectTimeoutException) { dispatch(details, "ontimeout", null) }
        catch (_: HttpRequestTimeoutException) { dispatch(details, "ontimeout", null) }
        catch (_: Exception) {
            response.put("readyState", 4)
            dispatch(details, "onerror", response, readyState = true)
        }
    }

    private fun dispatch(details: JSONObject, callback: String, response: JSONObject?, readyState: Boolean = false) {
        val callbacks = if (readyState) arrayOf(details.optString("onreadystatechange"), details.optString(callback)) else arrayOf(details.optString(callback))
        deliver(callbacks.filter(String::isNotEmpty).toTypedArray(), response)
    }

    private fun deliver(callbacks: Array<String>, response: JSONObject?) {
        if (callbacks.isEmpty()) return
        val script = GmApiSource.deliverCallbacks(callbacks, response?.let { JSONObject.quote(it.toString()) }) ?: return
        view.post { view.evaluateJavascript(script, null) }
    }

    private class ResponseTooLarge : Exception()
    companion object { private const val MAX_RESPONSE = 8_388_608 }
}
