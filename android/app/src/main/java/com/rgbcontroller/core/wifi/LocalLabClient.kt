package com.rgbcontroller.core.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.rgbcontroller.core.lab.LabExperimentLibrary
import com.rgbcontroller.core.model.RgbDeviceCommand
import com.rgbcontroller.core.model.RgbDeviceState
import com.rgbcontroller.core.model.RgbReadback
import com.rgbcontroller.core.hardware.Esp32BoardPinout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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
    suspend fun scan(key: String) = request("GET", "/v2/wifi/scan", key, readTimeout = 15000)
    suspend fun connectWifi(key: String, ssid: String, password: String): JSONObject {
        require(validSsid(ssid) && validPassword(password))
        return request("POST", "/v2/wifi/connect", key, mapOf("ssid" to ssid, "password" to password))
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
                val reason = runCatching { JSONObject(raw).optString("error") }.getOrDefault("HTTP " + code)
                error("ESP32 rechazó la operación: " + reason)
            }
            JSONObject(raw)
        } finally {
            connection.disconnect()
        }
    }
}
