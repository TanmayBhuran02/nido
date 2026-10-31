package com.nido.music.core.database.security

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

object DatabaseMigrationHelper {

    private fun cleanupPlaintext(plainDbFile: File) {
        runCatching {
            if (plainDbFile.exists()) plainDbFile.delete()
            val wal = File(plainDbFile.path + "-wal")
            if (wal.exists()) wal.delete()
            val shm = File(plainDbFile.path + "-shm")
            if (shm.exists()) shm.delete()
            val journal = File(plainDbFile.path + "-journal")
            if (journal.exists()) journal.delete()
        }
    }

    fun migrateToEncryptedIfNecessary(
        context: Context,
        plainDbName: String,
        encryptedDbName: String,
        passphrase: ByteArray
    ): SupportSQLiteOpenHelper.Factory {
        System.loadLibrary("sqlcipher")
        val plainDbFile = context.getDatabasePath(plainDbName)
        val encryptedDbFile = context.getDatabasePath(encryptedDbName)

        val hexPassphrase = passphrase.joinToString("") { "%02x".format(it) }
        val rawKeySpec = "x'$hexPassphrase'"
        val keyBytes = rawKeySpec.toByteArray(Charsets.US_ASCII)

        if (encryptedDbFile.exists()) {
            val isReadable = runCatching {
                val db = SQLiteDatabase.openDatabase(
                    encryptedDbFile.absolutePath,
                    keyBytes,
                    null,
                    SQLiteDatabase.OPEN_READWRITE,
                    null
                )
                val cursor = db.rawQuery("SELECT count(*) FROM sqlite_master;", null)
                cursor.moveToFirst()
                cursor.close()
                db.close()
                true
            }.getOrDefault(false)

            if (!isReadable) {
                cleanupPlaintext(encryptedDbFile)
            }
        }

        if (plainDbFile.exists() && encryptedDbFile.exists()) {
            val isEncryptedValid = runCatching {
                val db = SQLiteDatabase.openDatabase(
                    encryptedDbFile.absolutePath,
                    keyBytes,
                    null,
                    SQLiteDatabase.OPEN_READWRITE,
                    null
                )
                val cursor = db.rawQuery("SELECT count(*) FROM sqlite_master;", null)
                val hasExpectedContents = cursor.moveToFirst() && cursor.getInt(0) > 0
                cursor.close()
                db.close()
                hasExpectedContents
            }.getOrDefault(false)

            if (isEncryptedValid) {
                cleanupPlaintext(plainDbFile)
            } else {
                error("Encrypted database validation failed; retaining plaintext database")
            }
        } else if (plainDbFile.exists() && !encryptedDbFile.exists()) {
            val tempEncryptedFile = File(encryptedDbFile.parentFile, "$encryptedDbName.tmp")
            if (tempEncryptedFile.exists()) {
                tempEncryptedFile.delete()
            }

            var plainDb: SQLiteDatabase? = null
            var exportSuccess = false
            try {
                plainDb = SQLiteDatabase.openDatabase(
                    plainDbFile.absolutePath,
                    "",
                    null,
                    SQLiteDatabase.OPEN_READWRITE,
                    null
                )
                // Read user_version from plaintext database
                var sourceVersion = 0
                val versionCursor = plainDb.rawQuery("PRAGMA user_version;", null)
                if (versionCursor.moveToFirst()) {
                    sourceVersion = versionCursor.getInt(0)
                }
                versionCursor.close()

                plainDb.rawExecSQL("ATTACH DATABASE '${tempEncryptedFile.absolutePath}' AS encrypted KEY \"$rawKeySpec\";")
                plainDb.rawExecSQL("SELECT sqlcipher_export('encrypted');")
                // Preserve user_version on encrypted database schema
                plainDb.rawExecSQL("PRAGMA encrypted.user_version = $sourceVersion;")
                plainDb.rawExecSQL("DETACH DATABASE encrypted;")
                exportSuccess = true
            } finally {
                plainDb?.close()
            }

            if (exportSuccess && tempEncryptedFile.exists() && tempEncryptedFile.length() > 0) {
                if (tempEncryptedFile.renameTo(encryptedDbFile)) {
                    cleanupPlaintext(plainDbFile)
                } else {
                    tempEncryptedFile.delete()
                    error("Failed to promote temporary encrypted database")
                }
            } else {
                tempEncryptedFile.delete()
                error("Failed to export plain database to encrypted database")
            }
        }

        return SupportOpenHelperFactory(keyBytes)
    }
}
