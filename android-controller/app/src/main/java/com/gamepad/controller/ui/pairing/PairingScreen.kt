package com.gamepad.controller.ui.pairing

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * (zero Android camera runtime permissions required).
 * 
 * Also provides a manual fallback entry for direct IP and debug keys.
 */
@Composable
fun PairingScreen(
    onDismiss: () -> Unit,
    onPairingSuccess: (host: String, port: Int, session: Long, key: ByteArray) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var manualIp by remember { mutableStateOf("192.168.1.100") }
    var manualPort by remember { mutableStateOf("47789") }
    var manualHexKey by remember { mutableStateOf("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef") }
    var statusText by remember { mutableStateOf<String?>(null) }
    var isHandshaking by remember { mutableStateOf(false) }

    fun startHandshake(targetHost: String, targetPort: Int, pskHex: String) {
        val pskBytes = HandshakeManager.hexToBytes(pskHex)
        if (pskBytes.size != 32) {
            statusText = "Error: Key must be 64 hex characters (32 bytes)"
            return
        }

        isHandshaking = true
        statusText = "Sending hello challenge..."

        coroutineScope.launch(Dispatchers.IO) {
            val result = HandshakeManager.performHandshake(targetHost, targetPort, pskBytes)
            withContext(Dispatchers.Main) {
                isHandshaking = false
                if (result.isSuccess) {
                    val session = result.getOrThrow()
                    Toast.makeText(context, "Pairing successful! Session $session", Toast.LENGTH_SHORT).show()
                    onPairingSuccess(targetHost, targetPort, session, pskBytes)
                } else {
                    statusText = "Handshake failed: ${result.exceptionOrNull()?.message}"
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF161B22))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "RECEIVER PAIRING",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Google Play Services Code Scanner Button
                Button(
                    onClick = {
                        val options = GmsBarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                            .enableAutoZoom()
                            .build()

                        val scanner = GmsBarcodeScanning.getClient(context, options)
                        scanner.startScan()
                            .addOnSuccessListener { barcode ->
                                val rawValue = barcode.rawValue
                                if (!rawValue.isNullOrEmpty()) {
                                    try {
                                        val payload = Json { ignoreUnknownKeys = true }.decodeFromString<PairingQrPayload>(rawValue)
                                        val targetHost = payload.ips.firstOrNull() ?: "127.0.0.1"
                                        startHandshake(targetHost, payload.port, payload.key)
                                    } catch (e: Exception) {
                                        statusText = "Invalid QR JSON: ${e.message}"
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                statusText = "Scan failed: ${e.message}"
                            }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "SCAN RECEIVER QR CODE", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "— OR ENTER MANUALLY —", color = Color(0xFF8B949E), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = manualIp,
                    onValueChange = { manualIp = it },
                    label = { Text("PC IP Address") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        focusedLabelColor = Color(0xFF00E5FF),
                        unfocusedTextColor = Color.White,
                        focusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = manualPort,
                    onValueChange = { manualPort = it },
                    label = { Text("Port (Default 47789)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        focusedLabelColor = Color(0xFF00E5FF),
                        unfocusedTextColor = Color.White,
                        focusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = manualHexKey,
                    onValueChange = { manualHexKey = it },
                    label = { Text("32-Byte PSK (64 Hex Chars)") },
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
                    Text(
                        text = statusText!!,
                        color = Color(0xFFFF9100),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    horizontalArrangement = Arrangement.End,
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
