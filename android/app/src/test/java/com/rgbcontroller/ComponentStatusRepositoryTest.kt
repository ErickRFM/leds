package com.rgbcontroller

import com.rgbcontroller.core.components.status.ComponentStatusFreshness
import com.rgbcontroller.core.components.status.ComponentStatusRepository
import com.rgbcontroller.core.components.status.LastKnownStatus
import com.rgbcontroller.core.components.status.LocalDesiredComponent
import com.rgbcontroller.core.components.status.LocalDesiredConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ComponentStatusRepositoryTest {

    private val validPayload = """
        {"version":1,"gpioAuthority":"firmware","components":[
          {"id":"led.rgb","type":"actuator","interface":"pwm","state":"active","active":true,"pins":[27,25,26]}
        ]}
    """.trimIndent()

    private val validTelemetry = """
        {"version":1,"measurements":[
          {"type":"humidity","status":"fresh","valid":true,"value":48.30,"unit":"%","timestampMs":1200},
          {"type":"temperature","status":"fresh","valid":true,"value":21.70,"unit":"C","timestampMs":1200}
        ]}
    """.trimIndent()

    private fun newRepo(storage: FakeComponentStatusStorage = FakeComponentStatusStorage()) =
        ComponentStatusRepository(storage, clock = { 1000L })

    @Test fun restoresPersistedStatusAsLastKnownNotLive() = runBlocking {
        val storage = FakeComponentStatusStorage().apply {
            lastKnown = LastKnownStatus(
                "device-a",
                listOf(),
                1234L
            )
        }
        val repo = newRepo(storage)
        repo.setDevice("device-a")
        repo.loadPersisted()
        val state = repo.state.value
        assertEquals(ComponentStatusFreshness.LAST_KNOWN, state.freshness)
        assertNull(state.firmwareStatus)
        assertNull(state.authoritative)
        assertNotNull(state.persisted)
    }

    @Test fun compiledCatalogDriversAreNotReportedAsDetectedHardware() = runBlocking {
        val repo = newRepo()
        repo.setDevice("board-a")
        repo.setConnected(true)
        val payload = """
          {"version":1,"gpioAuthority":"firmware","components":[
            {"id":"led.rgb","type":"actuator","interface":"pwm","state":"active","active":true,"pins":[27,25,26]},
            {"id":"sensor.dht22","type":"sensor","interface":"one_wire","state":"catalog","active":false,"pins":[32]},
            {"id":"sensor.soil_moisture","type":"sensor","interface":"adc","state":"catalog","active":false,"pins":[34]}
          ]}
        """.trimIndent()
        assertTrue(repo.ingest(payload))
        assertEquals(setOf("led_rgb"), repo.state.value.confirmedCatalogIds)
        // A user's separately saved DHT22 draft cannot manufacture a detection.
        repo.setDesired(LocalDesiredConfig(listOf(LocalDesiredComponent("dht22", "sensor", listOf(32)))))
        assertEquals(setOf("led_rgb"), repo.state.value.confirmedCatalogIds)
    }

    @Test fun validPayloadBecomesLiveAndPersists() = runBlocking {
        val storage = FakeComponentStatusStorage()
        val repo = newRepo(storage)
        repo.setDevice("device-a")
        repo.setConnected(true)
        assertTrue(repo.ingest(validPayload))
        val state = repo.state.value
        assertEquals(ComponentStatusFreshness.LIVE, state.freshness)
        assertEquals(1, state.authoritative?.size)
        assertEquals(1, storage.saveLastKnownCount)
        assertEquals("device-a", storage.lastKnown?.deviceId)
    }

    @Test fun invalidPayloadNeverOverwritesConfirmedData() = runBlocking {
        val storage = FakeComponentStatusStorage()
        val repo = newRepo(storage)
        repo.setDevice("device-a")
        repo.setConnected(true)
        repo.ingest(validPayload)
        val before = repo.state.value

        assertFalse(repo.ingest("{\"version\":1,\"gpioAuthority\":\"client\"}"))
        assertFalse(repo.ingest("basura"))
        assertFalse(repo.ingest(null))

        val after = repo.state.value
        assertEquals(before.firmwareStatus, after.firmwareStatus)
        assertEquals(ComponentStatusFreshness.LIVE, after.freshness)
        assertNotNull(after.authorityError)
        assertEquals(1, storage.saveLastKnownCount)
    }

    @Test fun disconnectingRevokesLiveStatusButKeepsLastKnown() = runBlocking {
        val storage = FakeComponentStatusStorage()
        val repo = newRepo(storage)
        repo.setDevice("device-a")
        repo.setConnected(true)
        repo.ingest(validPayload)
        repo.setConnected(false)
        val state = repo.state.value
        assertNull(state.firmwareStatus)
        assertNull(state.authoritative)
        assertEquals(ComponentStatusFreshness.LAST_KNOWN, state.freshness)
        assertNotNull(state.persisted)
    }

    @Test fun deviceChangeNeverCarriesAuthoritativeStatusOver() = runBlocking {
        val storage = FakeComponentStatusStorage()
        val repo = newRepo(storage)
        repo.setDevice("device-a")
        repo.setConnected(true)
        repo.ingest(validPayload)

        repo.setDevice("device-b")
        val state = repo.state.value
        assertNull(state.authoritative)
        assertNull(state.persisted)
        assertEquals(ComponentStatusFreshness.NONE, state.freshness)

        assertTrue(repo.ingest(validPayload))
        assertEquals("device-b", repo.state.value.firmwareStatus?.deviceId)
    }

    @Test fun desiredConfigPersistsAndStaysSeparateFromConfirmed() = runBlocking {
        val storage = FakeComponentStatusStorage()
        val repo = newRepo(storage)
        repo.setDevice("device-a")
        repo.setConnected(true)
        repo.ingest(validPayload)
        repo.setDesired(LocalDesiredConfig(listOf(LocalDesiredComponent("dht22", "sensor", listOf(32)))))

        val state = repo.state.value
        assertEquals(1, state.desiredConfig.components.size)
        assertEquals(1, state.authoritative?.size)
        assertEquals(1, storage.saveDesiredCount)
        assertNotEquals(state.desiredConfig.components.first().id, state.authoritative?.first()?.id)
    }

    @Test fun validTelemetryBecomesLiveAndPersists() = runBlocking {
        val storage = FakeComponentStatusStorage()
        val repo = newRepo(storage)
        repo.setDevice("device-a")
        repo.setConnected(true)
        repo.ingest(validPayload)
        assertTrue(repo.ingestTelemetry(validTelemetry))
        val state = repo.state.value
        assertEquals(ComponentStatusFreshness.LIVE, state.telemetryFreshness)
        assertEquals(2, state.authoritativeTelemetry?.size)
        assertEquals(1, storage.saveTelemetryCount)
        assertEquals("device-a", storage.lastKnownTelemetry?.deviceId)
    }

    @Test fun invalidTelemetryNeverOverwritesConfirmedReadings() = runBlocking {
        val storage = FakeComponentStatusStorage()
        val repo = newRepo(storage)
        repo.setDevice("device-a")
        repo.setConnected(true)
        repo.ingest(validPayload)
        repo.ingestTelemetry(validTelemetry)
        val before = repo.state.value

        assertFalse(repo.ingestTelemetry("{\"version\":2,\"measurements\":[]}"))
        assertFalse(repo.ingestTelemetry("basura"))
        assertFalse(repo.ingestTelemetry(null))

        val after = repo.state.value
        assertEquals(before.firmwareTelemetry, after.firmwareTelemetry)
        assertEquals(ComponentStatusFreshness.LIVE, after.telemetryFreshness)
        assertNotNull(after.authorityError)
        assertEquals(1, storage.saveTelemetryCount)
    }

    @Test fun emptyButValidTelemetryIsAuthoritativeButHasNoNumbers() = runBlocking {
        val repo = newRepo()
        repo.setDevice("device-a")
        repo.setConnected(true)
        repo.ingest(validPayload)
        assertTrue(repo.ingestTelemetry("{\"version\":1,\"measurements\":[]}"))
        val state = repo.state.value
        assertEquals(ComponentStatusFreshness.LIVE, state.telemetryFreshness)
        assertTrue(state.authoritativeTelemetry!!.isEmpty())
    }

    @Test fun disconnectingRevokesLiveTelemetryButKeepsLastKnown() = runBlocking {
        val storage = FakeComponentStatusStorage()
        val repo = newRepo(storage)
        repo.setDevice("device-a")
        repo.setConnected(true)
        repo.ingest(validPayload)
        repo.ingestTelemetry(validTelemetry)
        repo.setConnected(false)
        val state = repo.state.value
        assertNull(state.firmwareTelemetry)
        assertNull(state.authoritativeTelemetry)
        assertEquals(ComponentStatusFreshness.LAST_KNOWN, state.telemetryFreshness)
        assertNotNull(state.persistedTelemetry)
    }

    @Test fun deviceChangeDropsLiveTelemetry() = runBlocking {
        val storage = FakeComponentStatusStorage()
        val repo = newRepo(storage)
        repo.setDevice("device-a")
        repo.setConnected(true)
        repo.ingest(validPayload)
        repo.ingestTelemetry(validTelemetry)

        repo.setDevice("device-b")
        assertEquals(ComponentStatusFreshness.NONE, repo.state.value.telemetryFreshness)
        assertNull(repo.state.value.authoritativeTelemetry)
    }

    @Test fun telemetryWithoutDeviceIsRejected() = runBlocking {
        val repo = newRepo()
        assertFalse(repo.ingestTelemetry(validTelemetry))
        assertNotNull(repo.state.value.authorityError)
        assertNull(repo.state.value.firmwareTelemetry)
    }

    @Test fun ingestWithoutDeviceIsRejected() = runBlocking {
        val repo = newRepo()
        assertFalse(repo.ingest(validPayload))
        assertNotNull(repo.state.value.authorityError)
        assertNull(repo.state.value.firmwareStatus)
    }
}
