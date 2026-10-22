package com.rgbcontroller.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Mascot states representing climate, system, music, and connectivity status.
 */
enum class CompiMascotState {
    HAPPY, COMFORTABLE, COLD, HOT, SLEEPY, ALERT, LISTENING, PLAYING, OFFLINE
}

/**
 * Original Compi Character / Mascot Component
 * Features a cute robot character with glowing display eyes, leaf antenna, and expression animations.
 */
@Composable
fun CompiMascot(
    state: CompiMascotState,
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
    speechText: String? = null,
    reducedMotion: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mascot_anim")
    val floatY by infiniteTransition.animateFloat(
        initialValue = if (reducedMotion) 0f else -3f,
        targetValue = if (reducedMotion) 0f else 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floating"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Optional Speech Bubble Above Character
        AnimatedVisibility(
            visible = !speechText.isNullXorBlank(),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            speechText?.let { text ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF222B3F),
                    shadowElevation = 4.dp,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xFFE2E8F0),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Mascot Canvas Character
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer { translationY = floatY },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(size)) {
                val w = size.toPx()
                val h = size.toPx()
                val cx = w / 2f
                val cy = h * 0.55f

                // Body Outer Glow / Shadow
                drawCircle(
                    color = when (state) {
                        CompiMascotState.COLD -> Color(0x3346C6EF)
                        CompiMascotState.HOT -> Color(0x33FF5252)
                        CompiMascotState.COMFORTABLE, CompiMascotState.HAPPY -> Color(0x3327D987)
                        CompiMascotState.ALERT -> Color(0x33FFB84D)
                        CompiMascotState.PLAYING, CompiMascotState.LISTENING -> Color(0x33A88BFF)
                        CompiMascotState.OFFLINE, CompiMascotState.SLEEPY -> Color(0x1F718096)
                    },
                    radius = w * 0.42f,
                    center = Offset(cx, cy)
                )

                // Antenna Stem & Sprout Leaf
                val antennaPath = Path().apply {
                    moveTo(cx, cy - h * 0.28f)
                    cubicTo(
                        cx - 10f, cy - h * 0.42f,
                        cx + 10f, cy - h * 0.44f,
                        cx, cy - h * 0.46f
                    )
                }
                drawPath(antennaPath, color = Color(0xFF3B4866), style = Stroke(width = 5f))

                // Green Sprout Leaf at Antenna Tip
                val leafPath = Path().apply {
                    moveTo(cx, cy - h * 0.46f)
                    cubicTo(
                        cx + w * 0.15f, cy - h * 0.56f,
                        cx + w * 0.1f, cy - h * 0.40f,
                        cx, cy - h * 0.46f
                    )
                }
                drawPath(
                    leafPath,
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF27D987), Color(0xFF1B5E3C)),
                        center = Offset(cx + 10f, cy - h * 0.48f)
                    )
                )

                // Robot Head Shell (Rounded Box)
                val headWidth = w * 0.72f
                val headHeight = h * 0.55f
                val headLeft = cx - headWidth / 2f
                val headTop = cy - headHeight / 2f

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF2A324B), Color(0xFF1B2132))
                    ),
                    topLeft = Offset(headLeft, headTop),
                    size = Size(headWidth, headHeight),
                    cornerRadius = CornerRadius(headWidth * 0.28f)
                )
                drawRoundRect(
                    color = Color(0xFF3D4968),
                    topLeft = Offset(headLeft, headTop),
                    size = Size(headWidth, headHeight),
                    cornerRadius = CornerRadius(headWidth * 0.28f),
                    style = Stroke(width = 3.5f)
                )

                // Inner Screen Glass
                val screenWidth = headWidth * 0.82f
                val screenHeight = headHeight * 0.72f
                val screenLeft = cx - screenWidth / 2f
                val screenTop = cy - screenHeight / 2f + 2f

                drawRoundRect(
                    color = Color(0xFF0D121D),
                    topLeft = Offset(screenLeft, screenTop),
                    size = Size(screenWidth, screenHeight),
                    cornerRadius = CornerRadius(screenWidth * 0.22f)
                )

                // Face Expression Eyes & Mouth Rendering
                val eyeColor = when (state) {
                    CompiMascotState.COLD -> Color(0xFF46C6EF)
                    CompiMascotState.HOT -> Color(0xFFFF6B4A)
                    CompiMascotState.HAPPY, CompiMascotState.COMFORTABLE -> Color(0xFF27D987)
                    CompiMascotState.ALERT -> Color(0xFFFFB84D)
                    CompiMascotState.PLAYING, CompiMascotState.LISTENING -> Color(0xFFA88BFF)
                    CompiMascotState.SLEEPY -> Color(0xFF8899B8)
                    CompiMascotState.OFFLINE -> Color(0xFF4A5568)
                }

                val eyeRadius = screenWidth * 0.12f
                val leftEyeX = cx - screenWidth * 0.25f
                val rightEyeX = cx + screenWidth * 0.25f
                val eyeY = screenTop + screenHeight * 0.42f

                when (state) {
                    CompiMascotState.SLEEPY, CompiMascotState.OFFLINE -> {
                        // Sleeping Eyes (Horizontal Bars/Curves)
                        drawLine(
                            color = eyeColor,
                            start = Offset(leftEyeX - eyeRadius, eyeY),
                            end = Offset(leftEyeX + eyeRadius, eyeY),
                            strokeWidth = 5f
                        )
                        drawLine(
                            color = eyeColor,
                            start = Offset(rightEyeX - eyeRadius, eyeY),
                            end = Offset(rightEyeX + eyeRadius, eyeY),
                            strokeWidth = 5f
                        )
                    }
                    CompiMascotState.COLD -> {
                        // Frosty Eyes (Lower Arcs)
                        drawCircle(eyeColor, radius = eyeRadius, center = Offset(leftEyeX, eyeY))
                        drawCircle(eyeColor, radius = eyeRadius, center = Offset(rightEyeX, eyeY))
                        // Sweat / Frost Drop
                        drawCircle(Color(0xFF70D6FF), radius = 3.5f, center = Offset(rightEyeX + 12f, eyeY + 10f))
                    }
                    else -> {
                        // Glowing Normal / Happy Eyes
                        drawCircle(eyeColor, radius = eyeRadius, center = Offset(leftEyeX, eyeY))
                        drawCircle(eyeColor, radius = eyeRadius, center = Offset(rightEyeX, eyeY))
                        // Eye Highlights
                        drawCircle(Color.White, radius = eyeRadius * 0.35f, center = Offset(leftEyeX - 2.5f, eyeY - 2.5f))
                        drawCircle(Color.White, radius = eyeRadius * 0.35f, center = Offset(rightEyeX - 2.5f, eyeY - 2.5f))
                    }
                }

                // Mouth Curve
                val mouthY = eyeY + screenHeight * 0.25f
                val mouthPath = Path().apply {
                    moveTo(cx - 8f, mouthY)
                    quadraticBezierTo(cx, mouthY + 8f, cx + 8f, mouthY)
                }
                drawPath(mouthPath, color = eyeColor, style = Stroke(width = 4f))

                // Rosy Cheeks
                if (state == CompiMascotState.HAPPY || state == CompiMascotState.COMFORTABLE) {
                    drawCircle(Color(0x44FF8AA8), radius = 6f, center = Offset(leftEyeX - 12f, eyeY + 10f))
                    drawCircle(Color(0x44FF8AA8), radius = 6f, center = Offset(rightEyeX + 12f, eyeY + 10f))
                }
            }
        }
    }
}

private fun String?.isNullXorBlank(): Boolean = this.isNullOrBlank()
