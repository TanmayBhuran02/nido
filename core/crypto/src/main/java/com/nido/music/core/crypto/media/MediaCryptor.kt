package com.nido.music.core.crypto.media

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaCryptor @Inject constructor() {

    data class EncryptionResult(
        val key: ByteArray,
        val iv: ByteArray,
        val sha256: String,
        val sizeBytes: Long
    )

    fun encryptStream(input: InputStream, output: OutputStream): EncryptionResult {
        val key = ByteArray(32)
        val iv = ByteArray(12)
        SecureRandom().apply {
            nextBytes(key)
            nextBytes(iv)
        }

        val secretKey = SecretKeySpec(key, "AES")
        val spec = GCMParameterSpec(128, iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var totalBytes = 0L

        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            val encryptedBytes = cipher.update(buffer, 0, bytesRead)
            if (encryptedBytes != null && encryptedBytes.isNotEmpty()) {
                output.write(encryptedBytes)
                digest.update(encryptedBytes)
                totalBytes += encryptedBytes.size
            }
        }

        val finalBytes = cipher.doFinal()
        if (finalBytes != null && finalBytes.isNotEmpty()) {
            output.write(finalBytes)
            digest.update(finalBytes)
            totalBytes += finalBytes.size
        }

        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        return EncryptionResult(key, iv, sha256, totalBytes)
    }

    fun decryptStream(input: InputStream, output: OutputStream, key: ByteArray, iv: ByteArray) {
        val secretKey = SecretKeySpec(key, "AES")
        val spec = GCMParameterSpec(128, iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            val decryptedBytes = cipher.update(buffer, 0, bytesRead)
            if (decryptedBytes != null && decryptedBytes.isNotEmpty()) {
                output.write(decryptedBytes)
            }
        }

        val finalBytes = cipher.doFinal()
        if (finalBytes != null && finalBytes.isNotEmpty()) {
            output.write(finalBytes)
        }
    }
}
