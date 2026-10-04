package com.gamepad.controller.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import com.gamepad.controller.haptics.HapticsManager
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * VirtualJoystick: High-performance touch joystick with floating/fixed origin,
 * visual knob, deadzone rendering, and normalized WASD output.
 */
@Composable
fun VirtualJoystick(
    size: Dp,
    deadzone: Float = 0.35f,
    sprintThreshold: Float = 0.85f,
    floatingOrigin: Boolean = true,
    hapticsManager: HapticsManager,
    onDirectionChanged: (normX: Float, normY: Float, isSprint: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var activePointerId by remember { mutableStateOf<PointerId?>(null) }
    var originOffset by remember { mutableStateOf(Offset.Zero) }
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    var hasHapticActuated by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .pointerInput(floatingOrigin) {
                val boxRadius = size.toPx() / 2f
                val defaultCenter = Offset(boxRadius, boxRadius)

                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            androidx.compose.ui.input.pointer.PointerEventType.Press -> {
                                val change = event.changes.firstOrNull() ?: continue
                                if (activePointerId == null) {
                                    activePointerId = change.id
                                    originOffset = if (floatingOrigin) change.position else defaultCenter
                                    knobOffset = originOffset
                                    hasHapticActuated = false
                                    change.consume()
                                }
                            }
                            androidx.compose.ui.input.pointer.PointerEventType.Move -> {
                                val change = event.changes.firstOrNull { it.id == activePointerId } ?: continue
                                val delta = change.position - originOffset
                                val dist = hypot(delta.x, delta.y)
                                val angle = atan2(delta.y, delta.x)
                                val maxRadius = boxRadius * 0.85f

                                val clampedDist = min(dist, maxRadius)
                                knobOffset = Offset(
                                    originOffset.x + clampedDist * cos(angle),
                                    originOffset.y + clampedDist * sin(angle)
                                )

                                val normDist = clampedDist / maxRadius
                                val normX = (clampedDist * cos(angle)) / maxRadius
                                val normY = (clampedDist * sin(angle)) / maxRadius

                                val isActive = normDist > deadzone
                                if (isActive && !hasHapticActuated) {
                                    hapticsManager.triggerClick()
                                    hasHapticActuated = true
                                } else if (!isActive) {
                                    hasHapticActuated = false
                                }

                                val isSprint = normY < -sprintThreshold
                                if (isActive) {
                                    onDirectionChanged(normX, normY, isSprint)
                                } else {
                                    onDirectionChanged(0f, 0f, false)
                                }
                                change.consume()
                            }
                            androidx.compose.ui.input.pointer.PointerEventType.Release,
                            androidx.compose.ui.input.pointer.PointerEventType.Cancel -> {
                                val change = event.changes.firstOrNull { it.id == activePointerId }
                                if (change != null) {
                                    activePointerId = null
                                    originOffset = defaultCenter
                                    knobOffset = defaultCenter
                                    hasHapticActuated = false
                                    onDirectionChanged(0f, 0f, false)
                                    change.consume()
                                }
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = if (originOffset == Offset.Zero) Offset(size.toPx() / 2f, size.toPx() / 2f) else originOffset
            val outerRadius = (size.toPx() / 2f) * 0.85f
            val deadzoneRadius = outerRadius * deadzone
            val currentKnob = if (knobOffset == Offset.Zero) center else knobOffset

            // Base outer boundary ring
            drawCircle(
                color = Color(0x3310141D),
                radius = outerRadius,
                center = center
            )
            drawCircle(
                color = Color(0x66FFFFFF),
                radius = outerRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )

            // Deadzone ring indicator
            drawCircle(
                color = Color(0x3300E5FF),
                radius = deadzoneRadius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // Thumb knob
            val knobRadius = outerRadius * 0.35f
            drawCircle(
                color = Color(0xCC00E5FF),
                radius = knobRadius,
                center = currentKnob
            )
            drawCircle(
                color = Color(0xFFFFFFFF),
                radius = knobRadius,
                center = currentKnob,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
