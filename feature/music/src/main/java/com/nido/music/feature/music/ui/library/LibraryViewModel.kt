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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    val songs: StateFlow<List<SongEntity>> = searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) songDao.getAllSongs()
            else songDao.searchSongs(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<String>> = songDao.getDistinctAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artists: StateFlow<List<String>> = songDao.getDistinctArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<String>> = songDao.getDistinctFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

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

    fun playSong(song: SongEntity) {
        playbackController.playSong(song, songs.value)
    }

    fun toggleLike(songId: Long) {
        viewModelScope.launch {
            songDao.toggleLike(songId)
        }
    }
}

