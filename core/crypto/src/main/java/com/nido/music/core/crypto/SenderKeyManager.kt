package com.nido.music.core.crypto

import java.util.Base64
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
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
        val signingPrivateKey: PrivateKey? = null,
        val signaturePublicKey: PublicKey
    )

    data class SenderKeyDistribution(
        val groupId: String,
        val senderId: String,
        val iteration: Int,
        val chainKeyB64: String,
        val signaturePublicKeyB64: String
    )

    data class GroupEncryptedEnvelope(
        val groupId: String,
        val senderId: String,
        val iteration: Int,
        val ciphertextB64: String,
        val signatureB64: String
    )

    private val secureRandom = SecureRandom()
    private val keyStore = ConcurrentHashMap<String, SenderKeyState>()

    companion object {
        private const val MAX_SKIP = 2000
    }

    /**
     * Initializes a new Sender Key for this user in [groupId].
     * Returns the [SenderKeyDistribution] payload to send to other members via pairwise Signal sessions.
     */
    fun createOrRotateSenderKey(groupId: String, myUserId: String): SenderKeyDistribution {
        val chainKey = ByteArray(32).also { secureRandom.nextBytes(it) }
        val kpg = KeyPairGenerator.getInstance("EC")
        val keyPair = kpg.generateKeyPair()

        val state = SenderKeyState(
            groupId = groupId,
            senderId = myUserId,
            iteration = 0,
            chainKey = chainKey,
            signingPrivateKey = keyPair.private,
            signaturePublicKey = keyPair.public
        )
        val keyId = "${groupId}:${myUserId}"
        keyStore[keyId] = state

        return SenderKeyDistribution(
            groupId = groupId,
            senderId = myUserId,
            iteration = 0,
            chainKeyB64 = Base64.getEncoder().encodeToString(chainKey),
            signaturePublicKeyB64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        )
    }

    /**
     * Ingests a received [SenderKeyDistribution] from another group member after verifying peer identity.
     */
    fun processSenderKeyDistribution(authenticatedSenderId: String, distribution: SenderKeyDistribution) {
        if (authenticatedSenderId != distribution.senderId) {
            throw IllegalArgumentException("Sender ID mismatch: expected $authenticatedSenderId, got ${distribution.senderId}")
        }
        val chainKey = Base64.getDecoder().decode(distribution.chainKeyB64)
        val pubKeyBytes = Base64.getDecoder().decode(distribution.signaturePublicKeyB64)
        val keyFactory = KeyFactory.getInstance("EC")
        val pubKey = keyFactory.generatePublic(X509EncodedKeySpec(pubKeyBytes))

        val state = SenderKeyState(
            groupId = distribution.groupId,
            senderId = distribution.senderId,
            iteration = distribution.iteration,
            chainKey = chainKey,
            signingPrivateKey = null,
            signaturePublicKey = pubKey
        )
        val keyId = "${distribution.groupId}:${distribution.senderId}"
        keyStore[keyId] = state
    }

    /**
     * Encrypts a message payload for [groupId] using the caller's sender key.
     * Ratchets the chain key forward after encryption and signs the envelope.
     */
    fun encryptGroupMessage(groupId: String, myUserId: String, plaintext: ByteArray): GroupEncryptedEnvelope {
        val keyId = "${groupId}:${myUserId}"
        val state = keyStore[keyId] ?: throw IllegalStateException("Sender key not found for $groupId:$myUserId")

        return synchronized(state) {
            val currentIteration = state.iteration
            val messageKey = deriveMessageKey(state.chainKey, currentIteration)
            val iv = ByteArray(12).also { secureRandom.nextBytes(it) }

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(messageKey, "AES"), GCMParameterSpec(128, iv))
            val encrypted = cipher.doFinal(plaintext)

            val buffer = ByteBuffer.allocate(iv.size + encrypted.size)
            buffer.put(iv)
            buffer.put(encrypted)
            val ciphertextB64 = Base64.getEncoder().encodeToString(buffer.array())

            // Ratchet chainKey forward
            state.chainKey = ratchetChainKey(state.chainKey)
            state.iteration += 1

            val signingKey = state.signingPrivateKey
                ?: throw IllegalStateException("Private signing key not available for $myUserId")
            val dataToSign = "$groupId:$myUserId:$currentIteration:$ciphertextB64".toByteArray(Charsets.UTF_8)
            val signer = Signature.getInstance("SHA256withECDSA")
            signer.initSign(signingKey)
            signer.update(dataToSign)
            val signatureB64 = Base64.getEncoder().encodeToString(signer.sign())

            GroupEncryptedEnvelope(
                groupId = groupId,
                senderId = myUserId,
                iteration = currentIteration,
                ciphertextB64 = ciphertextB64,
                signatureB64 = signatureB64
            )
        }
    }

    /**
     * Decrypts a group message from [senderId] in [groupId] after verifying signature.
     */
    fun decryptGroupMessage(
        groupId: String,
        senderId: String,
        iteration: Int,
        ciphertextB64: String,
        signatureB64: String
    ): ByteArray {
        val keyId = "${groupId}:${senderId}"
        val state = keyStore[keyId] ?: throw IllegalStateException("No Sender Key found for $senderId in $groupId")

        return synchronized(state) {
            if (iteration < state.iteration) {
                throw IllegalArgumentException("Iteration $iteration is lower than stored state ${state.iteration}")
            }
            if (iteration - state.iteration > MAX_SKIP) {
                throw IllegalArgumentException("Iteration $iteration exceeds max skip bound")
            }

            // Verify asymmetric signature before mutating state or decrypting
            val dataToSign = "$groupId:$senderId:$iteration:$ciphertextB64".toByteArray(Charsets.UTF_8)
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(state.signaturePublicKey)
            verifier.update(dataToSign)
            if (!verifier.verify(Base64.getDecoder().decode(signatureB64))) {
                throw SecurityException("Invalid signature for group message from $senderId")
            }

            // Derive candidate chain state locally
            var candidateChainKey = state.chainKey
            var currentIt = state.iteration
            while (currentIt < iteration) {
                candidateChainKey = ratchetChainKey(candidateChainKey)
                currentIt++
            }

            val messageKey = deriveMessageKey(candidateChainKey, iteration)
            val rawBytes = Base64.getDecoder().decode(ciphertextB64)
            val buffer = ByteBuffer.wrap(rawBytes)

            val iv = ByteArray(12)
            buffer.get(iv)
            val encryptedBytes = ByteArray(buffer.remaining())
            buffer.get(encryptedBytes)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(messageKey, "AES"), GCMParameterSpec(128, iv))
            val decrypted = cipher.doFinal(encryptedBytes)

            // Ratchet candidate forward past this message
            val nextChainKey = ratchetChainKey(candidateChainKey)

            // Commit state only after successful authentication and decryption
            state.chainKey = nextChainKey
            state.iteration = iteration + 1

            decrypted
        }
    }

    fun decryptGroupMessage(envelope: GroupEncryptedEnvelope): ByteArray =
        decryptGroupMessage(
            groupId = envelope.groupId,
            senderId = envelope.senderId,
            iteration = envelope.iteration,
            ciphertextB64 = envelope.ciphertextB64,
            signatureB64 = envelope.signatureB64
        )

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
