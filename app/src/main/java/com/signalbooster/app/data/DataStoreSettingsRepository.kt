package com.signalbooster.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.signalbooster.app.domain.interfaces.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "signalbooster_settings")

/**
 * DataStore implementation of SettingsRepository.
 * Manages user budgets, allowlisted probe endpoints, and local data purge.
 */
@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private object PreferencesKeys {
        val PROBE_TIMEOUT_MS = longPreferencesKey("probe_timeout_ms")
        val PROBE_BYTE_BUDGET = longPreferencesKey("probe_byte_budget")
        val PROBE_ENDPOINT = stringPreferencesKey("probe_endpoint")
        val ADAPTIVE_MONITORING = booleanPreferencesKey("adaptive_monitoring")
    }

    override val probeTimeoutMs: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PROBE_TIMEOUT_MS] ?: 10000L
    }

    override val probeByteBudget: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PROBE_BYTE_BUDGET] ?: 1048576L // 1 MB
    }

    override val probeEndpoint: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PROBE_ENDPOINT] ?: "https://connectivitycheck.gstatic.com/generate_204"
    }

    override val isAdaptiveMonitoringEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.ADAPTIVE_MONITORING] ?: true
    }

    override suspend fun setProbeTimeoutMs(timeoutMs: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PROBE_TIMEOUT_MS] = timeoutMs.coerceIn(2000L, 30000L)
        }
    }

    override suspend fun setProbeByteBudget(byteBudget: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PROBE_BYTE_BUDGET] = byteBudget.coerceIn(102400L, 10485760L) // 100KB - 10MB
        }
    }

    override suspend fun setProbeEndpoint(endpoint: String) {
        // Enforce safe allowlisted domains
        val safeEndpoints = listOf(
            "https://connectivitycheck.gstatic.com/generate_204",
            "https://1.1.1.1",
            "https://www.google.com/generate_204",
            "https://cloudflare.com"
        )
        val validEndpoint = if (safeEndpoints.any { endpoint.startsWith(it) }) endpoint else safeEndpoints.first()
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PROBE_ENDPOINT] = validEndpoint
        }
    }

    override suspend fun setAdaptiveMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ADAPTIVE_MONITORING] = enabled
        }
    }

    override suspend fun wipeLocalData(): Boolean {
        return try {
            context.dataStore.edit { preferences ->
                preferences.clear()
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
