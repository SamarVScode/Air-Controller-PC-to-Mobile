/**
 * @aegis-contract
 * @claim User-Friendly Android QR Pairing Dialog with Credential Persistence
 * @true Saves and restores IP, port, and key via SharedPreferences with Google Code Scanner QR support
 * @false Permits unhandled network exceptions or loss of user pairing preferences
 */
package com.gamepad.controller.ui.pairing

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.gamepad.controller.data.PairingQrPayload
import com.gamepad.controller.network.HandshakeManager
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * PairingScreen: Quick pairing modal using Google Play Services Code Scanner
 * (zero Android camera runtime permissions required) or manual IP/Key entry.
 * 
 * Features:
 * 1. SharedPreferences persistence for IP, Port, and PSK Key.
 * 2. High-visibility '📷 SCAN RECEIVER QR CODE (AUTO)' button with subtext.
 * 3. Friendly helper hints referencing the Windows Receiver screen.
 * 4. Responsive scrollable layout for all landscape form factors.
 * 5. Informative real-time status messages during handshake.
 */
@Composable
fun PairingScreen(
    onDismiss: () -> Unit,
    onPairingSuccess: (host: String, port: Int, session: Long, key: ByteArray) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val prefs = remember {
        context.getSharedPreferences("air_controller_pairing", Context.MODE_PRIVATE)
    }
    val savedIp = remember { prefs.getString("last_ip", "192.168.1.100") ?: "192.168.1.100" }
    val savedPort = remember { prefs.getString("last_port", "47789") ?: "47789" }
    val savedKey = remember {
        prefs.getString("last_key", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
            ?: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }

    var manualIp by remember { mutableStateOf(savedIp) }
    var manualPort by remember { mutableStateOf(savedPort) }
    var manualHexKey by remember { mutableStateOf(savedKey) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var isErrorStatus by remember { mutableStateOf(false) }
    var isHandshaking by remember { mutableStateOf(false) }

    fun savePreferences(host: String, port: Int, key: String) {
        prefs.edit()
            .putString("last_ip", host)
            .putString("last_port", port.toString())
            .putString("last_key", key)
            .apply()
    }

    fun startHandshake(targetHost: String, targetPort: Int, pskHex: String) {
        val cleanHost = targetHost.trim()
        val cleanHex = pskHex.trim()
        val pskBytes = try {
            HandshakeManager.hexToBytes(cleanHex)
        } catch (_: Exception) {
            ByteArray(0)
        }
        if (pskBytes.size != 32) {
            statusText = "⚠️ Key must be 64 hex characters (32 bytes)"
            isErrorStatus = true
            return
        }

        isHandshaking = true
        isErrorStatus = false
        statusText = "🔄 Connecting to $cleanHost:$targetPort..."

        coroutineScope.launch(Dispatchers.IO) {
            val result = HandshakeManager.performHandshake(cleanHost, targetPort, pskBytes)
            withContext(Dispatchers.Main) {
                isHandshaking = false
                if (result.isSuccess) {
                    val session = result.getOrThrow()
                    savePreferences(cleanHost, targetPort, cleanHex)
                    Toast.makeText(context, "Pairing successful! Session $session", Toast.LENGTH_SHORT).show()
                    onPairingSuccess(cleanHost, targetPort, session, pskBytes)
                } else {
                    isErrorStatus = true
                    statusText = "❌ Handshake failed: ${result.exceptionOrNull()?.message ?: "Check PC IP & Firewall"}"
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(460.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF161B22))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "PAIR WITH WINDOWS RECEIVER",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))

                // High-Visibility Automatic QR Scan Button
                Button(
                    onClick = {
                        val options = GmsBarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                            .enableAutoZoom()
                            .build()

                        val scanner = GmsBarcodeScanning.getClient(context, options)
                        scanner.startScan()
                            .addOnSuccessListener { barcode: Barcode ->
                                val rawValue = barcode.rawValue
                                if (!rawValue.isNullOrEmpty()) {
                                    try {
                                        val payload = Json { ignoreUnknownKeys = true }.decodeFromString<PairingQrPayload>(rawValue)
                                        val targetHost = payload.ips.firstOrNull() ?: "127.0.0.1"
                                        startHandshake(targetHost, payload.port, payload.key)
                                    } catch (e: Exception) {
                                        isErrorStatus = true
                                        statusText = "Invalid QR code format: ${e.message}"
                                    }
                                }
                            }
                            .addOnFailureListener { e: Exception ->
                                isErrorStatus = true
                                statusText = "Scan cancelled or failed: ${e.message}"
                            }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5FF),
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "📷 SCAN RECEIVER QR CODE (AUTO)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Scan the QR code on your Windows Receiver",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xCC000000)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "— OR CONNECT MANUALLY —",
                    color = Color(0xFF8B949E),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Helper Information Box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x1A00E5FF))
                        .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💡 Your PC's IP address and Port are shown on the Receiver screen. Ensure both PC and phone are connected to the same Wi-Fi.",
                        fontSize = 11.sp,
                        color = Color(0xFF80D8FF),
                        lineHeight = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = manualIp,
                    onValueChange = { manualIp = it },
                    label = { Text("PC IP Address") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        focusedLabelColor = Color(0xFF00E5FF),
                        unfocusedTextColor = Color.White,
                        focusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = manualPort,
                    onValueChange = { manualPort = it },
                    label = { Text("Port (Default 47789)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        focusedLabelColor = Color(0xFF00E5FF),
                        unfocusedTextColor = Color.White,
                        focusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = manualHexKey,
                    onValueChange = { manualHexKey = it },
                    label = { Text("32-Byte PSK Key (64 Hex Characters)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        focusedLabelColor = Color(0xFF00E5FF),
                        unfocusedTextColor = Color.White,
                        focusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (statusText != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val bgColor = if (isErrorStatus) Color(0x33FF1744) else Color(0x3300E5FF)
                    val txtColor = if (isErrorStatus) Color(0xFFFF5252) else Color(0xFF80D8FF)
                    val brdColor = if (isErrorStatus) Color(0x66FF1744) else Color(0x6600E5FF)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgColor)
                            .border(1.dp, brdColor, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = statusText!!,
                            color = txtColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCEL", color = Color(0xFF8B949E))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val port = manualPort.toIntOrNull() ?: 47789
                            startHandshake(manualIp.trim(), port, manualHexKey.trim())
                        },
                        enabled = !isHandshaking,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
                    ) {
                        Text(if (isHandshaking) "CONNECTING..." else "CONNECT")
                    }
                }
            }
        }
    }
}
``
