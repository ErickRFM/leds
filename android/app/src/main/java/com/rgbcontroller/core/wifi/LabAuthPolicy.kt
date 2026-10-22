package com.rgbcontroller.core.wifi

object LabAuthPolicy {
    fun validKey(key: String) = Regex("[a-fA-F0-9]{16}").matches(key)
}
