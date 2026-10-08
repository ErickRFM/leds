package com.rgbcontroller.core.model

import android.graphics.Color

object ColorUtils {
    data class RgbColor(val r: Int, val g: Int, val b: Int, val brightness: Int)

    fun hsvToRgb(hue: Float, saturation: Float, value: Float, brightness: Int = 255): RgbColor {
        val androidColor = Color.HSVToColor(floatArrayOf(hue, saturation, value))
        val r = Color.red(androidColor)
        val g = Color.green(androidColor)
        val b = Color.blue(androidColor)
        return RgbColor(r, g, b, brightness)
    }

    fun rgbToHex(r: Int, g: Int, b: Int): String {
        return String.format("#%02X%02X%02X", r, g, b)
    }

    fun serializeSetCommand(r: Int, g: Int, b: Int, brightness: Int): String {
        return "SET,$r,$g,$b,$brightness"
    }

    fun serializeOffCommand(): String {
        return "OFF"
    }

    fun serializeOnCommand(): String {
        return "ON"
    }

    fun serializeGetCommand(): String {
        return "GET"
    }
}
