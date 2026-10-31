package com.nido.music.core.network.spotdl

import com.nido.music.core.network.MusicSourceProvider
import com.nido.music.core.network.model.SearchResult
import com.nido.music.core.network.model.TrackDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SpotDLSong(
    val name: String = "",
    val artists: List<String> = emptyList(),
    val artist: String = "",
    @SerialName("album_name") val albumName: String? = null,
    val duration: Double = 0.0,
    val year: Int? = null,
    val date: String? = null,
    @SerialName("song_id") val songId: String = "",
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null
)

@Serializable
data class SpotDLDownloadResult(
    val success: Boolean = false,
    val message: String? = null,
    val path: String? = null
)

interface SpotDLApi {
    @GET("api/song/url")
    suspend fun getSongByUrl(
        @Query("url") url: String
    ): SpotDLSong

    @GET("api/download/url")
    suspend fun downloadByUrl(
        @Query("url") url: String
    ): SpotDLDownloadResult

    @GET("api/songs/search")
    suspend fun searchSongs(
        @Query("query") query: String
    ): List<SpotDLSong>
}

@Singleton
class SpotDLSourceProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: kotlinx.serialization.json.Json
) : MusicSourceProvider {

    override val name: String = "spotDL"
    override val iconDescription: String = "spotDL Downloader"

    var baseUrl: String = "http://10.0.2.2:8800/"
        set(value) {
            field = if (value.endsWith("/")) value else "$value/"
            _api = null
        }

    private var _api: SpotDLApi? = null
    private val api: SpotDLApi
        get() {
            return _api ?: run {
                val contentType = "application/json".toMediaType()
                Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(json.asConverterFactory(contentType))
                    .build()
                    .create(SpotDLApi::class.java)
                    .also { _api = it }
            }
        }

    val candidateHosts = listOf(
        "http://10.0.2.2:8800/",
        "http://192.168.0.107:8800/",
        "http://127.0.0.1:8800/"
    )

    private fun createApi(url: String): SpotDLApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(url)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(SpotDLApi::class.java)
    }

    override suspend fun search(query: String, page: Int): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        val hosts = if (candidateHosts.contains(baseUrl)) candidateHosts else listOf(baseUrl) + candidateHosts
        for (host in hosts) {
            val res = runCatching {
                val currentApi = createApi(host)
                val isUrl = query.startsWith("http://") || query.startsWith("https://")
                val songs = if (isUrl) {
                    listOf(currentApi.getSongByUrl(url = query))
                } else {
                    currentApi.searchSongs(query = query)
                }
                baseUrl = host
                songs.map { song ->
                    val trackIdentifier = song.songId.ifBlank { song.downloadUrl ?: query }
                    SearchResult(
                        id = trackIdentifier,
                        title = song.name.ifBlank { query },
                        artist = song.artist.ifBlank { song.artists.joinToString(", ") },
                        album = song.albumName,
                        durationMs = (song.duration * 1000L).toLong(),
                        coverUrl = song.coverUrl,
                        license = "spotDL",
                        provider = name
                    )
                }
            }
            if (res.isSuccess) {
                return@withContext res
            }
        }
        Result.failure(Exception("Could not connect to spotDL backend on $hosts"))
    }

    private fun buildAudioUrl(file: String): String {
        val encoded = java.net.URLEncoder.encode(file, "UTF-8")
        return "${baseUrl}api/download/file?file=$encoded"
    }

    override suspend fun getStreamUrl(trackId: String): Result<String> = withContext(Dispatchers.IO) {
        Result.success(buildAudioUrl(trackId))
    }

    override suspend fun getDownloadUrl(trackId: String): Result<String> = withContext(Dispatchers.IO) {
        Result.success(buildAudioUrl(trackId))
    }

    override suspend fun getTrackDetails(trackId: String): Result<TrackDetails> = withContext(Dispatchers.IO) {
        runCatching {
            val song = api.getSongByUrl(url = trackId)
            val audioUrl = buildAudioUrl(trackId)
            TrackDetails(
                id = song.songId.ifBlank { trackId },
                title = song.name,
                artist = song.artist.ifBlank { song.artists.joinToString(", ") },
                album = song.albumName,
                durationMs = (song.duration * 1000L).toLong(),
                coverUrl = song.coverUrl,
                license = "spotDL",
                provider = name,
                streamUrl = audioUrl,
                downloadUrl = audioUrl
            )
        }
    }
}
