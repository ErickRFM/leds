package com.rgbcontroller

import com.rgbcontroller.core.components.model.ComponentMeasurement
import com.rgbcontroller.core.components.status.ComponentStatusRepository
import com.rgbcontroller.core.components.status.LastKnownTelemetry
import com.rgbcontroller.core.components.telemetry.TelemetryHistory
import com.rgbcontroller.core.components.telemetry.TelemetryHistoryCodec
import com.rgbcontroller.core.components.telemetry.TelemetryHistoryLimits
import com.rgbcontroller.core.components.telemetry.TelemetryProvenance
import com.rgbcontroller.core.components.telemetry.TelemetrySample
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TelemetryHistoryTest {

    private fun sample(
        deviceId: String = "device-a",
        componentId: String = "dht22",
        type: String = "temperature",
        value: Float? = 20f,
        valid: Boolean = true,
        recordedAtMs: Long = 1000L
    ) = TelemetrySample(
        deviceId = deviceId,
        componentId = componentId,
        measurementType = type,
        value = value,
        unit = "C",
        status = if (valid) "fresh" else "invalid",
        valid = valid,
        sourceTimestampMs = recordedAtMs,
        recordedAtMs = recordedAtMs,
        provenance = TelemetryProvenance.FIRMWARE
    )

    @Test fun appendKeepsOnlyNewestSamplesPerSeries() {
        val limits = TelemetryHistoryLimits(maxSamplesPerSeries = 2, maxSeries = 4, maxTotalSamples = 100)
        val existing = listOf(sample(recordedAtMs = 1000), sample(recordedAtMs = 2000))
        val incoming = listOf(sample(recordedAtMs = 3000))
        val result = TelemetryHistory.append(existing, incoming, limits, nowMs = 3000)
        assertEquals(2, result.size)
        assertEquals(listOf(2000L, 3000L), result.map { it.recordedAtMs })
    }

    @Test fun appendDropsSamplesPastRetention() {
        val limits = TelemetryHistoryLimits(retentionMs = 500)
        val existing = listOf(sample(recordedAtMs = 100))
        val incoming = listOf(sample(recordedAtMs = 900))
        val result = TelemetryHistory.append(existing, incoming, limits, nowMs = 1000)
        assertEquals(1, result.size)
        assertEquals(900L, result[0].recordedAtMs)
    }

    @Test fun invalidMeasurementNeverBecomesPlottable() {
        val measurement = ComponentMeasurement("temperature", "invalid", false, Float.NaN, "C", 10L)
        val sample = TelemetryHistory.samplesFromMeasurements(
            listOf(measurement), "device-a", 1000L, TelemetryProvenance.FIRMWARE
        ).single()
        assertNull(sample.value)
        assertFalse(sample.valid)
        assertFalse(sample.isPlottable)
        assertEquals("dht22", sample.componentId)
    }

    @Test fun deviceIsolationNeverLeaksAnotherDeviceHistory() {
        val history = listOf(sample(deviceId = "device-a"), sample(deviceId = "device-b"))
        assertEquals(1, TelemetryHistory.samplesForDevice(history, "device-a").size)
        assertTrue(TelemetryHistory.samplesForDevice(history, "device-b").single().deviceId == "device-b")
        assertTrue(TelemetryHistory.samplesForDevice(history, null).isEmpty())
    }

    @Test fun codecRoundTripsAndRejectsUnknownVersion() {
        val samples = listOf(sample(value = 21.7f), sample(valid = false, value = null))
        val decoded = TelemetryHistoryCodec.decode(TelemetryHistoryCodec.encode(samples))
        assertEquals(samples, decoded)
        assertTrue(TelemetryHistoryCodec.decode("{\"version\":2,\"samples\":[]}").isEmpty())
        assertTrue(TelemetryHistoryCodec.decode("garbage").isEmpty())
    }

    @Test fun repositoryIngestAppendsFirmwareHistoryAndPersists() = runBlocking {
        val storage = FakeComponentStatusStorage()
        val repo = ComponentStatusRepository(storage, clock = { 1000L })
        repo.setDevice("device-a")
        val ok = repo.ingestTelemetry(
            """{"version":1,"measurements":[
               {"type":"humidity","status":"fresh","valid":true,"value":48.3,"unit":"%","timestampMs":1200},
               {"type":"temperature","status":"fresh","valid":true,"value":21.7,"unit":"C","timestampMs":1200}]}"""
        )
        assertTrue(ok)
        val history = repo.state.value.deviceHistory
        assertEquals(2, history.size)
        assertTrue(history.all { it.provenance == TelemetryProvenance.FIRMWARE })
        assertEquals(1, storage.saveHistoryCount)
    }

    @Test fun loadPersistedSeedsHistoryFromLastKnownSnapshot() = runBlocking {
        val storage = FakeComponentStatusStorage().apply {
            lastKnownTelemetry = LastKnownTelemetry(
                "device-a",
                listOf(ComponentMeasurement("humidity", "fresh", true, 50f, "%", 500L)),
                500L
            )
        }
        val repo = ComponentStatusRepository(storage, clock = { 1000L })
        repo.loadPersisted()
        val history = repo.state.value.history
        assertEquals(1, history.size)
        assertEquals(TelemetryProvenance.LAST_KNOWN, history[0].provenance)
        assertEquals("dht22", history[0].componentId)
        assertEquals(1, storage.saveHistoryCount)
    }

    @Test fun loadPersistedDoesNotReseedWhenHistoryExists() = runBlocking {
        val storage = FakeComponentStatusStorage().apply {
            lastKnownTelemetry = LastKnownTelemetry(
                "device-a",
                listOf(ComponentMeasurement("humidity", "fresh", true, 50f, "%", 500L)),
                500L
            )
            history = listOf(sample(recordedAtMs = 700))
        }
        val repo = ComponentStatusRepository(storage, clock = { 1000L })
        repo.loadPersisted()
        assertEquals(1, repo.state.value.history.size)
        assertEquals(0, storage.saveHistoryCount)
    }
}
