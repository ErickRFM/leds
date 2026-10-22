package com.rgbcontroller.core.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** Phone-side speaker only. No remote ESP32 audio permissions or GPIO side effects. */
class AndroidEnvironmentalSpeaker(context: Context) : AutoCloseable {
    private val mutableReady = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = mutableReady
    private var closed = false
    private var engine: TextToSpeech? = null

    init {
        engine = TextToSpeech(context.applicationContext) { result ->
            val tts = engine
            if (!closed && result == TextToSpeech.SUCCESS && tts != null) {
                val language = tts.setLanguage(Locale.forLanguageTag("es-MX"))
                mutableReady.value = language != TextToSpeech.LANG_MISSING_DATA &&
                    language != TextToSpeech.LANG_NOT_SUPPORTED
            } else {
                mutableReady.value = false
            }
        }
    }

    fun speak(text: String): Boolean {
        if (!mutableReady.value || closed || text.isBlank()) return false
        return engine?.speak(
            text.take(600), TextToSpeech.QUEUE_FLUSH,
            null, "compi-environmental-report"
        ) == TextToSpeech.SUCCESS
    }

    override fun close() {
        if (closed) return
        closed = true
        mutableReady.value = false
        engine?.stop()
        engine?.shutdown()
        engine = null
    }
}
