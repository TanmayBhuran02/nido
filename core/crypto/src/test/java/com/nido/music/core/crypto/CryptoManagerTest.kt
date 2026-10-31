package com.nido.music.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoManagerTest {

    @Test
    fun testSignalPairwiseEncryptDecrypt() {
        val manager = SignalProtocolManager()
        val plainText = "Hello Nido E2EE Signal!".toByteArray(Charsets.UTF_8)
        val peerId = "alice_recipient_id"

        val envelope = manager.encrypt(peerId, plainText)
        assertNotNull(envelope.ciphertext)
        assertTrue(envelope.ciphertext.isNotEmpty())

        val decrypted = manager.decrypt(peerId, envelope.ciphertext)
        assertArrayEquals(plainText, decrypted)
        assertEquals("Hello Nido E2EE Signal!", String(decrypted, Charsets.UTF_8))
    }

    @Test
    fun testSafetyNumberComputation() {
        val manager = SignalProtocolManager()
        val keyA = "KEY_ALICE_1234567890"
        val keyB = "KEY_BOB_0987654321"

        val safetyNumber1 = manager.computeSafetyNumber(keyA, keyB)
        val safetyNumber2 = manager.computeSafetyNumber(keyA, keyB)

        assertEquals(safetyNumber1, safetyNumber2)
        // Check format: 12 blocks of 5 digits separated by spaces
        val blocks = safetyNumber1.split(" ")
        assertEquals(12, blocks.size)
        assertTrue(blocks.all { it.length == 5 && it.all { c -> c.isDigit() } })
    }

    @Test
    fun testSenderKeyGroupEncryptionAndDistribution() {
        val senderKeyManager = SenderKeyManager()
        val groupId = "group_music_lovers_42"
        val aliceId = "alice_sender"
        val bobId = "bob_receiver"

        // Alice creates sender key
        val distribution = senderKeyManager.createOrRotateSenderKey(groupId, aliceId)
        assertEquals(groupId, distribution.groupId)
        assertEquals(aliceId, distribution.senderId)

        // Bob receives and processes distribution
        val bobSenderKeyManager = SenderKeyManager()
        bobSenderKeyManager.processSenderKeyDistribution(distribution)

        // Alice encrypts a message for the group
        val messageText = "Hey everyone in the group! 🎵"
        val envelope = senderKeyManager.encryptGroupMessage(groupId, aliceId, messageText.toByteArray(Charsets.UTF_8))

        // Bob decrypts the message
        val decryptedBytes = bobSenderKeyManager.decryptGroupMessage(groupId, aliceId, envelope.iteration, envelope.ciphertextB64)
        assertEquals(messageText, String(decryptedBytes, Charsets.UTF_8))

        // Next message ratchets forward
        val message2 = "Second ratcheted message!"
        val envelope2 = senderKeyManager.encryptGroupMessage(groupId, aliceId, message2.toByteArray(Charsets.UTF_8))
        assertEquals(1, envelope2.iteration)

        val decrypted2 = bobSenderKeyManager.decryptGroupMessage(groupId, aliceId, envelope2.iteration, envelope2.ciphertextB64)
        assertEquals(message2, String(decrypted2, Charsets.UTF_8))
    }
}
