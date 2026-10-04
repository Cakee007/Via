package dev.ujhhgtg.via.browser.script

import android.util.Base64
import android.webkit.WebSettings
import android.webkit.WebView
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Locale

/** o5.b/c GM request transport; callbacks use the original i6.j0 conversion code. */
class ScriptHttpRequest(private val view: WebView, details: String) {
    private val request = runCatching { JSONObject(details) }.getOrNull()
    private val defaultAgent = WebSettings.getDefaultUserAgent(view.context)

    fun start() {
        val details = request ?: return
        if (!details.has("url")) return
        if (details.optBoolean("synchronous", false)) execute(details)
        else Thread({ execute(details) }, "via-gm-request").start()
    }

    private fun execute(details: JSONObject) {
        val response = JSONObject()
        details.optJSONObject("context")?.let { response.put("context", it) }
        var connection: HttpURLConnection? = null
        try {
            val method = details.optString("method", "GET").uppercase(Locale.ROOT)
            connection = URL(details.getString("url")).openConnection() as HttpURLConnection
            connection.requestMethod = method
            response.put("readyState", 1)
            dispatch(details, "onloadstart", response, readyState = true)
            val data = details.optString("data").takeIf(String::isNotEmpty)?.toByteArray(Charsets.UTF_8)
            if (data != null) {
                connection.doOutput = true
                connection.doInput = true
                connection.setRequestProperty("Content-Length", data.size.toString())
                connection.setRequestProperty("Content-Type", if (details.optString("data").indexOf('=') > 0) "application/x-www-form-urlencoded;charset=UTF-8" else "text/plain;charset=UTF-8")
            }
            val user = details.optString("user")
            val password = details.optString("password")
            if (user.isNotEmpty() && password.isNotEmpty()) connection.setRequestProperty("Authorization", "Basic " + Base64.encodeToString("$user:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            var cookie: String? = null
            var agent: String? = null
            details.optJSONObject("headers")?.let { headers ->
                headers.keys().forEach { key ->
                    when {
                        key.equals("cookie", true) -> cookie = headers.getString(key)
                        key.equals("user-agent", true) -> agent = headers.getString(key)
                        else -> connection.setRequestProperty(key, headers.getString(key))
                    }
                }
            }
            connection.setRequestProperty("User-Agent", agent ?: defaultAgent)
            if (!details.optBoolean("anonymous", false)) {
                val cookies = listOfNotNull(cookie, details.optString("cookie").takeIf(String::isNotEmpty)).filter(String::isNotEmpty)
                if (cookies.isNotEmpty()) connection.setRequestProperty("Cookie", cookies.joinToString(";"))
            }
            details.optString("overrideMimeType").takeIf(String::isNotEmpty)?.let { connection.setRequestProperty("Content-Type", it) }
            connection.connectTimeout = details.optInt("timeout", 0)
            connection.readTimeout = details.optInt("timeout", 0)
            connection.connect()
            if (data != null) {
                connection.outputStream.use { it.write(data); it.flush() }
                details.optJSONObject("upload")?.optString("onprogress")?.takeIf(String::isNotEmpty)?.let { callback ->
                    deliver(arrayOf(callback), JSONObject().put("lengthComputable", true).put("loaded", data.size).put("total", data.size))
                }
            }
            val status = connection.responseCode
            response.put("status", status).put("statusText", connection.responseMessage).put("finalUrl", connection.url.toString()).put("readyState", 2)
            dispatch(details, "onreadystatechange", response)
            // The original reports non-2xx responses through onerror, rather than onload.
            if (status !in 200..299) { dispatch(details, "onerror", response, readyState = true); return }
            response.put("responseHeaders", connection.headerFields.entries.filter { !it.key.isNullOrEmpty() }.joinToString("") {
                it.key.lowercase(Locale.ROOT) + ": " + it.value.joinToString("; ") + "\r\n"
            })
            val length = connection.contentLength
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
                if (binary) {
                    val output = ByteArrayOutputStream()
                    connection.inputStream.buffered().use { input ->
                        val buffer = ByteArray(6144)
                        while (true) {
                            val count = input.read(buffer)
                            if (count <= 0) break
                            if (loaded >= MAX_RESPONSE) throw ResponseTooLarge()
                            output.write(buffer, 0, count); loaded += count
                            if (length > 0 && loaded - lastProgress > 32768) { progress(); lastProgress = loaded }
                        }
                    }
                    val type = connection.contentType?.substringBefore(';') ?: "text/plain"
                    response.put("responseDataUrl", "data:$type;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP))
                } else {
                    val encoding = connection.contentEncoding ?: Regex("charset=([^;]+)", RegexOption.IGNORE_CASE).find(connection.contentType.orEmpty())?.groupValues?.get(1)
                    val output = StringBuilder()
                    // s5.f consumes Unicode BOMs before choosing the explicit/default encoding.
                    ScriptResources.bomReader(connection.inputStream, encoding).use { reader ->
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
        } catch (_: SocketTimeoutException) {
            dispatch(details, "ontimeout", null)
        } catch (_: Exception) {
            response.put("readyState", 4)
            dispatch(details, "onerror", response, readyState = true)
        } finally { connection?.disconnect() }
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
