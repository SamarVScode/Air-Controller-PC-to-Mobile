/**
 * @aegis-contract
 * @claim Dynamic Jetpack Compose Touch HUD with Profile Customization
 * @true Dynamically positions and renders all 20 HUD controls from activeProfile.controls
 * @true Provides top status bar with connection state, ping, PAIR PC, EDIT LAYOUT, and GYRO chip
 * @false Hardcodes control positions or omits active profile bindings
 */
package com.gamepad.controller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamepad.controller.data.ButtonBehavior
import com.gamepad.controller.data.ConnectionStatus
import com.gamepad.controller.data.ControlConfig
import com.gamepad.controller.data.KeyId
import com.gamepad.controller.data.MouseButton
import com.gamepad.controller.haptics.HapticsManager
import com.gamepad.controller.input.InputStateHolder
import com.gamepad.controller.input.LookAccumulator
import com.gamepad.controller.network.UdpSenderThread
import com.gamepad.controller.profile.ProfileManager
import com.gamepad.controller.ui.components.LookZoneCanvas
import com.gamepad.controller.ui.components.TouchButton
import com.gamepad.controller.ui.components.VirtualJoystick

/**
 * GamepadHudScreen: Dynamic Jetpack Compose HUD layout rendering the complete
 * PUBG Mobile style touch controls from active GamepadLayoutProfile.
 * 
 * Supports dynamic control coordinates (xPercent, yPercent), size scaling,
 * opacity, and tactical cyberpunk top status bar with pairing and editor triggers.
 */
@Composable
fun GamepadHudScreen(
    inputStateHolder: InputStateHolder,
    lookAccumulator: LookAccumulator,
    hapticsManager: HapticsManager,
    senderThread: UdpSenderThread?,
    profileManager: ProfileManager,
    onOpenEditLayout: () -> Unit,
    onOpenPairing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeProfile by profileManager.activeProfile.collectAsState()

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
    var isGyroActive by remember { mutableStateOf(activeProfile.settings.gyroEnabled) }

    val controlsMap = remember(activeProfile) {
        activeProfile.controls.associateBy { it.id }
    }

    fun getControl(id: String, defX: Float, defY: Float, defW: Float, defH: Float, defOpacity: Float): ControlConfig {
        return controlsMap[id] ?: ControlConfig(
            id = id,
            type = "button",
            x = defX,
            y = defY,
            width = defW,
            height = defH,
            opacity = defOpacity
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight

        // 1. Dedicated Swipe-to-Look Zone (x = 42% to 100%, behind controls)
        LookZoneCanvas(
            lookAccumulator = lookAccumulator,
            touchSensitivity = activeProfile.settings.touchSensitivity,
            adsSensitivityMultiplier = activeProfile.settings.adsSensitivityMultiplier,
            isAdsActive = isAdsActive,
            modifier = Modifier
                .offset(x = screenWidth * 0.42f, y = 0.dp)
                .size(width = screenWidth * 0.58f, height = screenHeight)
        )

        // 2. Move Stick (ms): default x:16%, y:66%, size:17%
        val msCfg = getControl("ms", 16.0f, 66.0f, 17.0f, 17.0f, 0.75f)
        val msSize = screenWidth * (msCfg.width / 100f)
        VirtualJoystick(
            size = msSize,
            deadzone = activeProfile.settings.stickDeadzone,
            sprintThreshold = 0.85f,
            floatingOrigin = activeProfile.settings.stickFloatingOrigin,
            hapticsManager = hapticsManager,
            onDirectionChanged = { normX, normY, isSprint ->
                inputStateHolder.setStickDirection(normX, normY, isSprint)
            },
            modifier = Modifier
                .alignCenter(screenWidth * (msCfg.x / 100f), screenHeight * (msCfg.y / 100f), msSize, msSize)
                .alpha(msCfg.opacity)
        )

        // 3. Sprint Lock Toggle (run): default x:16%, y:34%, size:5.6%
        val runCfg = getControl("run", 16.0f, 34.0f, 5.6f, 5.6f, 0.75f)
        val runSize = screenWidth * (runCfg.width / 100f)
        TouchButton(
            label = "RUN",
            behavior = ButtonBehavior.TOGGLE,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.LEFT_SHIFT, active) },
            modifier = Modifier
                .alignCenter(screenWidth * (runCfg.x / 100f), screenHeight * (runCfg.y / 100f), runSize, runSize)
                .alpha(runCfg.opacity)
        )

        // 4. Left Fire (lfire): LMB hold, default x:31%, y:58%, size:9%
        val lfireCfg = getControl("lfire", 31.0f, 58.0f, 9.0f, 9.0f, 0.85f)
        val lfireSize = screenWidth * (lfireCfg.width / 100f)
        TouchButton(
            label = "FIRE",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setMouseButton(MouseButton.LMB, active) },
            modifier = Modifier
                .alignCenter(screenWidth * (lfireCfg.x / 100f), screenHeight * (lfireCfg.y / 100f), lfireSize, lfireSize)
                .alpha(lfireCfg.opacity)
        )

        // 5. Right Fire & Look (rfire): LMB hold + drag-to-look, default x:86%, y:64%, size:14%
        val rfireCfg = getControl("rfire", 86.0f, 64.0f, 14.0f, 14.0f, 0.85f)
        val rfireSize = screenWidth * (rfireCfg.width / 100f)
        TouchButton(
            label = "FIRE",
            behavior = ButtonBehavior.HOLD,
            allowDragLook = true,
            onLookDelta = { dx, dy ->
                val mult = if (isAdsActive) activeProfile.settings.adsSensitivityMultiplier else 1.0f
                lookAccumulator.addDeltas(dx * mult, dy * mult)
            },
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setMouseButton(MouseButton.LMB, active) },
            modifier = Modifier
                .alignCenter(screenWidth * (rfireCfg.x / 100f), screenHeight * (rfireCfg.y / 100f), rfireSize, rfireSize)
                .alpha(rfireCfg.opacity)
        )

        // 6. Aim Down Sights (scope): RMB toggle, default x:73%, y:47%, size:8%
        val scopeCfg = getControl("scope", 73.0f, 47.0f, 8.0f, 8.0f, 0.85f)
        val scopeSize = screenWidth * (scopeCfg.width / 100f)
        TouchButton(
            label = "ADS",
            behavior = ButtonBehavior.TOGGLE,
            hapticsManager = hapticsManager,
            onStateChange = { active ->
                isAdsActive = active
                inputStateHolder.setMouseButton(MouseButton.RMB, active)
            },
            modifier = Modifier
                .alignCenter(screenWidth * (scopeCfg.x / 100f), screenHeight * (scopeCfg.y / 100f), scopeSize, scopeSize)
                .alpha(scopeCfg.opacity)
        )

        // 7. Jump: Space hold, default x:94%, y:38%, size:8%
        val jumpCfg = getControl("jump", 94.0f, 38.0f, 8.0f, 8.0f, 0.85f)
        val jumpSize = screenWidth * (jumpCfg.width / 100f)
        TouchButton(
            label = "JUMP",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.SPACE, active) },
            modifier = Modifier
                .alignCenter(screenWidth * (jumpCfg.x / 100f), screenHeight * (jumpCfg.y / 100f), jumpSize, jumpSize)
                .alpha(jumpCfg.opacity)
        )

        // 8. Crouch: C hold, default x:76%, y:84%, size:7%
        val crouchCfg = getControl("crouch", 76.0f, 84.0f, 7.0f, 7.0f, 0.85f)
        val crouchSize = screenWidth * (crouchCfg.width / 100f)
        TouchButton(
            label = "CROUCH",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.C, active) },
            modifier = Modifier
                .alignCenter(screenWidth * (crouchCfg.x / 100f), screenHeight * (crouchCfg.y / 100f), crouchSize, crouchSize)
                .alpha(crouchCfg.opacity)
        )

        // 9. Prone: Z hold, default x:95%, y:88%, size:7%
        val proneCfg = getControl("prone", 95.0f, 88.0f, 7.0f, 7.0f, 0.85f)
        val proneSize = screenWidth * (proneCfg.width / 100f)
        TouchButton(
            label = "PRONE",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.Z, active) },
            modifier = Modifier
                .alignCenter(screenWidth * (proneCfg.x / 100f), screenHeight * (proneCfg.y / 100f), proneSize, proneSize)
                .alpha(proneCfg.opacity)
        )

        // 10. Lean Left (leanl): Q hold, default x:62%, y:32%, size:6%
        val leanlCfg = getControl("leanl", 62.0f, 32.0f, 6.0f, 6.0f, 0.8f)
        val leanlSize = screenWidth * (leanlCfg.width / 100f)
        TouchButton(
            label = "LEAN L",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.Q, active) },
            modifier = Modifier
                .alignCenter(screenWidth * (leanlCfg.x / 100f), screenHeight * (leanlCfg.y / 100f), leanlSize, leanlSize)
                .alpha(leanlCfg.opacity)
        )

        // 11. Lean Right (leanr): E hold, default x:70%, y:32%, size:6%
        val leanrCfg = getControl("leanr", 70.0f, 32.0f, 6.0f, 6.0f, 0.8f)
        val leanrSize = screenWidth * (leanrCfg.width / 100f)
        TouchButton(
            label = "LEAN R",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.E, active) },
            modifier = Modifier
                .alignCenter(screenWidth * (leanrCfg.x / 100f), screenHeight * (leanrCfg.y / 100f), leanrSize, leanrSize)
                .alpha(leanrCfg.opacity)
        )

        // 12. Reload: R hold, default x:64%, y:68%, size:6.4%
        val reloadCfg = getControl("reload", 64.0f, 68.0f, 6.4f, 6.4f, 0.85f)
        val reloadSize = screenWidth * (reloadCfg.width / 100f)
        TouchButton(
            label = "RELOAD",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.R, active) },
            modifier = Modifier
                .alignCenter(screenWidth * (reloadCfg.x / 100f), screenHeight * (reloadCfg.y / 100f), reloadSize, reloadSize)
                .alpha(reloadCfg.opacity)
        )

        // 13. Use/Pick Up: F hold, default x:58%, y:54%, size:6.4%
        val useCfg = getControl("use", 58.0f, 54.0f, 6.4f, 6.4f, 0.8f)
        val useSize = screenWidth * (useCfg.width / 100f)
        TouchButton(
            label = "USE",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.F, active) },
            modifier = Modifier
                .alignCenter(screenWidth * (useCfg.x / 100f), screenHeight * (useCfg.y / 100f), useSize, useSize)
                .alpha(useCfg.opacity)
        )

        // 14. Weapon Slot 1 (s1): 1 hold, default x:38%, y:15%, size:6%
        val s1Cfg = getControl("s1", 38.0f, 15.0f, 6.0f, 6.0f, 0.75f)
        val s1Size = screenWidth * (s1Cfg.width / 100f)
        TouchButton(
            label = "SLOT 1",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.KEY_1, active) },
            modifier = Modifier
                .alignCenter(screenWidth * (s1Cfg.x / 100f), screenHeight * (s1Cfg.y / 100f), s1Size, s1Size)
                .alpha(s1Cfg.opacity)
        )

        // 15. Weapon Slot 2 (s2): 2 hold, default x:44.5%, y:15%, size:6%
        val s2Cfg = getControl("s2", 44.5f, 15.0f, 6.0f, 6.0f, 0.75f)
        val s2Size = screenWidth * (s2Cfg.width / 100f)
        TouchButton(
            label = "SLOT 2",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.KEY_2, active) },
            modifier = Modifier
                .alignCenter(screenWidth * (s2Cfg.x / 100f), screenHeight * (s2Cfg.y / 100f), s2Size, s2Size)
                .alpha(s2Cfg.opacity)
        )

        // 16. Melee Slot (s3): 3 hold, default x:51%, y:15%, size:6%
        val s3Cfg = getControl("s3", 51.0f, 15.0f, 6.0f, 6.0f, 0.75f)
        val s3Size = screenWidth * (s3Cfg.width / 100f)
        TouchButton(
            label = "MELEE",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.KEY_3, active) },
            modifier = Modifier
                .alignCenter(screenWidth * (s3Cfg.x / 100f), screenHeight * (s3Cfg.y / 100f), s3Size, s3Size)
                .alpha(s3Cfg.opacity)
        )

        // 17. Grenade (gren): G hold, default x:58%, y:15%, size:6%
        val grenCfg = getControl("gren", 58.0f, 15.0f, 6.0f, 6.0f, 0.75f)
        val grenSize = screenWidth * (grenCfg.width / 100f)
        TouchButton(
            label = "GREN",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.G, active) },
            modifier = Modifier
                .alignCenter(screenWidth * (grenCfg.x / 100f), screenHeight * (grenCfg.y / 100f), grenSize, grenSize)
                .alpha(grenCfg.opacity)
        )

        // 18. Heal (heal): 5 hold, default x:65%, y:15%, size:6%
        val healCfg = getControl("heal", 65.0f, 15.0f, 6.0f, 6.0f, 0.75f)
        val healSize = screenWidth * (healCfg.width / 100f)
        TouchButton(
            label = "HEAL",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.KEY_5, active) },
            modifier = Modifier
                .alignCenter(screenWidth * (healCfg.x / 100f), screenHeight * (healCfg.y / 100f), healSize, healSize)
                .alpha(healCfg.opacity)
        )

        // 19. Map: M hold, default x:84%, y:12%, size:5.4%
        val mapCfg = getControl("map", 84.0f, 12.0f, 5.4f, 5.4f, 0.75f)
        val mapSize = screenWidth * (mapCfg.width / 100f)
        TouchButton(
            label = "MAP",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.M, active) },
            modifier = Modifier
                .alignCenter(screenWidth * (mapCfg.x / 100f), screenHeight * (mapCfg.y / 100f), mapSize, mapSize)
                .alpha(mapCfg.opacity)
        )

        // 20. Bag (Inventory): Tab hold, default x:91%, y:12%, size:5.4%
        val bagCfg = getControl("bag", 91.0f, 12.0f, 5.4f, 5.4f, 0.75f)
        val bagSize = screenWidth * (bagCfg.width / 100f)
        TouchButton(
            label = "BAG",
            behavior = ButtonBehavior.HOLD,
            hapticsManager = hapticsManager,
            onStateChange = { active -> inputStateHolder.setKey(KeyId.TAB, active) },
            modifier = Modifier
                .alignCenter(screenWidth * (bagCfg.x / 100f), screenHeight * (bagCfg.y / 100f), bagSize, bagSize)
                .alpha(bagCfg.opacity)
        )

        // 20. Gyroscope Toggle Chip (gyro): default x:50%, y:90%, width:15%, height:4.8%
        val gyroCfg = getControl("gyro", 50.0f, 90.0f, 15.0f, 4.8f, 0.7f)
        val gyroWidth = screenWidth * (gyroCfg.width / 100f)
        val gyroHeight = screenHeight * (gyroCfg.height / 100f)
        val gyroActiveBg = if (isGyroActive) Color(0xFF00E5FF) else Color(0x3310141D)
        val gyroTextCol = if (isGyroActive) Color.Black else Color.White
        val gyroBorderCol = if (isGyroActive) Color(0xFF00E5FF) else Color(0x66FFFFFF)

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .alignCenter(
                    screenWidth * (gyroCfg.x / 100f),
                    screenHeight * (gyroCfg.y / 100f),
                    gyroWidth,
                    gyroHeight
                )
                .alpha(gyroCfg.opacity)
                .clip(RoundedCornerShape(8.dp))
                .background(gyroActiveBg)
                .border(1.dp, gyroBorderCol, RoundedCornerShape(8.dp))
                .clickable {
                    hapticsManager.performTick()
                    isGyroActive = !isGyroActive
                }
        ) {
            Text(
                text = if (isGyroActive) "🎯 GYRO ON" else "🎯 GYRO",
                color = gyroTextCol,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Top Status Bar: Connection Badge, Pair Pill, Edit Layout Pill, and Gyro Chip
        TopStatusBar(
            connectionStatus = connectionState,
            rttMs = rttMs,
            isGyroActive = isGyroActive,
            onToggleGyro = { isGyroActive = !isGyroActive },
            onOpenPairing = onOpenPairing,
            onOpenEditLayout = onOpenEditLayout,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
        )
    }
}

/**
 * Cyberpunk tactical Top Status Bar.
 * 
 * Features:
 * - Connection status indicator + latency ping in ms
 * - Clickable 'PAIR PC' pill
 * - Clickable 'EDIT LAYOUT' pill with cyan border
 * - Clickable 'GYRO' toggle chip
 */
@Composable
private fun TopStatusBar(
    connectionStatus: ConnectionStatus,
    rttMs: Long,
    isGyroActive: Boolean,
    onToggleGyro: () -> Unit,
    onOpenPairing: () -> Unit,
    onOpenEditLayout: () -> Unit,
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xE610141D))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // 1. Connection Status Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x33FFFFFF))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = connectionStatus.name,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            if (connectionStatus == ConnectionStatus.CONNECTED) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${rttMs}ms",
                    color = Color(0xFF00E5FF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // 2. 🔗 'PAIR PC' Clickable Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x2600E5FF))
                .border(1.dp, Color(0x6600E5FF), RoundedCornerShape(16.dp))
                .clickable { onOpenPairing() }
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "🔗 PAIR PC",
                color = Color(0xFF80D8FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // 3. ✏️ 'EDIT LAYOUT' Clickable Pill (Cyan Border)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x2210141D))
                .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(16.dp))
                .clickable { onOpenEditLayout() }
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "✏️ EDIT LAYOUT",
                color = Color(0xFF00E5FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // 4. 🎯 'GYRO' Toggle Chip
        val gyroBg = if (isGyroActive) Color(0xFF00E5FF) else Color(0x22FFFFFF)
        val gyroTextColor = if (isGyroActive) Color.Black else Color.White
        val gyroBorderColor = if (isGyroActive) Color(0xFF00E5FF) else Color(0x33FFFFFF)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(gyroBg)
                .border(1.dp, gyroBorderColor, RoundedCornerShape(16.dp))
                .clickable { onToggleGyro() }
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (isGyroActive) "🎯 GYRO ON" else "🎯 GYRO",
                color = gyroTextColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
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
