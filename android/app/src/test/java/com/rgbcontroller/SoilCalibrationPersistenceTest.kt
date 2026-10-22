package com.rgbcontroller

import com.rgbcontroller.core.components.calibration.SoilCalibrationCodec
import com.rgbcontroller.core.components.calibration.StoredSoilCalibration
import com.rgbcontroller.core.components.status.ComponentStatusRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoilCalibrationPersistenceTest {

    private val record = StoredSoilCalibration("device-a", dryRaw = 40, wetRaw = 1200)

    @Test fun codecRoundTrip() {
        val decoded = SoilCalibrationCodec.decode(SoilCalibrationCodec.encode(record))
        assertEquals(record, decoded)
    }

    @Test fun codecRejectsUnknownVersionAndBlankDevice() {
        assertNull(SoilCalibrationCodec.decode("{\"version\":99,\"deviceId\":\"device-a\"}"))
        assertNull(SoilCalibrationCodec.decode("{\"version\":1,\"deviceId\":\"\"}"))
        assertNull(SoilCalibrationCodec.decode(null))
        assertNull(SoilCalibrationCodec.decode("not json"))
    }

    @Test fun recordOnlyAppliesToItsDeviceAndModule() {
        assertTrue(record.appliesTo("device-a"))
        assertFalse(record.appliesTo("device-b"))
        assertFalse(record.appliesTo("device-a", componentId = "dht22"))
    }

    @Test fun repositoryBindsCalibrationToCurrentDevice() = runBlocking {
        val storage = FakeComponentStatusStorage()
        val repo = ComponentStatusRepository(storage, clock = { 1L })
        repo.setDevice("device-a")
        assertTrue(repo.setSoilCalibration(dryRaw = 40, wetRaw = 1200))
        val state = repo.state.value
        assertNotNull(state.activeSoilCalibration)
        assertEquals(40, state.activeSoilCalibration?.dryRaw)
        assertEquals(1200, state.activeSoilCalibration?.wetRaw)
        assertEquals(record.deviceId, storage.soilCalibration?.deviceId)
    }

    @Test fun switchingDeviceInvalidatesStoredCalibration() = runBlocking {
        val storage = FakeComponentStatusStorage()
        val repo = ComponentStatusRepository(storage, clock = { 1L })
        repo.setDevice("device-a")
        repo.setSoilCalibration(dryRaw = 40, wetRaw = 1200)
        repo.setDevice("device-b")
        val state = repo.state.value
        assertNull(state.activeSoilCalibration)
        assertNotNull(state.soilCalibration)
        // Still persisted: it is not deleted, only ignored for the other device.
        assertEquals("device-a", storage.soilCalibration?.deviceId)
    }

    @Test fun invalidPairIsRejectedAndNeverStored() = runBlocking {
        val storage = FakeComponentStatusStorage()
        val repo = ComponentStatusRepository(storage, clock = { 1L })
        repo.setDevice("device-a")
        assertFalse(repo.setSoilCalibration(dryRaw = 5, wetRaw = 5))
        assertNull(storage.soilCalibration)
        assertNull(repo.state.value.soilCalibration)
    }

    @Test fun calibrationIsRejectedWithoutADevice() = runBlocking {
        val storage = FakeComponentStatusStorage()
        val repo = ComponentStatusRepository(storage, clock = { 1L })
        assertFalse(repo.setSoilCalibration(dryRaw = 40, wetRaw = 1200))
        assertNull(storage.soilCalibration)
    }

    @Test fun loadPersistedRestoresStoredCalibration() = runBlocking {
        val storage = FakeComponentStatusStorage().apply { soilCalibration = record }
        val repo = ComponentStatusRepository(storage, clock = { 1L })
        repo.setDevice("device-a")
        repo.loadPersisted()
        assertNotNull(repo.state.value.activeSoilCalibration)
    }

    @Test fun clearRemovesCalibration() = runBlocking {
        val storage = FakeComponentStatusStorage()
        val repo = ComponentStatusRepository(storage, clock = { 1L })
        repo.setDevice("device-a")
        repo.setSoilCalibration(dryRaw = 40, wetRaw = 1200)
        repo.clearSoilCalibration()
        assertNull(repo.state.value.soilCalibration)
        assertNull(storage.soilCalibration)
    }
}
