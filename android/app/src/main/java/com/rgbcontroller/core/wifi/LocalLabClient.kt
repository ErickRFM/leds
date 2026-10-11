package com.rgbcontroller.core.wifi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Debug-only client for WPA2 SoftAP at 192.168.4.1; no WAN and no TLS. */
class LocalLabClient {
    companion object {
        const val BASE = "http://192.168.4.1"
        fun allowedOutput(pin: Int) = pin in setOf(18,19,21,22,23)
        fun allowedInput(pin: Int) = allowedOutput(pin) || pin in setOf(34,35,36,39)
        fun allowedAnalog(pin: Int) = pin in setOf(32,33,34,35,36,39)
        fun validKey(key: String) = Regex("[a-fA-F0-9]{16}").matches(key)
        fun validSequence(steps: String): Boolean {
            if (steps.isBlank() || steps.length > 120 || steps.endsWith(",")) return false
            val parts = steps.split(',')
            if (parts.size !in 1..16) return false
            var total = 0
            for (part in parts) {
                val fields = part.split(':')
                if (fields.size != 2 || fields[0] !in listOf("0", "1")) return false
                if (!fields[1].all(Char::isDigit) || fields[1].length !in 3..4) return false
                val duration = fields[1].toIntOrNull() ?: return false
                if (duration !in 100..2000) return false
                total += duration
                if (total > 20000) return false
            }
            return true
        }
        fun validSsid(ssid: String) = ssid.isNotBlank() && ssid.toByteArray(Charsets.UTF_8).size <= 32
        fun validPassword(password: String) = password.isEmpty() || password.length in 8..63
    }

    suspend fun status(key: String) = request("GET", "/v2/status", key)
    suspend fun firmwareInfo(key: String) = request("GET", "/v2/firmware", key)
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
    suspend fun rgb(key: String, command: String): JSONObject {
        require(command in setOf("ON", "OFF", "GET", "FX,STOP", "FX,RAINBOW,120"))
        return request("POST", "/v2/led/command", key, mapOf("command" to command))
    }

    private suspend fun request(
        method: String, path: String, key: String, form: Map<String, String> = emptyMap(),
        readTimeout: Int = 7000
    ): JSONObject = withContext(Dispatchers.IO) {
        require(validKey(key)) { "Clave inválida (16 caracteres hexadecimales)." }
        require(path.startsWith("/v2/"))
        val connection = URL(BASE + path).openConnection() as HttpURLConnection
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
