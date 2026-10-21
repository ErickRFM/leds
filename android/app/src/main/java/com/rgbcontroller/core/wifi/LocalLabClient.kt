package com.rgbcontroller.core.wifi

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.rgbcontroller.core.lab.LabExperimentLibrary
import com.rgbcontroller.core.model.RgbDeviceCommand
import com.rgbcontroller.core.model.RgbDeviceState
import com.rgbcontroller.core.model.RgbReadback
import com.rgbcontroller.core.hardware.Esp32BoardPinout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/** Debug-only client for WPA2 SoftAP at 192.168.4.1; no WAN and no TLS. */
class LocalLabClient(private val context: Context) {
    companion object {
        const val BASE = "http://192.168.4.1"
        fun allowedOutput(pin: Int) = Esp32BoardPinout.canWrite(pin)
        fun allowedInput(pin: Int) = Esp32BoardPinout.canRead(pin)
        fun allowedAnalog(pin: Int) = Esp32BoardPinout.canReadAdc1(pin)
        fun validKey(key: String) = Regex("[a-fA-F0-9]{16}").matches(key)
        fun validSequence(steps: String) = LabExperimentLibrary.parse(steps) != null
        fun validSsid(ssid: String) = ssid.isNotBlank() && ssid.toByteArray(Charsets.UTF_8).size <= 32
        fun validPassword(password: String) = password.isEmpty() || password.length in 8..63
    }

    suspend fun status(key: String) = request("GET", "/v2/status", key)
    suspend fun firmwareInfo(key: String) = request("GET", "/v2/firmware", key)
    suspend fun diagnostics(key: String) = request("GET", "/v2/diagnostics", key)
    suspend fun identity(key: String) = request("GET", "/v2/identity", key)
    suspend fun scan(key: String) = request("GET", "/v2/wifi/scan", key, readTimeout = 15000)
    suspend fun connectWifi(key: String, ssid: String, password: String): JSONObject {
        require(validSsid(ssid) && validPassword(password))
        return request("POST", "/v2/wifi/connect", key, mapOf("ssid" to ssid, "password" to password))
    }
    /** Actual ownership snapshot from ESP32; a pin being FREE does not prove wiring. */
    suspend fun gpioStatus(key: String): JSONObject = request("GET", "/v2/gpio/status", key)
    /** Bounded automation only, requires authenticated local SoftAP. Never persistent/OTA. */
    suspend fun automationStatus(key: String): JSONObject =
        request("GET", "/v2/automation/status", key)
    suspend fun startAutomation(
        key: String, intervalSeconds: Int, cycles: Int, startOn: Boolean
    ): JSONObject {
        require(com.rgbcontroller.core.lab.RgbAutomationPolicy.valid(intervalSeconds, cycles)) {
            "Intervalo/ciclos de automatización fuera de límites."
        }
        return request("POST", "/v2/automation/start", key, mapOf(
            "intervalSeconds" to intervalSeconds.toString(),
            "cycles" to cycles.toString(),
            "startOn" to if (startOn) "1" else "0"
        ))
    }
    suspend fun stopAutomation(key: String): JSONObject =
        request("POST", "/v2/automation/stop", key)

    /** Runtime-only environmental RGB, requires the existing authenticated SoftAP.
     * BLE bonding alone never authorizes these operations.
     */
    suspend fun environmentalRgbStatus(key: String): JSONObject =
        request("GET", "/v2/environment/rgb/status", key)

    suspend fun startEnvironmentalRgb(key: String): JSONObject {
        val response = request("POST", "/v2/environment/rgb/start", key)
        check(response.optString("status") == "ENABLED" &&
            response.optBoolean("runtimeOnly")) { "El ESP32 no confirmó el modo ambiental." }
        return response
    }

    suspend fun stopEnvironmentalRgb(key: String): JSONObject {
        val response = request("POST", "/v2/environment/rgb/stop", key)
        check(response.optString("status") == "STOPPED" &&
            response.optBoolean("runtimeOnly")) { "El ESP32 no confirmó la detención." }
        return response
    }


    suspend fun writePin(key: String, pin: Int, value: Boolean): JSONObject {
        require(allowedOutput(pin) && pin !in setOf(25,26,27)) // RGB is reserved.
        return request("POST", "/v2/gpio/write", key,
            mapOf("pin" to pin.toString(), "value" to if (value) "1" else "0"))
    }
    suspend fun readPin(key: String, pin: Int): JSONObject {
        require(allowedInput(pin))
        return request("GET", "/v2/gpio/read?pin=" + pin, key)
    }
    suspend fun readAnalog(key: String, pin: Int): JSONObject {
        require(allowedAnalog(pin))
        return request("GET", "/v2/adc/read?pin=" + pin, key)
    }
    suspend fun resetPins(key: String) = request("POST", "/v2/gpio/reset", key)
    /** Declarative component catalog advertised by the firmware (metadata only). */
    suspend fun componentCatalog(key: String): JSONObject =
        request("GET", "/v2/components/catalog", key)
    /** Freshest read-only telemetry from active components; never fabricated. */
    suspend fun componentTelemetry(key: String): JSONObject =
        request("GET", "/v2/components/telemetry", key)
    /** Authoritative firmware runtime status (resolved pins + lifecycle). */
    suspend fun componentStatus(key: String): JSONObject =
        request("GET", "/v2/components/status", key)

    /** Explicit user action; HTTP ACK confirms the driver ran, not physical pixels. */
    suspend fun showOled(key: String, mode: String, value: String): JSONObject {
        require(mode == "text" || mode == "face")
        require(value.isNotEmpty() && value.length <= 39 &&
            value.all { it.code in 32..126 })
        if (mode == "face") {
            require(value in setOf("happy", "sleepy", "angry", "surprised"))
        }
        val response = request("POST", "/v2/display/oled", key,
            mapOf("mode" to mode, "value" to value))
        check(response.optString("status") == "DISPLAYED" &&
            response.optBoolean("runtimeOnly")) {
            "El ESP32 no confirmó el comando OLED."
        }
        return response
    }

    /** The LDR needs a real 3.3V divider and two distinct ADC1 references.
     * Calibration is runtime-only and requires the existing SoftAP authorization.
     */
    suspend fun calibrateLdr(key: String, darkRaw: Int, brightRaw: Int): JSONObject {
        require(darkRaw in 0..4095 && brightRaw in 0..4095 &&
            brightRaw - darkRaw >= 32) { "Captura primero oscuridad y claridad diferentes." }
        val response = request("POST", "/v2/components/ldr/calibrate", key, mapOf(
            "darkRaw" to darkRaw.toString(), "brightRaw" to brightRaw.toString()
        ))
        check(response.optString("status") == "CALIBRATED" &&
            response.optBoolean("runtimeOnly")) { "El ESP32 no confirmó la calibración LDR." }
        return response
    }


    /**
     * Only acknowledges a runtime GPIO change after an independent firmware
     * readback. A successful HTTP 200 alone is not proof of a new assignment.
     * No pins are persisted across ESP32 resets.
     */
    suspend fun applyComponentPinsConfirmed(
        key: String, id: String, target: List<Int>
    ): JSONObject {
        val names = mapOf(
            "dht11" to "sensor.dht11",
            "dht22" to "sensor.dht22",
            "soil_moisture" to "sensor.soil_moisture",
            "ldr_light" to "sensor.ldr_light",
            "ssd1306_oled" to "display.ssd1306",
            "led_rgb" to "led.rgb"
        )
        val firmwareId = names[id] ?: error("Este componente no admite reasignación en caliente.")
        val count = when (id) {
            "led_rgb" -> 3
            "ssd1306_oled" -> 2
            else -> 1
        }
        require(target.size == count && target.all { it in 0..39 })
        if (id == "led_rgb") {
            require(target.distinct().size == 3 && target.all(::allowedOutput)) {
                "El RGB necesita tres GPIO PWM de salida diferentes."
            }
        }
        val before = componentStatus(key)
        val components = before.optJSONArray("components") ?: error("No hay estado de componentes.")
        fun moduleFor(array: org.json.JSONArray): JSONObject? =
            (0 until array.length()).firstNotNullOfOrNull { index ->
                array.optJSONObject(index)?.takeIf { it.optString("id") == firmwareId }
            }
        fun pinsFor(item: JSONObject): List<Int>? {
            val pins = item.optJSONArray("pins") ?: return null
            return (0 until pins.length()).map { pins.optInt(it, -1) }
        }
        val prior = moduleFor(components) ?: error(
            "El firmware no incluye este driver. Actualiza el ESP32."
        )
        val expected = ComponentApplyCasPolicy.expected(prior, count)
        val next = target + List(maxOf(2, count) - count) { 0 }
        // One nonce per user transaction. The firmware caches successful
        // receipts to avoid applying duplicate POST requests twice.
        val requestId = UUID.randomUUID().toString().replace("-", "").take(16)
        val arguments = mutableMapOf(
            "id" to id,
            "from1" to expected[0].toString(),
            "from2" to expected[1].toString(),
            "to1" to next[0].toString(),
            "to2" to next[1].toString(),
            "requestId" to requestId
        )
        if (count == 3) {
            arguments["from3"] = expected[2].toString()
            arguments["to3"] = next[2].toString()
        }
        val response = try {
            request("POST", "/v2/components/apply", key, arguments)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (refused: LabHttpException) {
            // A definitive 4xx rejection never applies GPIO; 5xx/unknown may
            // occur after a pin change and must NOT be displayed as rejected.
            if (refused is LabHttpException.ServerError ||
                refused is LabHttpException.ProtocolError) {
                throw ComponentApplyUnconfirmedException(
                    "El ESP32 no confirmó el resultado HTTP. Consulta GPIO antes de reintentar.",
                    refused)
            }
            throw refused
        } catch (failure: Exception) {
            throw ComponentApplyUnconfirmedException(
                "Se perdió la respuesta al aplicar GPIO. El cambio pudo realizarse.", failure)
        }
        if (response.optString("status") != "APPLIED" ||
            !response.optBoolean("runtimeOnly") ||
            response.optString("requestId") != requestId) {
            throw ComponentApplyUnconfirmedException(
                "El recibo no coincide con la solicitud. Consulta los GPIO del ESP32.")
        }
        val after = try {
            componentStatus(key)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            throw ComponentApplyUnconfirmedException(
                "Aplicación aceptada, pero no se pudo consultar el estado de los GPIO.", failure)
        }
        val verified = after.optJSONArray("components")?.let(::moduleFor)
            ?.takeIf { it.optBoolean("active") }?.let(::pinsFor)
        if (verified != target) {
            throw ComponentApplyUnconfirmedException(
                "El recibo fue aceptado, pero los GPIO leídos no coinciden.")
        }
        return after
    }
    suspend fun blink(key: String, pin: Int, intervalMs: Int, cycles: Int): JSONObject {
        require(allowedOutput(pin) && pin !in setOf(25,26,27))
        require(intervalMs in 100..2000 && cycles in 1..20)
        return request("POST", "/v2/lab/blink", key, mapOf(
            "pin" to pin.toString(), "intervalMs" to intervalMs.toString(), "cycles" to cycles.toString()))
    }
    suspend fun sequence(key: String, pin: Int, steps: String): JSONObject {
        require(allowedOutput(pin) && validSequence(steps))
        return request("POST", "/v2/lab/sequence", key,
            mapOf("pin" to pin.toString(), "steps" to steps))
    }
    suspend fun stopExperiment(key: String) = request("POST", "/v2/lab/stop", key)
    /** RGB Script V4: authenticated SoftAP debug only; firmware validates again. */
    suspend fun runRgbScript(key: String, source: String): JSONObject {
        require(com.rgbcontroller.core.programs.LabRgbScriptPolicy.validate(source).accepted) {
            "Script RGB fuera de los límites admitidos."
        }
        return request("POST", "/v2/script/run", key, mapOf("source" to source))
    }
    suspend fun rgbScriptStatus(key: String): JSONObject =
        request("GET", "/v2/script/status", key)
    suspend fun stopRgbScript(key: String): JSONObject =
        request("POST", "/v2/script/stop", key)

    suspend fun setRgb(key: String, r: Int, g: Int, b: Int, brightness: Int): JSONObject =
        rgb(key, RgbDeviceCommand.set(r, g, b, brightness))

    /**
     * Confirms the requested RGB values from the ESP32's own /status endpoint.
     * This proves logical state only: physical LED voltage still requires a bench test.
     */
    suspend fun setRgbConfirmed(key: String, r: Int, g: Int, b: Int, brightness: Int): JSONObject {
        setRgb(key, r, g, b, brightness)
        val readback = status(key)
        val actual = RgbDeviceState.parse(readback.optString("rgb"))
        check(RgbReadback.confirmsSet(r, g, b, brightness, actual)) {
            "ESP32 aceptó SET, pero el estado leído no coincide. Comprueba logs y reconecta."
        }
        return readback
    }

    suspend fun startRgbEffect(key: String, name: String, speed: Int): JSONObject =
        rgb(key, RgbDeviceCommand.effect(name, speed))

    suspend fun rgb(key: String, command: String): JSONObject {
        require(RgbDeviceCommand.allowed(command)) { "Invalid RGB command" }
        return request("POST", "/v2/led/command", key, mapOf("command" to command))
    }

    /**
     * Android often prefers cellular when the ESP32's SoftAP has no Internet.
     * Bind each request to the explicitly identified Wi-Fi route; NEVER fall back.
     * This is still a debug laboratory channel, not a production authentication scheme.
     */
    private fun labNetwork(): Network {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return connectivity.allNetworks.firstOrNull { network ->
            val capabilities = connectivity.getNetworkCapabilities(network)
            val gateways = connectivity.getLinkProperties(network)?.routes
                ?.mapNotNull { it.gateway?.hostAddress }.orEmpty()
            LabNetworkPolicy.isEligible(
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
                gateways
            )
        } ?: error("No hay red Wi-Fi del ESP32 con gateway 192.168.4.1. Conecta a ESP32-Control-Lab y acepta mantener Wi-Fi sin Internet.")
    }

    private suspend fun request(
        method: String, path: String, key: String, form: Map<String, String> = emptyMap(),
        readTimeout: Int = 7000
    ): JSONObject = withContext(Dispatchers.IO) {
        require((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            "La API Wi-Fi del laboratorio está deshabilitada fuera de compilaciones debug."
        }
        require(validKey(key)) { "Clave inválida (16 caracteres hexadecimales)." }
        require(path.startsWith("/v2/"))
        val connection = labNetwork().openConnection(URL(BASE + path)) as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 5000
            connection.readTimeout = readTimeout
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("X-Device-Key", key)
            connection.setRequestProperty("Accept", "application/json")
            if (method == "POST") {
                val body = form.entries.joinToString("&") {
                    URLEncoder.encode(it.key, "UTF-8") + "=" +
                        URLEncoder.encode(it.value, "UTF-8")
                }.toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val reason = runCatching { JSONObject(raw).optString("error") }.getOrDefault("HTTP $code")
                throw LabHttpException.fromStatusCode(code, reason)
            }
            JSONObject(raw)
        } finally {
            connection.disconnect()
        }
    }
}

/** A GPIO POST may have executed despite a missing receipt/readback. Never label it rejected. */
class ComponentApplyUnconfirmedException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)
