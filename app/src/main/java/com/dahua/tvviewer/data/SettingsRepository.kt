package com.dahua.tvviewer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dahua_nvr_settings")

class SettingsRepository(private val context: Context) {

    private val configKey = stringPreferencesKey("nvr_config_json")
    private val liveChannelsKey = stringPreferencesKey("known_live_channels")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val defaultLiveChannels = setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 12, 13, 15)

    val knownLiveChannelsFlow: Flow<Set<Int>> = context.dataStore.data.map { preferences ->
        val raw = preferences[liveChannelsKey]
        if (raw.isNullOrBlank()) {
            defaultLiveChannels
        } else {
            try {
                raw.split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .filter { it in 1..64 }
                    .toSet()
                    .ifEmpty { defaultLiveChannels }
            } catch (_: Exception) {
                defaultLiveChannels
            }
        }
    }

    suspend fun saveKnownLiveChannels(channels: Set<Int>) {
        context.dataStore.edit { preferences ->
            preferences[liveChannelsKey] = channels.joinToString(",")
        }
    }

    val nvrConfigFlow: Flow<NvrConfig> = context.dataStore.data.map { preferences ->
        val rawJson = preferences[configKey]
        if (rawJson.isNullOrBlank()) {
            NvrConfig()
        } else {
            try {
                json.decodeFromString<NvrConfig>(rawJson)
            } catch (e: Exception) {
                NvrConfig()
            }
        }
    }

    suspend fun saveConfig(config: NvrConfig) {
        context.dataStore.edit { preferences ->
            preferences[configKey] = json.encodeToString(config)
        }
    }
}
