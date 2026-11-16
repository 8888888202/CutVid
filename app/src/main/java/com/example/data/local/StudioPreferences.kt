package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ExportResolution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioDataStore: DataStore<Preferences> by preferencesDataStore(name = "cutforge_prefs")

data class StudioSettings(
    val defaultResolution: ExportResolution = ExportResolution.FHD_1080P,
    val defaultFps: Int = 30,
    val showSafeZoneGuides: Boolean = true,
    val magneticSnapEnabled: Boolean = true
)

class StudioPreferences(private val context: Context) {
    private object Keys {
        val DEFAULT_RES = stringPreferencesKey("default_resolution")
        val DEFAULT_FPS = intPreferencesKey("default_fps")
        val SAFE_ZONE = booleanPreferencesKey("safe_zone_guides")
        val MAGNETIC_SNAP = booleanPreferencesKey("magnetic_snap")
    }

    val settingsFlow: Flow<StudioSettings> = context.studioDataStore.data.map { prefs ->
        val resName = prefs[Keys.DEFAULT_RES] ?: ExportResolution.FHD_1080P.name
        val res = runCatching { ExportResolution.valueOf(resName) }
            .getOrDefault(ExportResolution.FHD_1080P)
        StudioSettings(
            defaultResolution = res,
            defaultFps = prefs[Keys.DEFAULT_FPS] ?: 30,
            showSafeZoneGuides = prefs[Keys.SAFE_ZONE] ?: true,
            magneticSnapEnabled = prefs[Keys.MAGNETIC_SNAP] ?: true
        )
    }

    suspend fun updateDefaultResolution(resolution: ExportResolution) {
        context.studioDataStore.edit { it[Keys.DEFAULT_RES] = resolution.name }
    }

    suspend fun updateDefaultFps(fps: Int) {
        context.studioDataStore.edit { it[Keys.DEFAULT_FPS] = fps }
    }

    suspend fun setSafeZoneGuides(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.SAFE_ZONE] = enabled }
    }

    suspend fun setMagneticSnap(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.MAGNETIC_SNAP] = enabled }
    }
}
