package com.rgbcontroller.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rgbcontroller.core.components.status.ComponentStatusStorage
import com.rgbcontroller.core.components.status.LastKnownStatus
import com.rgbcontroller.core.components.status.LastKnownStatusCodec
import com.rgbcontroller.core.components.status.LastKnownTelemetry
import com.rgbcontroller.core.components.status.LastKnownTelemetryCodec
import com.rgbcontroller.core.components.status.LocalDesiredConfig
import com.rgbcontroller.core.components.status.LocalDesiredConfigCodec
import com.rgbcontroller.core.components.telemetry.TelemetryHistoryCodec
import com.rgbcontroller.core.components.telemetry.TelemetrySample
import kotlinx.coroutines.flow.first

/**
 * DataStore-backed persistence for the component dashboard. Reuses the single
 * app DataStore; keys are versioned so an old/unknown schema decodes to a safe
 * empty value instead of crashing. No credentials are ever written here.
 *
 * Room is intentionally NOT used: the model is a small bounded snapshot keyed by
 * device with no relational queries, so DataStore is sufficient.
 */
class DataStoreComponentStatusStorage(private val context: Context) : ComponentStatusStorage {
    companion object {
        private val LAST_KNOWN = stringPreferencesKey("component_last_known_v1")
        private val DESIRED = stringPreferencesKey("component_desired_v1")
        private val TELEMETRY = stringPreferencesKey("component_telemetry_v1")
        private val HISTORY = stringPreferencesKey("component_telemetry_history_v1")
    }

    override suspend fun loadLastKnown(): LastKnownStatus? =
        LastKnownStatusCodec.decode(context.dataStore.data.first()[LAST_KNOWN])

    override suspend fun saveLastKnown(status: LastKnownStatus) {
        val encoded = LastKnownStatusCodec.encode(status)
        context.dataStore.edit { prefs -> prefs[LAST_KNOWN] = encoded }
    }

    override suspend fun loadDesired(): LocalDesiredConfig =
        LocalDesiredConfigCodec.decode(context.dataStore.data.first()[DESIRED])

    override suspend fun saveDesired(config: LocalDesiredConfig) {
        val encoded = LocalDesiredConfigCodec.encode(config)
        context.dataStore.edit { prefs -> prefs[DESIRED] = encoded }
    }

    override suspend fun loadTelemetry(): LastKnownTelemetry? =
        LastKnownTelemetryCodec.decode(context.dataStore.data.first()[TELEMETRY])

    override suspend fun saveTelemetry(telemetry: LastKnownTelemetry) {
        val encoded = LastKnownTelemetryCodec.encode(telemetry)
        context.dataStore.edit { prefs -> prefs[TELEMETRY] = encoded }
    }

    override suspend fun loadHistory(): List<TelemetrySample> =
        TelemetryHistoryCodec.decode(context.dataStore.data.first()[HISTORY])

    override suspend fun saveHistory(samples: List<TelemetrySample>) {
        val encoded = TelemetryHistoryCodec.encode(samples)
        context.dataStore.edit { prefs -> prefs[HISTORY] = encoded }
    }
}
