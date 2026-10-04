/**
 * @aegis-contract
 * @claim Core Host Activity with ProfileManager and Layout Editor Integration
 * @true Instantiates ProfileManager, manages isEditingLayout state, and toggles between HUD and Editor
 * @false Permits unhandled lifecycle leaks or broken pairing transitions
 */
package com.gamepad.controller

import android.content.Context
import android.graphics.Rect
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gamepad.controller.haptics.HapticsManager
import com.gamepad.controller.input.InputStateHolder
import com.gamepad.controller.input.LookAccumulator
import com.gamepad.controller.network.UdpSenderThread
import com.gamepad.controller.profile.ProfileManager
import com.gamepad.controller.ui.GamepadHudScreen
import com.gamepad.controller.ui.editor.LayoutEditorScreen
import com.gamepad.controller.ui.pairing.PairingScreen

/**
 * MainActivity: Core host activity for Phone-as-Gamepad.
 * 
 * Features:
 * 1. Fullscreen sticky immersive mode (WindowInsetsControllerCompat)
 * 2. System gesture exclusion rects along outer screen boundaries (API 29+)
 * 3. Unbuffered touch dispatch (requestUnbufferedDispatch) for minimum input latency
 * 4. Wi-Fi Low-Latency Lock (WIFI_MODE_FULL_LOW_LATENCY)
 * 5. Lifecycle-aware input safety (Neutralize on pause, stop sender)
 * 6. Profile manager and dynamic layout editor integration
 */
class MainActivity : ComponentActivity() {

    private var wifiLock: WifiManager.WifiLock? = null
    private val hapticsManager by lazy { HapticsManager(this) }
    private val inputStateHolder = InputStateHolder()
    private val lookAccumulator = LookAccumulator()
    private val profileManager by lazy { ProfileManager(this) }
    private var senderThread: UdpSenderThread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen awake continuously during gameplay
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Set up initial fullscreen immersive layout
        configureImmersiveMode()

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0B0E14)
            ) {
                GamepadApp(
                    inputStateHolder = inputStateHolder,
                    lookAccumulator = lookAccumulator,
                    hapticsManager = hapticsManager,
                    senderThread = senderThread,
                    profileManager = profileManager,
                    onStartSender = { host, port, session, key ->
                        startSender(host, port, session, key)
                    },
                    onStopSender = {
                        stopSender()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        configureImmersiveMode()
        acquireWifiLock()
        updateSystemGestureExclusionRects()
    }

    override fun onPause() {
        super.onPause()
        // Neutralize all held keys and mouse buttons immediately on pause
        inputStateHolder.neutralize()
        senderThread?.sendNeutralState()
        releaseWifiLock()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSender()
        releaseWifiLock()
    }

    /**
     * Unbuffered touch dispatch ensures raw input events are dispatched directly to views
     * without platform touch-event batching or coalescing, achieving the lowest possible latency.
     */
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.decorView.requestUnbufferedDispatch(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    /**
     * Configures edge-to-edge layout and sticky transient system bars.
     */
    private fun configureImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    /**
     * Excludes left and right screen borders from Android 10+ system navigation back gestures
     * so intense thumb swipes and button taps do not accidentally navigate away.
     */
    private fun updateSystemGestureExclusionRects() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val decorView = window.decorView
            decorView.post {
                val width = decorView.width
                val height = decorView.height
                if (width > 0 && height > 0) {
                    val density = resources.displayMetrics.density
                    val edgeWidthPx = (48 * density).toInt()
                    val leftRect = Rect(0, 0, edgeWidthPx, height)
                    val rightRect = Rect(width - edgeWidthPx, 0, width, height)
                    decorView.systemGestureExclusionRects = listOf(leftRect, rightRect)
                }
            }
        }
    }

    /**
     * Acquires Wi-Fi Low Latency Lock (API 29+) or High Performance Lock (API < 29).
     */
    private fun acquireWifiLock() {
        if (wifiLock?.isHeld == true) return
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                wifiManager?.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                    "PhoneGamepad:LowLatencyLock"
                )
            } else {
                @Suppress("DEPRECATION")
                wifiManager?.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "PhoneGamepad:HighPerfLock"
                )
            }
            wifiLock?.setReferenceCounted(false)
            wifiLock?.acquire()
        } catch (_: Exception) {
            // Best effort; devices without Wi-Fi permission or support will fallback gracefully
        }
    }

    private fun releaseWifiLock() {
        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (_: Exception) {
        }
    }

    private fun startSender(host: String, port: Int, sessionId: Long, keyBytes: ByteArray) {
        stopSender()
        senderThread = UdpSenderThread(
            targetHost = host,
            targetPort = port,
            sessionId = sessionId,
            psk = keyBytes,
            inputStateHolder = inputStateHolder,
            lookAccumulator = lookAccumulator
        ).apply {
            start()
        }
    }

    private fun stopSender() {
        senderThread?.stopSending()
        senderThread = null
    }
}

@Composable
fun GamepadApp(
    inputStateHolder: InputStateHolder,
    lookAccumulator: LookAccumulator,
    hapticsManager: HapticsManager,
    senderThread: UdpSenderThread?,
    profileManager: ProfileManager,
    onStartSender: (String, Int, Long, ByteArray) -> Unit,
    onStopSender: () -> Unit
) {
    var showPairingModal by remember { mutableStateOf(false) }
    var isEditingLayout by remember { mutableStateOf(false) }

    if (isEditingLayout) {
        LayoutEditorScreen(
            profileManager = profileManager,
            onExitEditor = { isEditingLayout = false }
        )
    } else {
        GamepadHudScreen(
            inputStateHolder = inputStateHolder,
            lookAccumulator = lookAccumulator,
            hapticsManager = hapticsManager,
            senderThread = senderThread,
            profileManager = profileManager,
            onOpenEditLayout = { isEditingLayout = true },
            onOpenPairing = { showPairingModal = true }
        )
    }

    if (showPairingModal) {
        PairingScreen(
            onDismiss = { showPairingModal = false },
            onPairingSuccess = { host, port, session, key ->
                onStartSender(host, port, session, key)
                showPairingModal = false
            }
        )
    }
}
``
