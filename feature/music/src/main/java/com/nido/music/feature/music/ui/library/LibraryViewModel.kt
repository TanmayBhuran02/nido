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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    val playbackController: PlaybackController
) : ViewModel() {

    val searchQuery = MutableStateFlow("")

    init {
        viewModelScope.launch {
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

    val songs: StateFlow<List<SongEntity>> = searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) songDao.getAllSongs()
            else songDao.searchSongs(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val likedSongs: StateFlow<List<SongEntity>> = songDao.getLikedSongs()
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

    val playlistSongs: StateFlow<List<SongEntity>> = selectedPlaylistId
        .flatMapLatest { id ->
            if (id != null) playlistDao.getSongsInPlaylist(id)
            else kotlinx.coroutines.flow.flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            playlistDao.insertPlaylist(PlaylistEntity(name = name.trim()))
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

