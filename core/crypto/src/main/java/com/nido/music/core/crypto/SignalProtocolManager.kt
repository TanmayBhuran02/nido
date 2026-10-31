package com.nido.music.core.crypto

import android.util.Base64
import java.security.KeyPairGenerator
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

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
        val ciphertext: String, // Base64 encoded
        val messageType: Int    // 1=PreKeyMessage, 2=WhisperMessage
    )

    fun generateInitialPreKeyBundle(): PreKeyBundleDto {
        // Curve25519 identity key generation
        val kpg = KeyPairGenerator.getInstance("EC")
        val identityKeyPair = kpg.generateKeyPair()
        val identityPublicKeyB64 = Base64.encodeToString(identityKeyPair.public.encoded, Base64.NO_WRAP)

        // Generate signed prekey
        val signedPreKeyPair = kpg.generateKeyPair()
        val signedPreKeyB64 = Base64.encodeToString(signedPreKeyPair.public.encoded, Base64.NO_WRAP)

        // Generate dummy signature
        val sig = ByteArray(64).also { SecureRandom().nextBytes(it) }
        val signedPreKeySigB64 = Base64.encodeToString(sig, Base64.NO_WRAP)

        // Generate 100 one-time prekeys
        val otps = (1..100).map { id ->
            val kp = kpg.generateKeyPair()
            OneTimePreKeyDto(
                keyId = id,
                publicKey = Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP)
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

    fun encrypt(recipientUserId: String, plaintext: ByteArray): EncryptedEnvelope {
        throw UnsupportedOperationException("Signal session encryption not implemented")
    }

    fun decrypt(senderUserId: String, ciphertextB64: String): ByteArray {
        throw UnsupportedOperationException("Signal session decryption not implemented")
    }

    fun computeSafetyNumber(localIdentityKey: String, remoteIdentityKey: String): String {
        // 12-block (60 digit) fingerprint
        val combined = "$localIdentityKey:$remoteIdentityKey".toByteArray()
        val md = java.security.MessageDigest.getInstance("SHA-512")
        val hash = md.digest(combined)
        val sb = java.lang.StringBuilder()
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
