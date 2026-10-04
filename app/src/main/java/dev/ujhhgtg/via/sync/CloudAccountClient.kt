package dev.ujhhgtg.via.sync

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
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

    fun login(username: String, passwordHash: String): LoginResult = when (get(endpoints.user + "name=$username&psw=$passwordHash")) {
        "0" -> LoginResult.SIGNED_IN
        "1" -> LoginResult.PASSWORD_REJECTED
        "2" -> LoginResult.ACCOUNT_CREATED
        else -> LoginResult.UNKNOWN_RESPONSE
    }

    fun pull(username: String, passwordHash: String): String = get(endpoints.sync + "name=$username&psw=$passwordHash")

    /** Section values are already URL-encoded by l9.d.c; account fields remain original raw fields. */
    fun push(username: String, passwordHash: String, encodedSections: Map<String, String?>) =
        post(linkedMapOf<String, String?>().apply { putAll(encodedSections); put("name", username); put("psw", passwordHash) })

    fun requestDeletion(username: String, passwordHash: String) = post(linkedMapOf("name" to username, "psw" to passwordHash, "op" to "delete"))

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.readTimeout = 7_000
            if (connection.responseCode != 200) throw ServiceUnavailable(connection.responseCode)
            val source = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readLines().joinToString("") }
            return source.trim().replace(Regex("<meta.*?>"), "").trim()
        } finally { connection.disconnect() }
    }

    private fun post(fields: Map<String, String?>) {
        val body = fields.entries.joinToString("&") { "${it.key}=${it.value}" }.toByteArray(Charsets.UTF_8)
        val connection = URL(endpoints.update).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 3_000
            connection.readTimeout = 5_000 // original dialog disconnects the request at five seconds
            connection.doInput = true
            connection.doOutput = true
            connection.requestMethod = "POST"
            connection.useCaches = false
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            if (connection.responseCode != 200) throw ServiceUnavailable(connection.responseCode)
        } finally { connection.disconnect() }
    }

    companion object {
        fun passwordHash(value: String): String = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { String.format(Locale.ROOT, "%02x", it.toInt() and 255) }
    }
}
