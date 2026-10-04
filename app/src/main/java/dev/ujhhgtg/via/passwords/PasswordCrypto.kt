package dev.ujhhgtg.via.passwords

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Original z8/w0 wire format: 16-byte IV, then AES-256-CBC/PKCS5Padding ciphertext. */
object PasswordCrypto {
    fun deriveKey(password: String, salt: String): ByteArray = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        .generateSecret(PBEKeySpec(password.toCharArray(), salt.toByteArray(Charsets.UTF_8), 10000, 256)).encoded

    fun encrypt(key: ByteArray, content: ByteArray): ByteArray {
        require(key.size >= 32)
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, 0, 32, "AES"), IvParameterSpec(iv))
        return iv + cipher.doFinal(content)
    }

    fun decrypt(key: ByteArray, content: ByteArray): ByteArray {
        require(key.size >= 32 && content.size >= 16)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, 0, 32, "AES"), IvParameterSpec(content.copyOfRange(0, 16)))
        return cipher.doFinal(content, 16, content.size - 16)
    }

    /** m9/n stores its backup salt in info.enc using this reversible four-byte mask. */
    fun transformBackupInfo(content: ByteArray): ByteArray {
        val mask = intArrayOf(90, 60, 127, -89)
        return ByteArray(content.size) { (content[it].toInt() xor mask[it % 4]).toByte() }
    }
}
