package com.gamepad.controller.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.gamepad.controller.input.LookAccumulator

/**
 * LookZoneCanvas: Transparent touch gesture surface spanning the right half of the screen
 * behind other HUD buttons. Dispatches relative pixel deltas directly to LookAccumulator.
 */
@Composable
fun LookZoneCanvas(
    lookAccumulator: LookAccumulator,
    touchSensitivity: Float = 1.0f,
    adsSensitivityMultiplier: Float = 0.6f,
    isAdsActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(touchSensitivity, adsSensitivityMultiplier, isAdsActive) {
                val effectiveSensitivity = touchSensitivity * (if (isAdsActive) adsSensitivityMultiplier else 1.0f)

                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            androidx.compose.ui.input.pointer.PointerEventType.Move -> {
                                for (change in event.changes) {
                                    if (change.pressed) {
                                        val delta = change.positionChange()
                                        if (delta.x != 0f || delta.y != 0f) {
                                            lookAccumulator.addDeltas(
                                                delta.x * effectiveSensitivity,
                                                delta.y * effectiveSensitivity
                                            )
                                        }
                                        change.consume()
                                    }
                                }
                            }
                            else -> {
                                // Down / Up / Cancel consumed to preserve touch exclusivity
                                for (change in event.changes) {
                                    change.consume()
                                }
                            }
                        }
                    }
                }
            }
    )
}
