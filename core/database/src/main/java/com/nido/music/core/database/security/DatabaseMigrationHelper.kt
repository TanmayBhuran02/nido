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

        if (plainDbFile.exists() && !encryptedDbFile.exists()) {
            val plainDb = SQLiteDatabase.openDatabase(
                plainDbFile.absolutePath,
                "",
                null,
                SQLiteDatabase.OPEN_READWRITE,
                null
            )

            val hexPassphrase = passphrase.joinToString("") { "%02x".format(it) }
            plainDb.rawExecSQL("ATTACH DATABASE '${encryptedDbFile.absolutePath}' AS encrypted KEY \"x'$hexPassphrase'\";")
            plainDb.rawExecSQL("SELECT sqlcipher_export('encrypted');")
            plainDb.rawExecSQL("DETACH DATABASE encrypted;")
            plainDb.close()

            plainDbFile.delete()
        }

        return SupportOpenHelperFactory(passphrase)
    }
}
