package com.gamepad.controller.input

import android.view.MotionEvent
import com.gamepad.controller.model.ControlElement
import com.gamepad.controller.model.ControlType
import java.util.concurrent.ConcurrentHashMap

enum class PointerTarget {
    CONTROL,
    LOOK_ZONE
}

data class ActivePointer(
    val id: Int,
    var startX: Float,
    var startY: Float,
    var currentX: Float,
    var currentY: Float,
    val target: PointerTarget,
    val controlId: String? = null,
    val isDragLookEnabled: Boolean = false
)

/**
 * Multi-touch ownership tracker mapping physical pointer IDs exclusively to controls
 * or to the Look Zone.
 */
class PointerTracker(
    private val lookAccumulator: LookAccumulator,
    private val onControlStateChanged: (controlId: String, isDown: Boolean) -> Unit,
    private val onStickDelta: (deltaX: Float, deltaY: Float) -> Unit
) {
    private val pointers = ConcurrentHashMap<Int, ActivePointer>()

    fun handleTouchEvent(event: MotionEvent, controls: List<ControlElement>, screenWidthPx: Float, screenHeightPx: Float, isAdsActive: Boolean) {
        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val x = event.getX(actionIndex)
                val y = event.getY(actionIndex)
                val xPct = (x / screenWidthPx) * 100.0f
                val yPct = (y / screenHeightPx) * 100.0f

                // Find matching control (highest priority: stick / buttons)
                val hitControl = controls.firstOrNull { ctrl ->
                    ctrl.type != ControlType.LOOK_ZONE &&
                            xPct in (ctrl.x - ctrl.width / 2)..(ctrl.x + ctrl.width / 2) &&
                            yPct in (ctrl.y - ctrl.height / 2)..(ctrl.y + ctrl.height / 2)
                }

                if (hitControl != null) {
                    val isDragLook = hitControl.id == "rfire" // Fire-and-drag allows simultaneous looking
                    val active = ActivePointer(pointerId, x, y, x, y, PointerTarget.CONTROL, hitControl.id, isDragLook)
                    pointers[pointerId] = active
                    onControlStateChanged(hitControl.id, true)
                } else if (xPct >= 42.0f) {
                    // Look Zone ownership (x >= 42%)
                    val active = ActivePointer(pointerId, x, y, x, y, PointerTarget.LOOK_ZONE)
                    pointers[pointerId] = active
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val active = pointers[id] ?: continue
                    val newX = event.getX(i)
                    val newY = event.getY(i)
                    val deltaX = newX - active.currentX
                    val deltaY = newY - active.currentY

                    active.currentX = newX
                    active.currentY = newY

                    if (active.target == PointerTarget.LOOK_ZONE || active.isDragLookEnabled) {
                        lookAccumulator.addTouchDelta(deltaX, deltaY, 1.0f, isAdsActive, 0.6f)
                    } else if (active.controlId == "ms") {
                        val stickDeltaX = (newX - active.startX) / (screenWidthPx * 0.17f)
                        val stickDeltaY = (newY - active.startY) / (screenHeightPx * 0.17f)
                        onStickDelta(stickDeltaX, stickDeltaY)
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val active = pointers.remove(pointerId)
                if (active != null) {
                    if (active.controlId != null) {
                        onControlStateChanged(active.controlId, false)
                        if (active.controlId == "ms") {
                            onStickDelta(0.0f, 0.0f)
                        }
                    }
                }
            }
        }
    }

    fun releaseAll() {
        for ((_, active) in pointers) {
            if (active.controlId != null) {
                onControlStateChanged(active.controlId, false)
            }
        }
        pointers.clear()
        onStickDelta(0.0f, 0.0f)
    }
}
