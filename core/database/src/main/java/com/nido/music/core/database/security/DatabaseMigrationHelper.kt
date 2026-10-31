package com.nido.music.core.database.security

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

object DatabaseMigrationHelper {

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

        if (plainDbFile.exists() && !encryptedDbFile.exists()) {
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
                plainDb.rawExecSQL("ATTACH DATABASE '${tempEncryptedFile.absolutePath}' AS encrypted KEY \"$rawKeySpec\";")
                plainDb.rawExecSQL("SELECT sqlcipher_export('encrypted');")
                plainDb.rawExecSQL("DETACH DATABASE encrypted;")
                exportSuccess = true
            } finally {
                plainDb?.close()
            }

            if (exportSuccess && tempEncryptedFile.exists() && tempEncryptedFile.length() > 0) {
                if (tempEncryptedFile.renameTo(encryptedDbFile)) {
                    plainDbFile.delete()
                    File(plainDbFile.path + "-wal").delete()
                    File(plainDbFile.path + "-shm").delete()
                    File(plainDbFile.path + "-journal").delete()
                } else {
                    tempEncryptedFile.delete()
                    error("Failed to promote temporary encrypted database")
                }
            } else {
                tempEncryptedFile.delete()
                error("Failed to export plain database to encrypted database")
            }
        }

        return SupportOpenHelperFactory(rawKeySpec.toByteArray(Charsets.US_ASCII))
    }
}
