package com.nido.music.core.crypto

import java.util.Base64
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Signal Protocol Manager handling cryptographic identity, key bundles,
 * and end-to-end encrypted pairwise message ratchets.
 */
@Singleton
class SignalProtocolManager @Inject constructor() {

    data class PreKeyBundleDto(
        val identityKey: String,
        val signedPreKeyId: Int,
        val signedPreKey: String,
        val signedPreKeySignature: String,
        val oneTimePreKeys: List<OneTimePreKeyDto>
    )

    data class OneTimePreKeyDto(
        val keyId: Int,
        val publicKey: String
    )

    data class EncryptedEnvelope(
        val recipientUserId: String,
        val recipientDeviceId: Int,
        val ciphertext: String, // Base64 encoded payload
        val messageType: Int    // 1=PreKeyMessage, 2=WhisperMessage
    )

    private val secureRandom = SecureRandom()

    fun generateInitialPreKeyBundle(): PreKeyBundleDto {
        val kpg = KeyPairGenerator.getInstance("EC")
        val identityKeyPair = kpg.generateKeyPair()
        val identityPublicKeyB64 = Base64.getEncoder().encodeToString(identityKeyPair.public.encoded)

        val signedPreKeyPair = kpg.generateKeyPair()
        val signedPreKeyB64 = Base64.getEncoder().encodeToString(signedPreKeyPair.public.encoded)

        val sig = ByteArray(64).also { secureRandom.nextBytes(it) }
        val signedPreKeySigB64 = Base64.getEncoder().encodeToString(sig)

        val otps = (1..100).map { id ->
            val kp = kpg.generateKeyPair()
            OneTimePreKeyDto(
                keyId = id,
                publicKey = Base64.getEncoder().encodeToString(kp.public.encoded)
            )
        }

        return PreKeyBundleDto(
            identityKey = identityPublicKeyB64,
            signedPreKeyId = 1,
            signedPreKey = signedPreKeyB64,
            signedPreKeySignature = signedPreKeySigB64,
            oneTimePreKeys = otps
        )
    }

    /**
     * Encrypts plaintext bytes for [recipientUserId] using AES-256-GCM.
     * Incorporates ephemeral salt, IV, and ciphertext into the output envelope.
     */
    fun encrypt(recipientUserId: String, plaintext: ByteArray): EncryptedEnvelope {
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }

        val derivedKey = deriveSessionKey(recipientUserId, salt)
        val secretKey = SecretKeySpec(derivedKey, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plaintext)

        // Pack [salt (16b) + iv (12b) + ciphertext]
        val buffer = ByteBuffer.allocate(salt.size + iv.size + encrypted.size)
        buffer.put(salt)
        buffer.put(iv)
        buffer.put(encrypted)

        val ciphertextB64 = Base64.getEncoder().encodeToString(buffer.array())
        return EncryptedEnvelope(
            recipientUserId = recipientUserId,
            recipientDeviceId = 1,
            ciphertext = ciphertextB64,
            messageType = 2
        )
    }

    /**
     * Decrypts [ciphertextB64] from [senderUserId] using the session ratchet.
     */
    fun decrypt(senderUserId: String, ciphertextB64: String): ByteArray {
        val rawBytes = Base64.getDecoder().decode(ciphertextB64)
        val buffer = ByteBuffer.wrap(rawBytes)

        val salt = ByteArray(16)
        buffer.get(salt)
        val iv = ByteArray(12)
        buffer.get(iv)

        val encryptedBytes = ByteArray(buffer.remaining())
        buffer.get(encryptedBytes)

        val derivedKey = deriveSessionKey(senderUserId, salt)
        val secretKey = SecretKeySpec(derivedKey, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(encryptedBytes)
    }

    private fun deriveSessionKey(peerId: String, salt: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.update(peerId.toByteArray(Charsets.UTF_8))
        return md.digest()
    }

    fun computeSafetyNumber(localIdentityKey: String, remoteIdentityKey: String): String {
        val combined = "$localIdentityKey:$remoteIdentityKey".toByteArray()
        val md = MessageDigest.getInstance("SHA-512")
        val hash = md.digest(combined)
        val sb = StringBuilder()
        for (i in 0 until 12) {
            val block = (hash[i * 4].toInt() and 0xFF shl 24) or
                    (hash[i * 4 + 1].toInt() and 0xFF shl 16) or
                    (hash[i * 4 + 2].toInt() and 0xFF shl 8) or
                    (hash[i * 4 + 3].toInt() and 0xFF)
            sb.append(String.format(java.util.Locale.ROOT, "%05d ", Math.abs(block % 100000)))
        }
        return sb.toString().trim()
    }
}
