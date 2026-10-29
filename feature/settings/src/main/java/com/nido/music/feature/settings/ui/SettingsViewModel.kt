package com.nido.music.feature.settings.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.nido.music.core.database.dao.LibraryFolderDao
import com.nido.music.core.database.entity.LibraryFolderEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val folderDao: LibraryFolderDao
) : ViewModel() {

    val folders: StateFlow<List<LibraryFolderEntity>> = folderDao.getAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rescanTriggered = MutableStateFlow(false)

    fun triggerRescan() {
        rescanTriggered.value = true
        val workerClass = try {
            Class.forName("com.nido.music.feature.music.data.scanner.ScanWorker")
                .asSubclass(ListenableWorker::class.java)
        } catch (_: Exception) {
            null
        }
        if (workerClass != null) {
            val workRequest = OneTimeWorkRequest.Builder(workerClass).build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "rescan_all",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    fun removeFolder(uri: String) {
        viewModelScope.launch {
            folderDao.deleteFolder(uri)
        }
    }
}

