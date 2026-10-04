package dev.ujhhgtg.via.sync

import android.content.Context
import android.util.Base64
import dev.ujhhgtg.via.data.BrowserPreferences
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** qb.a/f: flags 1 auto-sync, 16 bookmarks, 32 settings, 64 favorites. */
data class SyncConfiguration(
    val id: String,
    val url: String,
    val username: String,
    val password: String,
    val digestAuth: Boolean = false,
    val path: String = "/Via",
    val flags: Int = 81,
) {
    val autoSync: Boolean get() = flags and 1 != 0
    val sections: Int get() = flags and 112
    val baseUrl: String get() = if (url.isEmpty() || url.endsWith('/')) url else "$url/"
    val remotePath: String get() = path.ifEmpty { "/Via" }.removePrefix("/").let { if (it.isEmpty() || it.endsWith('/')) it else "$it/" }
    val isConfigured: Boolean get() = url.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty()
    override fun toString(): String = "SyncConfiguration(id=$id, url=$url, digestAuth=$digestAuth, path=$path, flags=$flags)"
}

/** pb.a, w9.k.w1/e0 and z8.w0: same JSON fields and local AES-CBC credential encoding. */
class SyncConfigurationStore(context: Context) {
    private val app = context.applicationContext
    private val preferences = BrowserPreferences(app)
    val directory: File = (app.getExternalFilesDir("syncing") ?: File(app.filesDir, "syncing")).apply { mkdirs() }
    val tempDirectory: File get() = File(directory, "temp").apply { mkdirs() }
    private val file = File(directory, "storages.txt")
    private fun secret(key: String, length: Int): String = preferences.getString(key).takeUnless { it.isNullOrEmpty() }
        ?: UUID.randomUUID().toString().replace("-", "").take(length).also { preferences.putString(key, it) }

    fun read(): SyncConfiguration? {
        if (!file.isFile) return null
        val json = file.useLines { lines ->
            lines.filter(String::isNotBlank).firstNotNullOfOrNull { runCatching { JSONObject(it) }.getOrNull() }
        } ?: return null
        val id = json.optString("id").takeIf(String::isNotEmpty) ?: return null
        if (json.optInt("type") != 1) return null
        val config = json.optJSONObject("config") ?: return null
        return SyncConfiguration(id, config.optString("url"), decrypt(config.optString("user")), decrypt(config.optString("pass")),
            config.optBoolean("digestAuth"), config.optString("path", "/Via"), json.optInt("flags"))
    }

    fun save(configuration: SyncConfiguration?) {
        val oldId = read()?.id
        if (configuration == null) { tempDirectory.deleteRecursively(); file.delete(); return }
        if (oldId != null && oldId != configuration.id) tempDirectory.deleteRecursively()
        val nested = JSONObject().apply {
            if (configuration.baseUrl.isNotEmpty()) put("url", configuration.baseUrl)
            if (configuration.username.isNotEmpty()) put("user", encrypt(configuration.username))
            if (configuration.password.isNotEmpty()) put("pass", encrypt(configuration.password))
            put("digestAuth", configuration.digestAuth); put("path", configuration.path)
        }
        file.writeText(JSONObject().put("id", configuration.id).put("flags", configuration.flags).put("type", 1).put("config", nested).toString() + "\n")
    }

    private fun encrypt(value: String): String = if (value.isEmpty()) "" else credentialCipher(Cipher.ENCRYPT_MODE, secret("sk", 32), secret("iv", 16), value.toByteArray(Charsets.UTF_8))
        .let { Base64.encodeToString(it, Base64.DEFAULT) }
    private fun decrypt(value: String): String = if (value.isEmpty()) "" else runCatching {
        credentialCipher(Cipher.DECRYPT_MODE, secret("sk", 32), secret("iv", 16), Base64.decode(value, Base64.DEFAULT)).toString(Charsets.UTF_8)
    }.getOrDefault("")

    companion object {
        fun credentialCipher(mode: Int, key: String, iv: String, value: ByteArray): ByteArray = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
            init(mode, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"), IvParameterSpec(iv.toByteArray(Charsets.UTF_8)))
        }.doFinal(value)
    }
}
