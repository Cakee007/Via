package dev.ujhhgtg.via.passwords

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.util.UUID
import javax.crypto.Cipher

/** ya/d and v9/d: RSA-wrapped secret in settings.psk, with settings.pst as its generation salt. */
internal class PasswordKeyStore(context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): ByteArray {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = preferences.getString("psk", null)
        if (store.containsAlias(ALIAS) && !existing.isNullOrEmpty()) {
            val recovered = runCatching {
                val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
                cipher.init(Cipher.DECRYPT_MODE, store.getKey(ALIAS, null) as PrivateKey)
                cipher.doFinal(Base64.decode(existing, Base64.NO_WRAP))
            }.getOrNull()
            if (recovered != null && recovered.size >= 32) {
                val sample = "Hello".toByteArray()
                if (PasswordCrypto.decrypt(recovered, PasswordCrypto.encrypt(recovered, sample)).contentEquals(sample)) return recovered
            }
        }
        if (!store.containsAlias(ALIAS)) generateKeyPair()
        val salt = preferences.getString("pst", "").orEmpty().ifEmpty {
            UUID.randomUUID().toString().replace("-", "").also { preferences.edit {
                putString(
                    "pst",
                    it
                )
            } }
        }
        val secret = PasswordCrypto.deriveKey(UUID.randomUUID().toString().replace("-", ""), salt)
        val refreshedStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, refreshedStore.getCertificate(ALIAS).publicKey)
        preferences.edit {
            putString(
                "psk",
                Base64.encodeToString(cipher.doFinal(secret), Base64.NO_WRAP)
            )
        }
        return secret
    }

    private fun generateKeyPair() {
        val generator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore")
        generator.initialize(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(2048).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1).build())
        generator.generateKeyPair()
    }

    companion object { private const val ALIAS = "dev.ujhhgtg.via.ViaPass" }
}
