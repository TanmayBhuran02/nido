package com.nido.music.core.common

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nido_user_preferences"
)

/**
 * Persistent settings repository backed by Jetpack DataStore.
 * Survives app restarts and — with Auto Backup enabled — reinstalls/upgrades.
 */
@Singleton
class UserPreferencesRepository @Inject constructor(
    private val context: Context
) {
    private val dataStore = context.userPreferencesDataStore

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val READ_RECEIPTS = booleanPreferencesKey("read_receipts")
        val LAST_SEEN_PRIVACY = booleanPreferencesKey("last_seen_privacy")
        val SORT_OPTION = stringPreferencesKey("sort_option")
        val SORT_ORDER_ASC = booleanPreferencesKey("sort_order_asc")
        val PLAYLIST_SORT_OPTION = stringPreferencesKey("playlist_sort_option")
        val PLAYLIST_SORT_ORDER_ASC = booleanPreferencesKey("playlist_sort_order_asc")
        val LAST_SONG_URI = stringPreferencesKey("last_song_uri")
        val LAST_POSITION_MS = longPreferencesKey("last_position_ms")
        val LAST_SHUFFLE_ENABLED = booleanPreferencesKey("last_shuffle_enabled")
        val LAST_REPEAT_MODE = intPreferencesKey("last_repeat_mode")
    }

    // ── Theme ───────────────────────────────────────────────────────────

    val themeModeFlow: Flow<ThemeMode> = dataStore.data.map { prefs ->
        val name = prefs[Keys.THEME_MODE]
        if (name != null) {
            ThemeMode.entries.firstOrNull { it.name == name } ?: ThemeMode.SYSTEM
        } else {
            ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    // ── Privacy ─────────────────────────────────────────────────────────

    val readReceiptsFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.READ_RECEIPTS] ?: true
    }

    suspend fun setReadReceipts(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.READ_RECEIPTS] = enabled
        }
    }

    val lastSeenPrivacyFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.LAST_SEEN_PRIVACY] ?: true
    }

    suspend fun setLastSeenPrivacy(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_SEEN_PRIVACY] = enabled
        }
    }

    // ── Sort Option ─────────────────────────────────────────────────────

    val sortOptionFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.SORT_OPTION] ?: "TITLE"
    }

    val sortOrderAscFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SORT_ORDER_ASC] ?: true
    }

    suspend fun setSortOption(option: String, ascending: Boolean = true) {
        dataStore.edit { prefs ->
            prefs[Keys.SORT_OPTION] = option
            prefs[Keys.SORT_ORDER_ASC] = ascending
        }
    }

    val playlistSortOptionFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.PLAYLIST_SORT_OPTION] ?: "TITLE"
    }

    val playlistSortOrderAscFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.PLAYLIST_SORT_ORDER_ASC] ?: true
    }

    suspend fun setPlaylistSortOption(option: String, ascending: Boolean = true) {
        dataStore.edit { prefs ->
            prefs[Keys.PLAYLIST_SORT_OPTION] = option
            prefs[Keys.PLAYLIST_SORT_ORDER_ASC] = ascending
        }
    }

    // ── Playback State Persistence ──────────────────────────────────────

    val lastSongUriFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[Keys.LAST_SONG_URI]
    }

    val lastPositionMsFlow: Flow<Long> = dataStore.data.map { prefs ->
        prefs[Keys.LAST_POSITION_MS] ?: 0L
    }

    val lastShuffleEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.LAST_SHUFFLE_ENABLED] ?: false
    }

    val lastRepeatModeFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.LAST_REPEAT_MODE] ?: 0
    }

    suspend fun savePlaybackState(
        songUri: String,
        positionMs: Long = 0L,
        shuffleEnabled: Boolean = false,
        repeatMode: Int = 0
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_SONG_URI] = songUri
            prefs[Keys.LAST_POSITION_MS] = positionMs
            prefs[Keys.LAST_SHUFFLE_ENABLED] = shuffleEnabled
            prefs[Keys.LAST_REPEAT_MODE] = repeatMode
        }
    }

    suspend fun savePosition(positionMs: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_POSITION_MS] = positionMs
        }
    }

    suspend fun saveShuffleEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_SHUFFLE_ENABLED] = enabled
        }
    }

    suspend fun saveRepeatMode(repeatMode: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_REPEAT_MODE] = repeatMode
        }
    }
}
