package com.rgbcontroller.core.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Phone-only speech output. It never sends a command to the DFPlayer and its
 * failure cannot prevent physical audio or sensor sampling.
 */
class CompiVoiceController(context: Context) : TextToSpeech.OnInitListener {
    private val preferences = context.applicationContext.getSharedPreferences("compi_voice", Context.MODE_PRIVATE)
    private val tts: TextToSpeech
    private var ready = false
    private var closed = false
    private var device: String? = null
    private val events = EnvironmentalSpeechEngine()

    init { tts = TextToSpeech(context.applicationContext, this) }

    override fun onInit(status: Int) {
        if (closed || status != TextToSpeech.SUCCESS) return
        val result = tts.setLanguage(Locale("es", "MX"))
        ready = result != TextToSpeech.LANG_MISSING_DATA &&
            result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    fun accept(
        deviceId: String?,
        connected: Boolean,
        temperatureC: Float?,
        lightPercent: Float?,
        sampleAtMs: Long,
        nowMs: Long
    ) {
        if (!connected || deviceId == null) {
            if (!closed) tts.stop()
            device = null
            events.reset()
            return
        }
        if (device != deviceId) {
            device = deviceId
            events.reset()
        }
        val cues = events.evaluate(temperatureC, lightPercent, sampleAtMs, nowMs)
        if (!ready || closed || !preferences.getBoolean("phone_enabled", false)) return
        for (cue in cues) {
            // Audio from other phone apps takes precedence; no backlog of old
            // environmental events should be spoken on reconnection.
            tts.speak(cue.message, TextToSpeech.QUEUE_FLUSH, null,
                "compi-${cue.name}-${sampleAtMs}")
        }
    }

    fun close() {
        closed = true
        tts.stop()
        tts.shutdown()
    }
}
