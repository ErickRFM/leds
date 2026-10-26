package com.rgbcontroller.core.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rgbcontroller.feature.hardwarelab.ComponentDashboardViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Opt-in connected-device foreground sampling. Never attempts to recreate
 * pairing secrets or a Wi-Fi session after process death.
 */
class CompiVoiceForegroundService : Service() {
    companion object {
        const val ACTION_STOP = "com.rgbcontroller.voice.STOP"
        const val CHANNEL = "compi_voice_live"
        @Volatile var running: Boolean = false
            private set
        @Volatile var dashboard: ComponentDashboardViewModel? = null
        @Volatile var controller: CompiVoiceController? = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var polling: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Compi Voice en segundo plano",
                NotificationManager.IMPORTANCE_LOW)
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Compi Voice activo")
            .setContentText("Lecturas del ESP32 y avisos ambientales")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(6001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else startForeground(6001, notification)
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || dashboard == null || controller == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (polling?.isActive == true) return START_NOT_STICKY
        polling = scope.launch {
            val model = dashboard ?: return@launch
            val speech = controller ?: return@launch
            while (isActive) {
                try {
                    model.onConnectionChanged()
                    val state = model.uiState.value
                    val readings = state.authoritativeTelemetry.orEmpty()
                    fun value(type: String): Float? = readings.firstOrNull {
                        it.type == type && it.valid && it.value.isFinite() &&
                            it.status.equals("fresh", ignoreCase = true)
                    }?.value
                    speech.accept(
                        state.deviceId, state.connected, value("temperature"),
                        value("ambient_light"), state.firmwareTelemetry?.receivedAtMs ?: 0L,
                        System.currentTimeMillis(),
                        state.firmwareVoiceEvent?.id, state.firmwareVoiceEvent?.cue
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A lost link never fabricates telemetry or keeps announcing.
                    speech.pause()
                }
                delay(3_000L)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        polling?.cancel()
        scope.cancel()
        controller?.pause()
        super.onDestroy()
    }
}
