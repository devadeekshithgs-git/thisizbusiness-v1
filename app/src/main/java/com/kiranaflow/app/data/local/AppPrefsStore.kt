package com.kiranaflow.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

data class AppPrefs(
    val demoModeEnabled: Boolean = false,
    val demoResetRequested: Boolean = false,
    val deviceId: String? = null,
    val lastSyncAttemptAtMillis: Long? = null,
    val lastSyncMessage: String? = null,
    val lastBackupAtMillis: Long? = null,
    // Scan feedback defaults match POS expectations: enabled by default, user-configurable.
    val scanBeepEnabled: Boolean = true,
    val scanVibrationEnabled: Boolean = true,
    /**
     * If > currentTimeMillis, financial numbers may be revealed.
     * When 0 or expired, show privacy overlay (masked values).
     */
    val privacyUnlockedUntilMillis: Long = 0L
)

private val Context.appPrefsDataStore by preferencesDataStore(name = "app_prefs")

class AppPrefsStore(private val context: Context) {
    private object Keys {
        val demoModeEnabled = booleanPreferencesKey("demo_mode_enabled")
        val demoResetRequested = booleanPreferencesKey("demo_reset_requested")
        val deviceId = stringPreferencesKey("device_id")
        val lastSyncAttemptAtMillis = longPreferencesKey("last_sync_attempt_at_millis")
        val lastSyncMessage = stringPreferencesKey("last_sync_message")
        val lastBackupAtMillis = longPreferencesKey("last_backup_at_millis")
        val scanBeepEnabled = booleanPreferencesKey("scan_beep_enabled")
        val scanVibrationEnabled = booleanPreferencesKey("scan_vibration_enabled")
        val privacyUnlockedUntilMillis = longPreferencesKey("privacy_unlocked_until_millis")
    }

    val prefs: Flow<AppPrefs> = context.appPrefsDataStore.data.map { p ->
        AppPrefs(
            demoModeEnabled = p[Keys.demoModeEnabled] ?: false,
            demoResetRequested = p[Keys.demoResetRequested] ?: false,
            deviceId = p[Keys.deviceId],
            lastSyncAttemptAtMillis = p[Keys.lastSyncAttemptAtMillis],
            lastSyncMessage = p[Keys.lastSyncMessage],
            lastBackupAtMillis = p[Keys.lastBackupAtMillis],
            scanBeepEnabled = p[Keys.scanBeepEnabled] ?: true,
            scanVibrationEnabled = p[Keys.scanVibrationEnabled] ?: true,
            privacyUnlockedUntilMillis = p[Keys.privacyUnlockedUntilMillis] ?: 0L
        )
    }

    suspend fun setDemoModeEnabled(enabled: Boolean) {
        context.appPrefsDataStore.edit { it[Keys.demoModeEnabled] = enabled }
    }

    /**
     * When true, the app should wipe local DB on next startup, then seed based on [demoModeEnabled].
     */
    suspend fun requestDemoReset(reset: Boolean) {
        context.appPrefsDataStore.edit { it[Keys.demoResetRequested] = reset }
    }

    suspend fun getOrCreateDeviceId(): String {
        var id: String? = null
        context.appPrefsDataStore.edit { prefs ->
            id = prefs[Keys.deviceId]
            if (id.isNullOrBlank()) {
                id = UUID.randomUUID().toString()
                prefs[Keys.deviceId] = id!!
            }
        }
        return id!!
    }

    suspend fun setLastSyncAttempt(atMillis: Long, message: String?) {
        context.appPrefsDataStore.edit {
            it[Keys.lastSyncAttemptAtMillis] = atMillis
            if (message.isNullOrBlank()) it.remove(Keys.lastSyncMessage) else it[Keys.lastSyncMessage] = message
        }
    }

    suspend fun setLastBackupAt(atMillis: Long) {
        context.appPrefsDataStore.edit { it[Keys.lastBackupAtMillis] = atMillis }
    }

    suspend fun setScanBeepEnabled(enabled: Boolean) {
        context.appPrefsDataStore.edit { it[Keys.scanBeepEnabled] = enabled }
    }

    suspend fun setScanVibrationEnabled(enabled: Boolean) {
        context.appPrefsDataStore.edit { it[Keys.scanVibrationEnabled] = enabled }
    }

    suspend fun setPrivacyUnlockedUntil(untilMillis: Long) {
        context.appPrefsDataStore.edit { it[Keys.privacyUnlockedUntilMillis] = untilMillis.coerceAtLeast(0L) }
    }
}




