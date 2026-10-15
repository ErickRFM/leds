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

    @Test fun ingestWithoutDeviceIsRejected() = runBlocking {
        val repo = newRepo()
        assertFalse(repo.ingest(validPayload))
        assertNotNull(repo.state.value.authorityError)
        assertNull(repo.state.value.firmwareStatus)
    }
}
