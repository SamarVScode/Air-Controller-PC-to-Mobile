package com.gamepad.controller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamepad.controller.data.ButtonBehavior
import com.gamepad.controller.haptics.HapticsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TouchButton: Low-latency responsive touch HUD button with pressed state
 * feedback, haptic actuation, and support for Hold, Toggle, and Tap behaviors.
 * 
 * Supports fire-and-drag look deltas (rfire) when allowDragLook = true.
 */
@Composable
fun TouchButton(
    label: String,
    behavior: ButtonBehavior,
    hapticsManager: HapticsManager,
    onStateChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    allowDragLook: Boolean = false,
    onLookDelta: ((Float, Float) -> Unit)? = null
) {
    var isPressedVisual by remember { mutableStateOf(false) }
    var isToggledOn by remember { mutableStateOf(false) }
    var trackingPointerId by remember { mutableStateOf<PointerId?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val isActive = if (behavior == ButtonBehavior.TOGGLE) isToggledOn else isPressedVisual

    val backgroundColor = when {
        isActive -> Color(0xCC00E5FF)
        else -> Color(0x6610141D)
    }

    val borderColor = when {
        isActive -> Color(0xFFFFFFFF)
        else -> Color(0x55FFFFFF)
    }

    val textColor = when {
        isActive -> Color(0xFF000000)
        else -> Color(0xFFEEEEEE)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.5.dp, borderColor, CircleShape)
            .pointerInput(behavior, allowDragLook) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            androidx.compose.ui.input.pointer.PointerEventType.Press -> {
                                val change = event.changes.firstOrNull() ?: continue
                                if (trackingPointerId == null) {
                                    trackingPointerId = change.id
                                    hapticsManager.triggerClick()

                                    when (behavior) {
                                        ButtonBehavior.HOLD -> {
                                            isPressedVisual = true
                                            onStateChange(true)
                                        }
                                        ButtonBehavior.TOGGLE -> {
                                            isToggledOn = !isToggledOn
                                            onStateChange(isToggledOn)
                                        }
                                        ButtonBehavior.TAP -> {
                                            isPressedVisual = true
                                            onStateChange(true)
                                            coroutineScope.launch {
                                                delay(30)
                                                isPressedVisual = false
                                                onStateChange(false)
                                            }
                                        }
                                    }
                                    change.consume()
                                }
                            }
                            androidx.compose.ui.input.pointer.PointerEventType.Move -> {
                                val change = event.changes.firstOrNull { it.id == trackingPointerId } ?: continue
                                if (allowDragLook && onLookDelta != null) {
                                    val delta = change.positionChange()
                                    if (delta.x != 0f || delta.y != 0f) {
                                        onLookDelta(delta.x, delta.y)
                                    }
                                }
                                change.consume()
                            }
                            androidx.compose.ui.input.pointer.PointerEventType.Release -> {
                                val change = event.changes.firstOrNull { it.id == trackingPointerId }
                                if (change != null) {
                                    trackingPointerId = null
                                    if (behavior == ButtonBehavior.HOLD) {
                                        isPressedVisual = false
                                        onStateChange(false)
                                    }
                                    change.consume()
                                }
                            }
                        }
                    }
                }
            }
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
