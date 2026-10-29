package com.nido.music.core.network.jamendo

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
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class JamendoResponse(
    val results: List<JamendoTrack> = emptyList()
)

@Serializable
data class JamendoTrack(
    val id: String,
    val name: String,
    val duration: Long = 0,
    val artist_name: String = "",
    val album_name: String = "",
    val image: String = "",
    val audio: String = "",
    val audiodownload: String = "",
    val license_ccurl: String = ""
)

interface JamendoApi {
    @GET("v3.0/tracks/")
    suspend fun searchTracks(
        @Query("client_id") clientId: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 20,
        @Query("namesearch") query: String,
        @Query("audioformat") audioFormat: String = "mp32"
    ): JamendoResponse
}

@Singleton
class JamendoSourceProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: kotlinx.serialization.json.Json
) : MusicSourceProvider {

    override val name: String = "Jamendo"
    override val iconDescription: String = "Jamendo Creative Commons Catalog"

    private val api: JamendoApi by lazy {
        val contentType = "application/json".toMediaType()
        Retrofit.Builder()
            .baseUrl("https://api.jamendo.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(JamendoApi::class.java)
    }

    // Default Jamendo developer test client ID
    private val clientId = "56d30c95"

    override suspend fun search(query: String, page: Int): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.searchTracks(clientId = clientId, query = query, limit = 20)
            response.results.map { track ->
                SearchResult(
                    id = track.id,
                    title = track.name,
                    artist = track.artist_name,
                    album = track.album_name,
                    durationMs = track.duration * 1000L,
                    coverUrl = track.image,
                    license = "CC-BY",
                    provider = name
                )
            }
        }
    }

    override suspend fun getStreamUrl(trackId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.searchTracks(clientId = clientId, query = trackId, limit = 1)
            response.results.firstOrNull()?.audio ?: throw IllegalStateException("Track not found")
        }
    }

    override suspend fun getDownloadUrl(trackId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.searchTracks(clientId = clientId, query = trackId, limit = 1)
            response.results.firstOrNull()?.audiodownload ?: response.results.firstOrNull()?.audio ?: throw IllegalStateException("Download URL not found")
        }
    }

    override suspend fun getTrackDetails(trackId: String): Result<TrackDetails> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.searchTracks(clientId = clientId, query = trackId, limit = 1)
            val track = response.results.firstOrNull() ?: throw IllegalStateException("Track not found")
            TrackDetails(
                id = track.id,
                title = track.name,
                artist = track.artist_name,
                album = track.album_name,
                durationMs = track.duration * 1000L,
                coverUrl = track.image,
                license = "CC-BY",
                provider = name,
                downloadUrl = track.audiodownload.ifBlank { track.audio },
                streamUrl = track.audio
            )
        }
    }
}

