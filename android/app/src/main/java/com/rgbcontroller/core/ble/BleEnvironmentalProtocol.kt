package com.rgbcontroller.core.ble

/** Exact BLE environmental frames. Owner authentication is enforced by both ends. */
object BleEnvironmentalProtocol {
    enum class Action(val frame: String) {
        GET("ENV,GET"), START("ENV,START"), STOP("ENV,STOP")
    }

    sealed interface Reply {
        data class State(val enabled: Boolean) : Reply
        data object Busy : Reply
    }

    fun parseReply(frame: String): Reply? = when (frame) {
        "ENV,ON" -> Reply.State(true)
        "ENV,OFF" -> Reply.State(false)
        "ENV,BUSY" -> Reply.Busy
        else -> null
    }
}
