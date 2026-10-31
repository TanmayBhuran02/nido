package com.nido.music.feature.chat.stickers

import android.content.Context
import android.net.Uri
import com.nido.music.feature.chat.data.StickerEntity
import com.nido.music.feature.chat.data.StickerPackEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class WhatsAppStickerContents(
    val android_play_store_link: String? = null,
    val ios_app_store_link: String? = null,
    val sticker_packs: List<WhatsAppStickerPackJson>
)

@Serializable
data class WhatsAppStickerPackJson(
    val identifier: String,
    val name: String,
    val publisher: String,
    val tray_image_file: String? = null,
    val animated_sticker_pack: Boolean = false,
    val stickers: List<WhatsAppStickerJson>
)

@Serializable
data class WhatsAppStickerJson(
    val image_file: String,
    val emojis: List<String> = emptyList()
)

data class ImportedStickerPack(
    val pack: StickerPackEntity,
    val stickers: List<StickerEntity>
)

/**
 * Importer for WhatsApp-compatible sticker packs (.wastickers zip or contents.json structure).
 * Also handles share intents exported by Sticker.ly and similar apps.
 */
@Singleton
class WhatsAppStickerImporter @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    fun parseContentsJson(jsonString: String): WhatsAppStickerContents {
        return json.decodeFromString<WhatsAppStickerContents>(jsonString)
    }

    /**
     * Imports a .wastickers archive from an input stream into app internal files directory.
     */
    fun importFromZip(context: Context, inputStream: InputStream): List<ImportedStickerPack> {
        val outputDir = File(context.filesDir, "stickers")
        if (!outputDir.exists()) outputDir.mkdirs()

        var contentsJsonString: String? = null
        val extractedFiles = mutableMapOf<String, File>()

        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val entryName = File(entry.name).name
                    if (entryName.equals("contents.json", ignoreCase = true)) {
                        contentsJsonString = zis.bufferedReader(Charsets.UTF_8).readText()
                    } else if (entryName.endsWith(".webp", ignoreCase = true) || entryName.endsWith(".png", ignoreCase = true)) {
                        val targetFile = File(outputDir, entryName)
                        targetFile.outputStream().use { fos ->
                            zis.copyTo(fos)
                        }
                        extractedFiles[entryName] = targetFile
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        if (contentsJsonString == null) {
            return emptyList()
        }

        val manifest = parseContentsJson(contentsJsonString!!)
        val results = mutableListOf<ImportedStickerPack>()

        for (packJson in manifest.sticker_packs) {
            val packEntity = StickerPackEntity(
                id = packJson.identifier,
                name = packJson.name,
                publisher = packJson.publisher,
                trayImageUri = packJson.tray_image_file?.let { extractedFiles[it]?.toURI()?.toString() },
                animated = packJson.animated_sticker_pack
            )

            val stickerEntities = packJson.stickers.mapIndexed { idx, sJson ->
                val localFile = extractedFiles[sJson.image_file]
                StickerEntity(
                    id = "${packJson.identifier}_$idx",
                    packId = packJson.identifier,
                    imageUri = localFile?.toURI()?.toString() ?: sJson.image_file,
                    emojis = sJson.emojis.joinToString(",")
                )
            }

            results.add(ImportedStickerPack(pack = packEntity, stickers = stickerEntities))
        }

        return results
    }
}
