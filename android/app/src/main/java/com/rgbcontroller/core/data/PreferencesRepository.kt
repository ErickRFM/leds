package com.rgbcontroller.core.data

import android.content.Context
import com.rgbcontroller.core.lab.LabExperimentLibrary
import com.rgbcontroller.core.lab.LabProject
import com.rgbcontroller.core.lab.LabProjects
import com.rgbcontroller.core.programs.EditorProject
import com.rgbcontroller.core.programs.EditorProjects
import com.rgbcontroller.core.programs.ProgramLanguage
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
        val EXPERIMENT_PROJECTS = stringPreferencesKey("experiment_projects_v3")
        val EDITOR_PROJECTS = stringPreferencesKey("editor_projects_v5")
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

    val experimentProjects: Flow<List<LabProject>> = context.dataStore.data
        .map { prefs -> LabProjects.decode(prefs[EXPERIMENT_PROJECTS] ?: "") }

    suspend fun saveExperimentProject(name: String, sequence: String) {
        context.dataStore.edit { prefs ->
            val before = LabProjects.decode(prefs[EXPERIMENT_PROJECTS] ?: "")
            prefs[EXPERIMENT_PROJECTS] = LabProjects.encode(LabProjects.upsert(before, name, sequence))
        }
    }

    suspend fun deleteExperimentProject(name: String) {
        context.dataStore.edit { prefs ->
            val before = LabProjects.decode(prefs[EXPERIMENT_PROJECTS] ?: "")
            prefs[EXPERIMENT_PROJECTS] = LabProjects.encode(LabProjects.remove(before, name))
        }
    }

    /** Local code editor projects only; never save device keys or network credentials. */
    val editorProjects: Flow<List<EditorProject>> = context.dataStore.data.map { prefs ->
        EditorProjects.decode(prefs[EDITOR_PROJECTS] ?: "")
    }

    suspend fun saveEditorProject(name: String, language: ProgramLanguage, source: String) {
        context.dataStore.edit { prefs ->
            val existing = EditorProjects.decode(prefs[EDITOR_PROJECTS] ?: "")
            prefs[EDITOR_PROJECTS] = EditorProjects.encode(
                EditorProjects.upsert(existing, name, language, source)
            )
        }
    }

    suspend fun deleteEditorProject(name: String) {
        context.dataStore.edit { prefs ->
            val existing = EditorProjects.decode(prefs[EDITOR_PROJECTS] ?: "")
            prefs[EDITOR_PROJECTS] = EditorProjects.encode(EditorProjects.remove(existing, name))
        }
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
