package com.nido.music.feature.chat.stickers

import android.content.Context
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
     * Extracts files into isolated pack subdirectories and skips missing sticker entries.
     */
    fun importFromZip(context: Context, inputStream: InputStream): List<ImportedStickerPack> {
        val tempDir = File(context.cacheDir, "sticker_staging_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val target = File(tempDir, entry.name)
                        target.parentFile?.mkdirs()
                        target.outputStream().use { zis.copyTo(it) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val contentsFile = tempDir.walkTopDown().firstOrNull { it.name.equals("contents.json", ignoreCase = true) }
                ?: return emptyList()

            val manifest = parseContentsJson(contentsFile.readText(Charsets.UTF_8))
            val baseStickersDir = File(context.filesDir, "stickers")
            if (!baseStickersDir.exists()) baseStickersDir.mkdirs()

            val results = mutableListOf<ImportedStickerPack>()

            for (packJson in manifest.sticker_packs) {
                val packDir = File(baseStickersDir, packJson.identifier)
                if (!packDir.exists()) packDir.mkdirs()

                val trayFile = packJson.tray_image_file?.let { name ->
                    tempDir.walkTopDown().firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { src ->
                        val dest = File(packDir, src.name)
                        src.copyTo(dest, overwrite = true)
                        dest
                    }
                }

                val stickerEntities = mutableListOf<StickerEntity>()
                var idx = 0
                for (sJson in packJson.stickers) {
                    val src = tempDir.walkTopDown().firstOrNull { it.name.equals(sJson.image_file, ignoreCase = true) }
                    if (src != null && src.exists()) {
                        val dest = File(packDir, src.name)
                        src.copyTo(dest, overwrite = true)
                        stickerEntities.add(
                            StickerEntity(
                                id = "${packJson.identifier}_$idx",
                                packId = packJson.identifier,
                                imageUri = dest.toURI().toString(),
                                emojis = sJson.emojis.joinToString(",")
                            )
                        )
                        idx++
                    }
                }

                val packEntity = StickerPackEntity(
                    id = packJson.identifier,
                    name = packJson.name,
                    publisher = packJson.publisher,
                    trayImageUri = trayFile?.toURI()?.toString(),
                    animated = packJson.animated_sticker_pack
                )

                results.add(ImportedStickerPack(pack = packEntity, stickers = stickerEntities))
            }

            return results
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
