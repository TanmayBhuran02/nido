package com.nido.music.core.database.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseKeyManager @Inject constructor(
    private val context: Context
) {
    /**
     * Encrypted prefs backed by Android Keystore — secure but device-bound.
     * Created lazily; may fail after restore if Keystore key was wiped.
     */
    private val encryptedPrefs by lazy {
        runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrNull()
    }

    /**
     * Backup-safe plain file in the no_backup directory is NOT useful for cloud,
     * so we use a file in the regular files dir that IS included in Auto Backup.
     * This allows the DB passphrase to survive reinstall via Google cloud backup.
     *
     * The passphrase is stored as hex in a plain file. While less secure than
     * EncryptedSharedPreferences, it enables data recovery after reinstall.
     * The DB itself is still encrypted at rest via SQLCipher.
     */
    private val backupKeyFile: File
        get() = File(context.filesDir, BACKUP_KEY_FILENAME)

    fun getOrCreatePassphrase(): ByteArray {
        // 1. Try EncryptedSharedPreferences (primary, most secure)
        val fromEncrypted = readFromEncryptedPrefs()
        if (fromEncrypted != null) {
            // Ensure backup copy exists
            writeBackupKey(bytesToHex(fromEncrypted))
            return fromEncrypted
        }

        // 2. Try backup file (survives reinstall via Auto Backup)
        val fromBackup = readBackupKey()
        if (fromBackup != null) {
            val bytes = hexToBytes(fromBackup)
            // Re-store in EncryptedSharedPreferences for future use
            writeToEncryptedPrefs(fromBackup)
            return bytes
        }

        // 3. Generate new passphrase
        val newKey = ByteArray(32)
        SecureRandom().nextBytes(newKey)
        val hexString = bytesToHex(newKey)

        writeToEncryptedPrefs(hexString)
        writeBackupKey(hexString)

        return newKey
    }

    private fun readFromEncryptedPrefs(): ByteArray? {
        return try {
            val hex = encryptedPrefs?.getString(KEY_DB_PASSPHRASE, null) ?: return null
            hexToBytes(hex)
        } catch (_: Exception) {
            // Keystore key was wiped (reinstall) — EncryptedSharedPreferences is broken.
            // Delete the corrupted prefs file so it can be recreated.
            runCatching {
                val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/$ENCRYPTED_PREFS_FILE.xml")
                if (prefsFile.exists()) prefsFile.delete()
            }
            null
        }
    }

    private fun writeToEncryptedPrefs(hexPassphrase: String) {
        try {
            encryptedPrefs?.edit()
                ?.putString(KEY_DB_PASSPHRASE, hexPassphrase)
                ?.commit()
        } catch (_: Exception) {
            // Keystore may be unavailable; backup file is the fallback
        }
    }

    private fun readBackupKey(): String? {
        return try {
            if (backupKeyFile.exists()) backupKeyFile.readText().trim().ifEmpty { null } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun writeBackupKey(hexPassphrase: String) {
        try {
            backupKeyFile.parentFile?.mkdirs()
            backupKeyFile.writeText(hexPassphrase)
        } catch (_: Exception) {
            // Non-fatal — encrypted prefs is the primary store
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) +
                    Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    companion object {
        private const val KEY_DB_PASSPHRASE = "db_passphrase_key"
        private const val ENCRYPTED_PREFS_FILE = "nido_vault_prefs"
        private const val BACKUP_KEY_FILENAME = "datastore/nido_db_key.bin"
    }
}
