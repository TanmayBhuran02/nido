package com.nido.music.core.crypto

import java.util.Base64
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sender Keys protocol implementation for Group End-to-End Encryption.
 * Allows efficient 1-to-N group encryption without N separate pairwise messages,
 * while maintaining forward secrecy through ratcheting and key rotation.
 */
@Singleton
class SenderKeyManager @Inject constructor() {

    data class SenderKeyState(
        val groupId: String,
        val senderId: String,
        var iteration: Int,
        var chainKey: ByteArray,
        val signingKey: ByteArray
    )

    data class SenderKeyDistribution(
        val groupId: String,
        val senderId: String,
        val iteration: Int,
        val chainKeyB64: String,
        val signingKeyB64: String
    )

    data class GroupEncryptedEnvelope(
        val groupId: String,
        val senderId: String,
        val iteration: Int,
        val ciphertextB64: String
    )

    private val secureRandom = SecureRandom()
    // In-memory session store (groupId:senderId -> SenderKeyState)
    private val keyStore = ConcurrentHashMap<String, SenderKeyState>()

    /**
     * Initializes a new Sender Key for this user in [groupId].
     * Returns the [SenderKeyDistribution] payload to send to other members via pairwise Signal sessions.
     */
    fun createOrRotateSenderKey(groupId: String, myUserId: String): SenderKeyDistribution {
        val chainKey = ByteArray(32).also { secureRandom.nextBytes(it) }
        val signingKey = ByteArray(32).also { secureRandom.nextBytes(it) }
        val state = SenderKeyState(
            groupId = groupId,
            senderId = myUserId,
            iteration = 0,
            chainKey = chainKey,
            signingKey = signingKey
        )
        val keyId = "${groupId}:${myUserId}"
        keyStore[keyId] = state

        return SenderKeyDistribution(
            groupId = groupId,
            senderId = myUserId,
            iteration = 0,
            chainKeyB64 = Base64.getEncoder().encodeToString(chainKey),
            signingKeyB64 = Base64.getEncoder().encodeToString(signingKey)
        )
    }

    /**
     * Ingests a received [SenderKeyDistribution] from another group member.
     */
    fun processSenderKeyDistribution(distribution: SenderKeyDistribution) {
        val chainKey = Base64.getDecoder().decode(distribution.chainKeyB64)
        val signingKey = Base64.getDecoder().decode(distribution.signingKeyB64)
        val state = SenderKeyState(
            groupId = distribution.groupId,
            senderId = distribution.senderId,
            iteration = distribution.iteration,
            chainKey = chainKey,
            signingKey = signingKey
        )
        val keyId = "${distribution.groupId}:${distribution.senderId}"
        keyStore[keyId] = state
    }

    /**
     * Encrypts a message payload for [groupId] using the caller's sender key.
     * Ratchets the chain key forward after encryption.
     */
    fun encryptGroupMessage(groupId: String, myUserId: String, plaintext: ByteArray): GroupEncryptedEnvelope {
        val keyId = "${groupId}:${myUserId}"
        val state = keyStore[keyId] ?: run {
            createOrRotateSenderKey(groupId, myUserId)
            keyStore[keyId]!!
        }

        // Derive message encryption key using HMAC-SHA256 from chainKey
        val messageKey = deriveMessageKey(state.chainKey, state.iteration)
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(messageKey, "AES"), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plaintext)

        // Envelope: [iv (12b) + encrypted]
        val buffer = ByteBuffer.allocate(iv.size + encrypted.size)
        buffer.put(iv)
        buffer.put(encrypted)
        val ciphertextB64 = Base64.getEncoder().encodeToString(buffer.array())

        val currentIteration = state.iteration

        // Ratchet chainKey forward (KDF ratchet)
        state.chainKey = ratchetChainKey(state.chainKey)
        state.iteration += 1

        return GroupEncryptedEnvelope(
            groupId = groupId,
            senderId = myUserId,
            iteration = currentIteration,
            ciphertextB64 = ciphertextB64
        )
    }

    /**
     * Decrypts a group message from [senderId] in [groupId].
     */
    fun decryptGroupMessage(groupId: String, senderId: String, iteration: Int, ciphertextB64: String): ByteArray {
        val keyId = "${groupId}:${senderId}"
        val state = keyStore[keyId] ?: throw IllegalStateException("No Sender Key found for $senderId in $groupId")

        // Catch up chainKey if message is ahead
        while (state.iteration < iteration) {
            state.chainKey = ratchetChainKey(state.chainKey)
            state.iteration += 1
        }

        val messageKey = deriveMessageKey(state.chainKey, iteration)
        val rawBytes = Base64.getDecoder().decode(ciphertextB64)
        val buffer = ByteBuffer.wrap(rawBytes)

        val iv = ByteArray(12)
        buffer.get(iv)
        val encryptedBytes = ByteArray(buffer.remaining())
        buffer.get(encryptedBytes)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(messageKey, "AES"), GCMParameterSpec(128, iv))
        val decrypted = cipher.doFinal(encryptedBytes)

        // Advance ratchet past this message
        state.chainKey = ratchetChainKey(state.chainKey)
        state.iteration += 1

        return decrypted
    }

    /**
     * Clears sender key state for a user (e.g. when removed from group)
     */
    fun removeSenderKey(groupId: String, userId: String) {
        keyStore.remove("${groupId}:${userId}")
    }

    private fun deriveMessageKey(chainKey: ByteArray, iteration: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(chainKey, "HmacSHA256"))
        mac.update(byteArrayOf(0x01))
        mac.update(ByteBuffer.allocate(4).putInt(iteration).array())
        return mac.doFinal()
    }

    private fun ratchetChainKey(chainKey: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(chainKey, "HmacSHA256"))
        mac.update(byteArrayOf(0x02))
        return mac.doFinal()
    }
}
