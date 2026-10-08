package com.rgbcontroller.core.model

import kotlin.math.abs
import kotlin.math.roundToInt

object ColorUtils {
    data class RgbColor(val r: Int, val g: Int, val b: Int, val brightness: Int = 255)

    /** Pure Kotlin HSV conversion; safe to test without an Android runtime. */
    fun hsvToRgb(hue: Float, saturation: Float, value: Float, brightness: Int = 255): RgbColor {
        val h = ((hue % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = v - c
        val (rr, gg, bb) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun byte(n: Float) = ((n + m) * 255f).roundToInt().coerceIn(0, 255)
        return RgbColor(byte(rr), byte(gg), byte(bb), brightness.coerceIn(0, 255))
    }

    fun rgbToHex(r: Int, g: Int, b: Int): String =
        "#%02X%02X%02X".format(r.coerceIn(0,255), g.coerceIn(0,255), b.coerceIn(0,255))

    fun hexToRgb(input: String): RgbColor? {
        if (!Regex("^#[0-9a-fA-F]{6}$").matches(input)) return null
        return RgbColor(
            input.substring(1, 3).toInt(16),
            input.substring(3, 5).toInt(16),
            input.substring(5, 7).toInt(16)
        )
    }

    fun serializeSetCommand(r: Int, g: Int, b: Int, brightness: Int): String {
        require(listOf(r,g,b,brightness).all { it in 0..255 })
        return "SET,$r,$g,$b,$brightness"
    }

    fun serializeOffCommand() = "OFF"
    fun serializeOnCommand() = "ON"
    fun serializeGetCommand() = "GET"
}
