package dev.ujhhgtg.via.sync

import dev.ujhhgtg.via.common.httpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.content.ByteArrayContent
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale

/** aa.a/b and hb.u: the original legacy account protocol, independent of UI or stored secrets. */
class CloudAccountClient(val endpoints: Endpoints) {
    data class Endpoints(val user: String, val sync: String, val update: String) {
        companion object {
            fun original(global: Boolean): Endpoints {
                val base = if (global) "https://us.app.viayoo.com/api/" else "https://app.viayoo.com/api/"
                return Endpoints(base + "user?", base + "sync?", base + "update")
            }
        }
    }
    enum class LoginResult { SIGNED_IN, PASSWORD_REJECTED, ACCOUNT_CREATED, UNKNOWN_RESPONSE }
    /** z8.u0.r(112) / a4.m(1): the server answered with a status other than 200. */
    class ServiceUnavailable(code: Int) : IOException("HTTP $code")

    suspend fun login(username: String, passwordHash: String): LoginResult = when (get(endpoints.user + "name=$username&psw=$passwordHash")) {
        "0" -> LoginResult.SIGNED_IN
        "1" -> LoginResult.PASSWORD_REJECTED
        "2" -> LoginResult.ACCOUNT_CREATED
        else -> LoginResult.UNKNOWN_RESPONSE
    }

    suspend fun pull(username: String, passwordHash: String): String = get(endpoints.sync + "name=$username&psw=$passwordHash")

    /** Section values are already URL-encoded by l9.d.c; account fields remain original raw fields. */
    suspend fun push(username: String, passwordHash: String, encodedSections: Map<String, String?>) =
        post(linkedMapOf<String, String?>().apply { putAll(encodedSections); put("name", username); put("psw", passwordHash) })

    suspend fun requestDeletion(username: String, passwordHash: String) = post(linkedMapOf("name" to username, "psw" to passwordHash, "op" to "delete"))

    private suspend fun get(url: String): String {
        val response = httpClient.get(url) { timeout { socketTimeoutMillis = 7_000 } }
        if (response.status.value != 200) throw ServiceUnavailable(response.status.value)
        val source = response.bodyAsBytes().toString(Charsets.UTF_8).lines().joinToString("")
        return source.trim().replace(Regex("<meta.*?>"), "").trim()
    }

    private suspend fun post(fields: Map<String, String?>) {
        val body = fields.entries.joinToString("&") { "${it.key}=${it.value}" }.toByteArray(Charsets.UTF_8)
        val response = httpClient.post(endpoints.update) {
            timeout {
                connectTimeoutMillis = 3_000
                socketTimeoutMillis = 5_000 // original dialog disconnects the request at five seconds
            }
            setBody(ByteArrayContent(body, ContentType.Application.FormUrlEncoded))
        }
        if (response.status.value != 200) throw ServiceUnavailable(response.status.value)
    }

    companion object {
        fun passwordHash(value: String): String = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { String.format(Locale.ROOT, "%02x", it.toInt() and 255) }
    }
}
