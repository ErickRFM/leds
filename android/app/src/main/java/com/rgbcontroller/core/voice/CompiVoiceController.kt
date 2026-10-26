package com.rgbcontroller.core.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Independent local speech output; never blocks the ESP32 DFPlayer. */
class CompiVoiceController(context: Context) : TextToSpeech.OnInitListener {
    private val preferences = context.applicationContext.getSharedPreferences(
        "compi_voice", Context.MODE_PRIVATE
    )
    private var tts: TextToSpeech? = null
    private var initialized = false
    private var closed = false
    private var device: String? = null
    private val events = EnvironmentalSpeechEngine()
    private var pendingInitialization = false
    private var lastEnabled = false
    private val recentAnnouncements = mutableMapOf<String, Long>()

    init {
        tts = TextToSpeech(context.applicationContext, this)
        if (pendingInitialization) configureVoice()
    }

    override fun onInit(status: Int) {
        if (closed || status != TextToSpeech.SUCCESS) return
        if (tts == null) pendingInitialization = true else configureVoice()
    }

    private fun configureVoice() {
        val engine = tts ?: return
        val result = engine.setLanguage(Locale("es", "MX"))
        initialized = result != TextToSpeech.LANG_MISSING_DATA &&
            result != TextToSpeech.LANG_NOT_SUPPORTED
        pendingInitialization = false
    }

    fun accept(
        deviceId: String?,
        connected: Boolean,
        temperatureC: Float?,
        lightPercent: Float?,
        sampleAtMs: Long,
        nowMs: Long
    ) {
        if (closed) return
        if (!connected || deviceId.isNullOrEmpty()) {
            tts?.stop()
            device = null
            recentAnnouncements.clear()
            events.reset()
            return
        }
        if (device != deviceId) {
            tts?.stop()
            device = deviceId
            recentAnnouncements.clear()
            events.reset()
        }
        val enabled = preferences.getBoolean("phone_enabled", false)
        if (!enabled) {
            if (lastEnabled) tts?.stop()
            recentAnnouncements.clear()
            events.reset()
            lastEnabled = false
            return
        }
        if (!lastEnabled) events.reset()
        lastEnabled = true
        val cues = events.evaluate(temperatureC, lightPercent, sampleAtMs, nowMs)
        if (!initialized) return
        val engine = tts ?: return
        cues.forEachIndexed { index, cue ->
            val key = "${deviceId}:${cue.name}"
            val previous = recentAnnouncements[key]
            if (previous != null && nowMs >= previous && nowMs - previous < 60_000L) {
                return@forEachIndexed
            }
            recentAnnouncements[key] = nowMs
            if (recentAnnouncements.size > 12) {
                val oldest = recentAnnouncements.minByOrNull { it.value }?.key
                if (oldest != null) recentAnnouncements.remove(oldest)
            }
            engine.speak(
                cue.message,
                if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                null,
                "compi-${cue.name}-${sampleAtMs}"
            )
        }
    }

    fun close() {
        if (closed) return
        closed = true
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
