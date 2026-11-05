package com.nido.music.core.common

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
}
