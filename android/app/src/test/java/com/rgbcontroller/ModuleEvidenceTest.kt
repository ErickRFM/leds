package com.rgbcontroller

import com.rgbcontroller.core.components.model.ComponentMeasurement
import com.rgbcontroller.core.components.model.ComponentRuntimeStatus
import com.rgbcontroller.core.components.status.*
import org.junit.Assert.*
import org.junit.Test

/** No Compose; validity and physical-presence messaging must remain deterministic. */
class ModuleEvidenceTest {
    private fun status(id: String, active: Boolean = true) =
        ComponentRuntimeStatus(id, if (id == "led.rgb") "actuator" else "sensor",
            if (id == "led.rgb") "pwm" else "one_wire",
            if (active) "active" else "catalog", active,
            if (active) listOf(16) else emptyList())

    private fun measurement(type: String, valid: Boolean = true, result: String = "fresh") =
        ComponentMeasurement(type, result, valid, 21.2f, "C", 1234)

    private fun state(model: String = "dht11", connected: Boolean = true,
                      temperatureValid: Boolean = true,
                      humidityValid: Boolean = true,
                      bothActive: Boolean = false) : ComponentDashboardState {
        val active = listOf(status("sensor.$model")) +
            if (bothActive) listOf(status("sensor.dht22")) else emptyList()
        return ComponentDashboardState(
            deviceId = "esp32-a",
            connected = connected,
            firmwareStatus = FirmwareReportedStatus("esp32-a", active, 1234),
            firmwareTelemetry = FirmwareReportedTelemetry(
                "esp32-a", listOf(
                    measurement("temperature", temperatureValid),
                    measurement("humidity", humidityValid)
                ), 1234
            )
        )
    }

    @Test fun realDht11CanBeShownAsResponding() {
        assertTrue(ModuleEvidence.dhtResponding(state(), "dht11"))
        assertEquals("Sensor respondiendo · datos en vivo",
            ModuleEvidence.moduleStatus("dht11", true, true, true))
    }

    @Test fun missingOrStaleMeasurementsCannotClaimPhysicalPresence() {
        assertFalse(ModuleEvidence.dhtResponding(state(temperatureValid = false), "dht11"))
        assertFalse(ModuleEvidence.dhtResponding(state(humidityValid = false), "dht11"))
        assertFalse(ModuleEvidence.dhtResponding(state(connected = false), "dht11"))
        val cached = state().copy(
            firmwareStatus = null, firmwareTelemetry = null,
            lastKnownStatus = LastKnownStatus("esp32-a",
                listOf(status("sensor.dht11")), 1234)
        )
        assertFalse(ModuleEvidence.dhtResponding(cached, "dht11"))
        assertEquals("Último estado guardado · no en vivo",
            ModuleEvidence.moduleStatus("dht11", true, false, false))
    }

    @Test fun wrongOrAmbiguousSensorModelDoesNotClaimReading() {
        assertFalse(ModuleEvidence.dhtResponding(state(), "dht22"))
        assertFalse(ModuleEvidence.dhtResponding(state(bothActive = true), "dht11"))
        assertFalse(ModuleEvidence.dhtResponding(state(), "soil_moisture"))
    }

    @Test fun ledAndConnectedButtonNeverPretendPhysicalDetection() {
        assertTrue(ModuleEvidence.moduleStatus("led_rgb", true, true, false)
            .contains("presencia LED no verificable"))
        assertEquals("Gestionar", ModuleEvidence.connectionAction(true, false))
        assertEquals("Gestionar", ModuleEvidence.connectionAction(false, true))
        assertEquals("Conectar", ModuleEvidence.connectionAction(false, false))
    }
}
