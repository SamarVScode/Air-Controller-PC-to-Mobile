package com.gamepad.controller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamepad.controller.data.ButtonBehavior
import com.gamepad.controller.data.ConnectionStatus
import com.gamepad.controller.data.KeyId
import com.gamepad.controller.data.MouseButton
import com.gamepad.controller.haptics.HapticsManager
import com.gamepad.controller.input.InputStateHolder
import com.gamepad.controller.input.LookAccumulator
import com.gamepad.controller.network.UdpSenderThread
import com.gamepad.controller.ui.components.LookZoneCanvas
import com.gamepad.controller.ui.components.TouchButton
import com.gamepad.controller.ui.components.VirtualJoystick

/**
 * GamepadHudScreen: Authoritative Jetpack Compose HUD layout rendering the complete
 * PUBG Mobile style touch controls from PLAN.md Rev 4 Section 3.
 */
@Composable
fun GamepadHudScreen(
    inputStateHolder: InputStateHolder,
    lookAccumulator: LookAccumulator,
    hapticsManager: HapticsManager,
    senderThread: UdpSenderThread?,
    onOpenPairing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connectionState by (senderThread?.connectionStatusFlow
        ?: remember { mutableStateOf(ConnectionStatus.DISCONNECTED) }
    ).let {
        if (senderThread != null) senderThread.connectionStatusFlow.collectAsState()
        else remember { mutableStateOf(ConnectionStatus.DISCONNECTED) }
    }

    val rttMs by (senderThread?.rttMsFlow
        ?: remember { mutableStateOf(0L) }
    ).let {
        if (senderThread != null) senderThread.rttMsFlow.collectAsState()
        else remember { mutableStateOf(0L) }
    }

    var isAdsActive by remember { mutableStateOf(false) }
    var isGyroActive by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight

        // 1. Dedicated Swipe-to-Look Zone (x = 42% to 100%, behind controls)
        LookZoneCanvas(
            lookAccumulator = lookAccumulator,
            touchSensitivity = 1.0f,
            adsSensitivityMultiplier = 0.6f,
            isAdsActive = isAdsActive,
            modifier = Modifier
                .offset(x = screenWidth * 0.42f, y = 0.dp)
                .size(width = screenWidth * 0.58f, height = screenHeight)
        )

        // 2. Move Stick (ms): x:16%, y:66%, size:17%
        val msSize = screenWidth * 0.17f
        VirtualJoystick(
            size = msSize,
            deadzone = 0.35f,
            sprintThreshold = 0.85f,
            floatingOrigin = true,
            hapticsManager = hapticsManager,
            onDirectionChanged = { normX, normY, isSprint ->
                inputStateHolder.setStickDirection(normX, normY, isSprint)
            },
            modifier = Modifier
                .alignCenter(screenWidth * 0.16f, screenHeight * 0.66f, msSize, msSize)
        )

        // 3. Sprint Lock Toggle (run): x:16%, y:34%, size:5.6%
        val runSize = screenWidth * 0.056f
        TouchButton(
            label = "RUN",
            behavior = ButtonBehavior.TOGGLE,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.LEFT_SHIFT, active) },
            modifier = Modifier
                .alignCenter(screenWidth * 0.16f, screenHeight * 0.34f, runSize, runSize)
        )

        // 4. Left Fire (lfire): LMB hold, x:31%, y:58%, size:9%
        val lfireSize = screenWidth * 0.09f
        TouchButton(
            label = "FIRE",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setMouseButton(MouseButton.LMB, active) },
            modifier = Modifier
                .alignCenter(screenWidth * 0.31f, screenHeight * 0.58f, lfireSize, lfireSize)
        )

        // 5. Right Fire & Look (rfire): LMB hold + drag-to-look, x:86%, y:64%, size:14%
        val rfireSize = screenWidth * 0.14f
        TouchButton(
            label = "FIRE",
            behavior = ButtonBehavior.HOLD,
            allowDragLook = true,
            onLookDelta = { dx, dy ->
                val mult = if (isAdsActive) 0.6f else 1.0f
                lookAccumulator.addDeltas(dx * mult, dy * mult)
            },
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setMouseButton(MouseButton.LMB, active) },
            modifier = Modifier
                .alignCenter(screenWidth * 0.86f, screenHeight * 0.64f, rfireSize, rfireSize)
        )

        // 6. Aim Down Sights (scope): RMB toggle, x:73%, y:47%, size:8%
        val scopeSize = screenWidth * 0.08f
        TouchButton(
            label = "ADS",
            behavior = ButtonBehavior.TOGGLE,
            hapticsManager = hapticsManager,
            onStateChange = { active ->
                isAdsActive = active
                inputStateHolder.setMouseButton(MouseButton.RMB, active)
            },
            modifier = Modifier
                .alignCenter(screenWidth * 0.73f, screenHeight * 0.47f, scopeSize, scopeSize)
        )

        // 7. Jump: Space hold, x:94%, y:38%, size:8%
        val jumpSize = screenWidth * 0.08f
        TouchButton(
            label = "JUMP",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.SPACE, active) },
            modifier = Modifier
                .alignCenter(screenWidth * 0.94f, screenHeight * 0.38f, jumpSize, jumpSize)
        )

        // 8. Crouch: C hold, x:76%, y:84%, size:7%
        val crouchSize = screenWidth * 0.07f
        TouchButton(
            label = "CROUCH",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.C, active) },
            modifier = Modifier
                .alignCenter(screenWidth * 0.76f, screenHeight * 0.84f, crouchSize, crouchSize)
        )

        // 9. Prone: Z hold, x:95%, y:88%, size:7%
        val proneSize = screenWidth * 0.07f
        TouchButton(
            label = "PRONE",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.Z, active) },
            modifier = Modifier
                .alignCenter(screenWidth * 0.95f, screenHeight * 0.88f, proneSize, proneSize)
        )

        // 10. Lean Left (leanl): Q hold, x:62%, y:32%, size:6%
        val leanSize = screenWidth * 0.06f
        TouchButton(
            label = "LEAN L",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.Q, active) },
            modifier = Modifier
                .alignCenter(screenWidth * 0.62f, screenHeight * 0.32f, leanSize, leanSize)
        )

        // 11. Lean Right (leanr): E hold, x:70%, y:32%, size:6%
        TouchButton(
            label = "LEAN R",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.E, active) },
            modifier = Modifier
                .alignCenter(screenWidth * 0.70f, screenHeight * 0.32f, leanSize, leanSize)
        )

        // 12. Reload: R hold, x:64%, y:68%, size:6.4%
        val reloadSize = screenWidth * 0.064f
        TouchButton(
            label = "RELOAD",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.R, active) },
            modifier = Modifier
                .alignCenter(screenWidth * 0.64f, screenHeight * 0.68f, reloadSize, reloadSize)
        )

        // 13. Use/Pick Up: F hold, x:58%, y:54%, size:6.4%
        val useSize = screenWidth * 0.064f
        TouchButton(
            label = "USE",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.F, active) },
            modifier = Modifier
                .alignCenter(screenWidth * 0.58f, screenHeight * 0.54f, useSize, useSize)
        )

        // 14. Weapon Slot 1 (s1): 1 hold, x:38%, y:15%, size:6%
        val weaponSize = screenWidth * 0.06f
        TouchButton(
            label = "SLOT 1",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.KEY_1, active) },
            modifier = Modifier
                .alignCenter(screenWidth * 0.38f, screenHeight * 0.15f, weaponSize, weaponSize)
        )

        // 15. Weapon Slot 2 (s2): 2 hold, x:44.5%, y:15%, size:6%
        TouchButton(
            label = "SLOT 2",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.KEY_2, active) },
            modifier = Modifier
                .alignCenter(screenWidth * 0.445f, screenHeight * 0.15f, weaponSize, weaponSize)
        )

        // 16. Melee Slot (s3): 3 hold, x:51%, y:15%, size:6%
        TouchButton(
            label = "MELEE",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.KEY_3, active) },
            modifier = Modifier
                .alignCenter(screenWidth * 0.51f, screenHeight * 0.15f, weaponSize, weaponSize)
        )

        // 17. Grenade (gren): G hold, x:58%, y:15%, size:6%
        TouchButton(
            label = "GREN",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.G, active) },
            modifier = Modifier
                .alignCenter(screenWidth * 0.58f, screenHeight * 0.15f, weaponSize, weaponSize)
        )

        // 18. Heal (heal): 5 hold, x:65%, y:15%, size:6%
        TouchButton(
            label = "HEAL",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.KEY_5, active) },
            modifier = Modifier
                .alignCenter(screenWidth * 0.65f, screenHeight * 0.15f, weaponSize, weaponSize)
        )

        // 19. Map: M hold, x:84%, y:12%, size:5.4%
        val utilitySize = screenWidth * 0.054f
        TouchButton(
            label = "MAP",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.M, active) },
            modifier = Modifier
                .alignCenter(screenWidth * 0.84f, screenHeight * 0.12f, utilitySize, utilitySize)
        )

        // 20. Bag (Inventory): Tab hold, x:91%, y:12%, size:5.4%
        TouchButton(
            label = "BAG",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.TAB, active) },
            modifier = Modifier
                .alignCenter(screenWidth * 0.91f, screenHeight * 0.12f, utilitySize, utilitySize)
        )

        // 21. Gyro Toggle Chip: x:50%, y:90%, size:15% x 4.8%
        val gyroWidth = screenWidth * 0.15f
        val gyroHeight = screenHeight * 0.048f
        TouchButton(
            label = if (isGyroActive) "GYRO ON" else "GYRO OFF",
            behavior = ButtonBehavior.TOGGLE,
            hapticsManager = hapticsManager,
            onStateChange = { active -> isGyroActive = active },
            modifier = Modifier
                .alignCenter(screenWidth * 0.50f, screenHeight * 0.90f, gyroWidth, gyroHeight)
        )

        // Top Status Bar: Connection, RTT, and Pairing Trigger
        TopStatusBar(
            connectionStatus = connectionState,
            rttMs = rttMs,
            onOpenPairing = onOpenPairing,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
        )
    }
}

@Composable
private fun TopStatusBar(
    connectionStatus: ConnectionStatus,
    rttMs: Long,
    onOpenPairing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (connectionStatus) {
        ConnectionStatus.CONNECTED -> Color(0xFF00E676)
        ConnectionStatus.CONNECTING -> Color(0xFFFFD600)
        ConnectionStatus.PAUSED -> Color(0xFFFF9100)
        ConnectionStatus.DISCONNECTED -> Color(0xFFFF1744)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xB310141D))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
            .clickable { onOpenPairing() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = connectionStatus.name,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        if (connectionStatus == ConnectionStatus.CONNECTED) {
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "${rttMs}ms",
                color = Color(0xFFB0BEC5),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Positions a composable such that its geometric center sits at (centerX, centerY).
 */
private fun Modifier.alignCenter(
    centerX: Dp,
    centerY: Dp,
    width: Dp,
    height: Dp
): Modifier = this
    .offset(x = centerX - width / 2, y = centerY - height / 2)
    .size(width, height)
