package com.nido.music.feature.music.ui.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.nido.music.core.database.dao.LibraryFolderDao
import com.nido.music.core.database.dao.PlaylistDao
import com.nido.music.core.database.dao.SongDao
import com.nido.music.core.database.entity.LibraryFolderEntity
import com.nido.music.core.database.entity.PlaylistEntity
import com.nido.music.core.database.entity.SongEntity
import com.nido.music.core.common.UserPreferencesRepository
import com.nido.music.feature.music.data.playback.PlaybackController
import com.nido.music.feature.music.data.scanner.ScanWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortField(val label: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    DATE_ADDED("Date Added"),
    DURATION("Duration")
}

data class SortConfig(
    val field: SortField = SortField.TITLE,
    val ascending: Boolean = true
)

fun List<SongEntity>.keepUniqueSongs(): List<SongEntity> {
    val seenUris = HashSet<String>()
    val seenMeta = HashSet<String>()
    return filter { song ->
        val uriKey = song.uri.trim()
        val normTitle = song.title.trim().lowercase()
        val normArtist = song.artist.trim().lowercase()
        val durSec = song.durationMs / 1000L
        val metaKey = if (normTitle.isNotBlank() && !normTitle.equals("unknown", ignoreCase = true)) {
            "$normTitle|$normArtist|$durSec"
        } else null

        val isUriNew = seenUris.add(uriKey)
        val isMetaNew = if (metaKey != null) seenMeta.add(metaKey) else true
        isUriNew && isMetaNew
    }
}

fun List<SongEntity>.applySort(config: SortConfig): List<SongEntity> {
    val comparator = when (config.field) {
        SortField.TITLE -> compareBy<SongEntity> { it.title.lowercase() }.thenBy { it.artist.lowercase() }
        SortField.ARTIST -> compareBy<SongEntity> { it.artist.lowercase() }.thenBy { it.title.lowercase() }
        SortField.DATE_ADDED -> compareBy<SongEntity> { it.dateAdded }.thenBy { it.title.lowercase() }
        SortField.DURATION -> compareBy<SongEntity> { it.durationMs }.thenBy { it.title.lowercase() }
    }.thenBy { it.id }

    return if (config.ascending) {
        sortedWith(comparator)
    } else {
        sortedWith(comparator.reversed())
    }
}

data class PlaylistWithCount(
    val playlist: PlaylistEntity,
    val count: Int
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val folderDao: LibraryFolderDao,
    private val playlistDao: PlaylistDao,
    val playbackController: PlaybackController,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val searchQuery = MutableStateFlow("")

    val sortConfig = MutableStateFlow(SortConfig(SortField.TITLE, true))
    val playlistSortConfig = MutableStateFlow(SortConfig(SortField.TITLE, true))

    init {
        // Restore persisted sort preference
        viewModelScope.launch {
            combine(
                userPreferencesRepository.sortOptionFlow,
                userPreferencesRepository.sortOrderAscFlow
            ) { name, asc ->
                val field = SortField.entries.firstOrNull { it.name == name } ?: SortField.TITLE
                SortConfig(field, asc)
            }.collect {
                sortConfig.value = it
            }
        }
        viewModelScope.launch {
            combine(
                userPreferencesRepository.playlistSortOptionFlow,
                userPreferencesRepository.playlistSortOrderAscFlow
            ) { name, asc ->
                val field = SortField.entries.firstOrNull { it.name == name } ?: SortField.TITLE
                SortConfig(field, asc)
            }.collect {
                playlistSortConfig.value = it
            }
        }

        viewModelScope.launch {
            songDao.deduplicateSongs()
            val count = songDao.getSongCount().first()
            if (count == 0) {
                val demoSongs = listOf(
                    SongEntity(
                        uri = "https://prod-1.storage.jamendo.com/?trackid=1215291&format=mp32&from=app-56d30c95",
                        title = "Creative Commons Anthem",
                        artist = "Jamendo Artists",
                        album = "Nido Starter Pack",
                        durationMs = 184000L,
                        folderPath = "Online",
                        dateAdded = System.currentTimeMillis()
                    ),
                    SongEntity(
                        uri = "https://prod-1.storage.jamendo.com/?trackid=1118141&format=mp32&from=app-56d30c95",
                        title = "Ambient Chillout",
                        artist = "Soundroll",
                        album = "Nido Starter Pack",
                        durationMs = 210000L,
                        folderPath = "Online",
                        dateAdded = System.currentTimeMillis()
                    ),
                    SongEntity(
                        uri = "https://prod-1.storage.jamendo.com/?trackid=1411516&format=mp32&from=app-56d30c95",
                        title = "Summer Grooves",
                        artist = "Electro Nido",
                        album = "Beats",
                        durationMs = 195000L,
                        folderPath = "Online",
                        dateAdded = System.currentTimeMillis()
                    )
                )
                songDao.upsertSongs(demoSongs)
            }
        }
    }

    val songs: StateFlow<List<SongEntity>> = combine(
        searchQuery.flatMapLatest { query ->
            if (query.isBlank()) songDao.getAllSongs()
            else songDao.searchSongs(query)
        },
        sortConfig
    ) { rawSongs, config -> rawSongs.keepUniqueSongs().applySort(config) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val likedSongs: StateFlow<List<SongEntity>> = combine(
        songDao.getLikedSongs(),
        sortConfig
    ) { rawSongs, config -> rawSongs.keepUniqueSongs().applySort(config) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<String>> = songDao.getDistinctAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artists: StateFlow<List<String>> = songDao.getDistinctArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<String>> = songDao.getDistinctFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedPlaylistId = MutableStateFlow<Long?>(null)

    val playlistSongs: StateFlow<List<SongEntity>> = combine(
        selectedPlaylistId.flatMapLatest { id ->
            if (id != null) playlistDao.getSongsInPlaylist(id)
            else kotlinx.coroutines.flow.flowOf(emptyList())
        },
        playlistSortConfig
    ) { rawSongs, config -> rawSongs.keepUniqueSongs().applySort(config) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun setSortConfig(field: SortField, ascending: Boolean) {
        val newConfig = SortConfig(field, ascending)
        sortConfig.value = newConfig
        viewModelScope.launch {
            userPreferencesRepository.setSortOption(field.name, ascending)
        }
    }

    fun toggleSortDirection() {
        val current = sortConfig.value
        setSortConfig(current.field, !current.ascending)
    }

    fun setPlaylistSortConfig(field: SortField, ascending: Boolean) {
        val newConfig = SortConfig(field, ascending)
        playlistSortConfig.value = newConfig
        viewModelScope.launch {
            userPreferencesRepository.setPlaylistSortOption(field.name, ascending)
        }
    }

    fun togglePlaylistSortDirection() {
        val current = playlistSortConfig.value
        setPlaylistSortConfig(current.field, !current.ascending)
    }

    fun shufflePlay(songList: List<SongEntity>) {
        if (songList.isEmpty()) return
        playbackController.shufflePlay(songList)
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            playlistDao.insertPlaylist(PlaylistEntity(name = name.trim()))
        }
    }

    fun renamePlaylist(id: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            playlistDao.renamePlaylist(id, newName.trim())
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            playlistDao.deletePlaylist(id)
            if (selectedPlaylistId.value == id) {
                selectedPlaylistId.value = null
            }
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            playlistDao.addSongToPlaylist(playlistId, songId)
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            playlistDao.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun selectPlaylist(id: Long?) {
        selectedPlaylistId.value = id
    }

    fun onFolderSelected(uri: Uri) {
        viewModelScope.launch {
            // Persist permission & folder
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            folderDao.insertFolder(
                LibraryFolderEntity(
                    treeUri = uri.toString(),
                    displayName = uri.lastPathSegment ?: "Music Folder",
                    lastScanTimestamp = 0L
                )
            )

            // Trigger scanner worker
            val workRequest = OneTimeWorkRequestBuilder<ScanWorker>()
                .setInputData(workDataOf(ScanWorker.KEY_TREE_URI to uri.toString()))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "scan_${uri.hashCode()}",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    fun playSong(song: SongEntity, queue: List<SongEntity> = songs.value) {
        playbackController.playSong(song, queue)
    }

    fun toggleLike(songId: Long) {
        viewModelScope.launch {
            songDao.toggleLike(songId)
        }
    }
}

