package com.nido.music.core.network.audius

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
import retrofit2.http.Path
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AudiusUser(
    val name: String = ""
)

@Serializable
data class AudiusArtwork(
    @SerialName("480x480") val small: String? = null,
    @SerialName("1000x1000") val large: String? = null
)

@Serializable
data class AudiusTrackAccess(
    val stream: Boolean = true,
    val download: Boolean = true
)

@Serializable
data class AudiusTrack(
    val id: String,
    val title: String,
    val duration: Long = 0,
    val user: AudiusUser = AudiusUser(),
    val genre: String? = null,
    @SerialName("artwork") val artwork: AudiusArtwork? = null,
    @SerialName("is_downloadable") val isDownloadable: Boolean = false,
    val access: AudiusTrackAccess? = null
)

@Serializable
data class AudiusSearchResponse(
    val data: List<AudiusTrack> = emptyList()
)

@Serializable
data class AudiusTrackResponse(
    val data: AudiusTrack? = null
)

interface AudiusApi {
    @GET("v1/tracks/search")
    suspend fun searchTracks(
        @Query("query") query: String,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("app_name") appName: String = "NidoMusic"
    ): AudiusSearchResponse

    @GET("v1/tracks/{track_id}")
    suspend fun getTrack(
        @Path("track_id") trackId: String,
        @Query("app_name") appName: String = "NidoMusic"
    ): AudiusTrackResponse
}

@Singleton
class AudiusSourceProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: kotlinx.serialization.json.Json
) : MusicSourceProvider {

    override val name: String = "Audius"
    override val iconDescription: String = "Audius Open Music Protocol"

    private val api: AudiusApi by lazy {
        val contentType = "application/json".toMediaType()
        Retrofit.Builder()
            .baseUrl("https://api.audius.co/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(AudiusApi::class.java)
    }

    override suspend fun search(query: String, page: Int): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        runCatching {
            val limit = 20
            val offset = maxOf(0, page) * limit
            val response = api.searchTracks(query = query, limit = limit, offset = offset)
            response.data.map { track ->
                SearchResult(
                    id = track.id,
                    title = track.title,
                    artist = track.user.name,
                    durationMs = track.duration * 1000L,
                    coverUrl = track.artwork?.large ?: track.artwork?.small,
                    license = "Open Catalog",
                    provider = name
                )
            }
        }
    }

    override suspend fun getStreamUrl(trackId: String): Result<String> = withContext(Dispatchers.IO) {
        Result.success("https://api.audius.co/v1/tracks/$trackId/stream?app_name=NidoMusic")
    }

    override suspend fun getDownloadUrl(trackId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getTrack(trackId = trackId)
            val track = response.data ?: throw NoSuchElementException("Track not found: $trackId")
            if (!track.isDownloadable || track.access?.download == false) {
                throw IllegalStateException("Download unavailable or denied for track: $trackId")
            }
            "https://api.audius.co/v1/tracks/$trackId/download?app_name=NidoMusic"
        }
    }

    override suspend fun getTrackDetails(trackId: String): Result<TrackDetails> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getTrack(trackId = trackId)
            val track = response.data ?: throw NoSuchElementException("Track not found: $trackId")
            TrackDetails(
                id = track.id,
                title = track.title,
                artist = track.user.name,
                durationMs = track.duration * 1000L,
                coverUrl = track.artwork?.large ?: track.artwork?.small,
                license = "Open Catalog",
                provider = name,
                streamUrl = "https://api.audius.co/v1/tracks/$trackId/stream?app_name=NidoMusic",
                downloadUrl = "https://api.audius.co/v1/tracks/$trackId/stream?app_name=NidoMusic"
            )
        }
    }
}
