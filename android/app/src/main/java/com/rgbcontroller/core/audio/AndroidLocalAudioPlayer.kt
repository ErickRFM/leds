package com.rgbcontroller.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Android playback of user-selected local document URIs. No ESP32 output. */
class AndroidLocalAudioPlayer(private val context: Context) : AutoCloseable {
    data class State(
        val phase: Phase = Phase.IDLE,
        val description: String = "Sin reproducción"
    )
    enum class Phase { IDLE, PREPARING, PLAYING, PAUSED, ERROR }

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState
    private var player: MediaPlayer? = null
    private var volume = 0.6f
    private var closed = false

    fun play(uri: Uri, onCompleted: () -> Unit = {}): Boolean {
        if (closed || uri.scheme != "content") return false
        releasePlayer()
        mutableState.value = State(Phase.PREPARING, "Preparando audio…")
        return try {
            val media = MediaPlayer()
            player = media
            media.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            media.setDataSource(context.applicationContext, uri)
            media.setVolume(volume, volume)
            media.setOnPreparedListener {
                if (player !== it || closed) return@setOnPreparedListener
                try {
                    it.start()
                    mutableState.value = State(Phase.PLAYING, "Reproduciendo")
                } catch (failure: Exception) {
                    mutableState.value = State(Phase.ERROR, failure.message ?: "Error al iniciar audio")
                    releasePlayer()
                }
            }
            media.setOnCompletionListener {
                if (player === it && !closed) {
                    mutableState.value = State(Phase.IDLE, "Pista finalizada")
                    onCompleted()
                }
            }
            media.setOnErrorListener { p, what, extra ->
                if (player === p) {
                    mutableState.value = State(
                        Phase.ERROR, "Formato o archivo no compatible ($what/$extra)"
                    )
                    releasePlayer()
                }
                true
            }
            media.prepareAsync()
            true
        } catch (failure: Exception) {
            mutableState.value = State(
                Phase.ERROR, failure.message ?: "No se pudo abrir el archivo"
            )
            releasePlayer()
            false
        }
    }

    fun pause(): Boolean {
        val media = player ?: return false
        return try {
            if (!media.isPlaying) return false
            media.pause()
            mutableState.value = State(Phase.PAUSED, "Pausado")
            true
        } catch (_: IllegalStateException) { false }
    }

    fun resume(): Boolean {
        val media = player ?: return false
        return try {
            media.start()
            mutableState.value = State(Phase.PLAYING, "Reproduciendo")
            true
        } catch (_: IllegalStateException) { false }
    }

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        try { player?.setVolume(volume, volume) } catch (_: IllegalStateException) {}
    }

    fun stop() {
        releasePlayer()
        if (!closed) mutableState.value = State()
    }

    private fun releasePlayer() {
        val old = player
        player = null
        if (old != null) {
            try { old.reset() } catch (_: Exception) {}
            try { old.release() } catch (_: Exception) {}
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        releasePlayer()
        mutableState.value = State()
    }
}
