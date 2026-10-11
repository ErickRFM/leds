package com.rgbcontroller.core.data

import android.content.Context
import com.rgbcontroller.core.lab.LabExperimentLibrary
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "rgb_preferences")

class PreferencesRepository(private val context: Context) {
    companion object {
        val LAST_DEVICE_MAC = stringPreferencesKey("last_device_mac")
        val FAVORITE_COLORS = stringPreferencesKey("favorite_colors")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val EXPERIMENT_DRAFT = stringPreferencesKey("experiment_draft")
    }

    val themeMode: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[THEME_MODE] ?: "SYSTEM" }

    suspend fun saveThemeMode(mode: String) {
        require(mode in setOf("SYSTEM", "DARK", "LIGHT"))
        context.dataStore.edit { preferences -> preferences[THEME_MODE] = mode }
    }

    /** Only experiment instructions are stored locally; never device keys. */
    val experimentDraft: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[EXPERIMENT_DRAFT]?.takeIf { LabExperimentLibrary.parse(it) != null } }

    suspend fun saveExperimentDraft(sequence: String) {
        require(LabExperimentLibrary.parse(sequence) != null) { "Invalid experiment" }
        context.dataStore.edit { prefs -> prefs[EXPERIMENT_DRAFT] = sequence }
    }

    val lastDeviceMac: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[LAST_DEVICE_MAC] }

    suspend fun saveLastDeviceMac(mac: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_DEVICE_MAC] = mac
        }
    }

    val favoriteColors: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[FAVORITE_COLORS] ?: "#FF0000,#00FF00,#0000FF,#FFFF00,#00FFFF,#FF00FF,#FFFFFF" }

    suspend fun saveFavoriteColors(colors: String) {
        context.dataStore.edit { preferences ->
            preferences[FAVORITE_COLORS] = colors
        }
    }
}
