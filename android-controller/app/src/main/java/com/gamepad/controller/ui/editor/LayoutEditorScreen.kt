/**
 * @aegis-contract
 * @claim Interactive Touch HUD Layout Editor and Profile Manager Surface
 * @true Implements screen-relative coordinate manipulation, 5%/10% snap grid, boundary clamping, and key/mouse inspector
 * @false Permits out-of-bounds controls, unhandled bindings, or broken profile switches
 */
package com.gamepad.controller.ui.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.gamepad.controller.data.ControlConfig
import com.gamepad.controller.data.GamepadLayoutProfile
import com.gamepad.controller.data.KeyDefinition
import com.gamepad.controller.data.KeyMap
import com.gamepad.controller.profile.ProfileManager
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class SnapMode(val label: String, val stepPercent: Float) {
    OFF("Snap: Off", 0.0f),
    SNAP_5("Snap: 5%", 5.0f),
    SNAP_10("Snap: 10%", 10.0f)
}

@Composable
fun LayoutEditorScreen(
    profileManager: ProfileManager,
    onExitEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeProfile by profileManager.activeProfile.collectAsState()
    val allProfiles by profileManager.profiles.collectAsState()

    var workingProfile by remember(activeProfile) { mutableStateOf(activeProfile) }
    var selectedControlId by remember { mutableStateOf<String?>(null) }
    var snapMode by remember { mutableStateOf(SnapMode.OFF) }

    // Dialog state controllers
    var showInspectorDialog by remember { mutableStateOf(false) }
    var showProfileDropdown by remember { mutableStateOf(false) }
    var showNewProfileDialog by remember { mutableStateOf(false) }
    var showRenameProfileDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    val selectedControl = workingProfile.controls.find { it.id == selectedControlId }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14))
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val density = LocalDensity.current

        val screenWidthPx = with(density) { screenWidth.toPx() }
        val screenHeightPx = with(density) { screenHeight.toPx() }

        // 1. Interactive Grid Canvas & Safe Boundary Exclusion Reticles
        EditorGridAndBoundaryCanvas(
            snapMode = snapMode,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Control Manipulation Surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures {
                        // Tapping background deselects active control
                        selectedControlId = null
                    }
                }
        ) {
            workingProfile.controls.forEach { control ->
                val isSelected = (control.id == selectedControlId)
                val controlWidthDp = screenWidth * (control.width / 100f)
                val controlHeightDp = if (control.type == "chip") {
                    screenHeight * (control.height / 100f)
                } else {
                    controlWidthDp
                }

                val centerX = screenWidth * (control.x / 100f)
                val centerY = screenHeight * (control.y / 100f)

                EditorControlElement(
                    control = control,
                    isSelected = isSelected,
                    widthDp = controlWidthDp,
                    heightDp = controlHeightDp,
                    centerX = centerX,
                    centerY = centerY,
                    onSelect = { selectedControlId = control.id },
                    onDragDelta = { dxPx, dyPx ->
                        val dxPercent = (dxPx / screenWidthPx) * 100f
                        val dyPercent = (dyPx / screenHeightPx) * 100f

                        val rawX = control.x + dxPercent
                        val rawY = control.y + dyPercent

                        val snappedX = snapCoordinate(rawX, snapMode.stepPercent)
                        val snappedY = snapCoordinate(rawY, snapMode.stepPercent)

                        // Clamping to ensure notch/gesture safe margin and on-screen visibility
                        val halfW = control.width / 2f
                        val halfH = (control.height * screenHeightPx / screenWidthPx) / 2f
                        val minX = max(4.0f, halfW)
                        val maxX = min(96.0f, 100.0f - halfW)
                        val minY = max(4.0f, halfH)
                        val maxY = min(96.0f, 100.0f - halfH)

                        val clampedX = snappedX.coerceIn(minX, maxX)
                        val clampedY = snappedY.coerceIn(minY, maxY)

                        val updatedList = workingProfile.controls.map {
                            if (it.id == control.id) it.copy(x = clampedX, y = clampedY) else it
                        }
                        workingProfile = workingProfile.copy(controls = updatedList)
                    }
                )
            }
        }

        // 3. Top Profile Management Bar
        EditorTopBar(
            activeProfileName = workingProfile.profileName,
            snapMode = snapMode,
            onCycleSnap = {
                snapMode = when (snapMode) {
                    SnapMode.OFF -> SnapMode.SNAP_5
                    SnapMode.SNAP_5 -> SnapMode.SNAP_10
                    SnapMode.SNAP_10 -> SnapMode.OFF
                }
            },
            onOpenProfileMenu = { showProfileDropdown = true },
            onExport = { showExportDialog = true },
            onImport = { showImportDialog = true },
            onSave = {
                profileManager.saveProfile(workingProfile)
                Toast.makeText(context, "Layout saved: ${workingProfile.profileName}", Toast.LENGTH_SHORT).show()
            },
            onExit = onExitEditor,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp, start = 16.dp, end = 16.dp)
        )

        // Dropdown menu for profiles
        if (showProfileDropdown) {
            DropdownMenu(
                expanded = showProfileDropdown,
                onDismissRequest = { showProfileDropdown = false },
                modifier = Modifier.background(Color(0xFF161B26))
            ) {
                allProfiles.forEach { p ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = p.profileName + (if (p.profileName == workingProfile.profileName) " (Active)" else ""),
                                color = if (p.profileName == workingProfile.profileName) Color(0xFF00E5FF) else Color.White
                            )
                        },
                        onClick = {
                            profileManager.setActiveProfile(p.profileName)
                            workingProfile = p
                            selectedControlId = null
                            showProfileDropdown = false
                        }
                    )
                }
                HorizontalDivider(color = Color(0x33FFFFFF))
                DropdownMenuItem(
                    text = { Text("+ New Profile", color = Color(0xFF00E676)) },
                    onClick = {
                        showProfileDropdown = false
                        showNewProfileDialog = true
                    }
                )
                DropdownMenuItem(
                    text = { Text("Duplicate Current", color = Color(0xFFEEEEEE)) },
                    onClick = {
                        showProfileDropdown = false
                        val dup = profileManager.duplicateProfile(workingProfile.profileName)
                        if (dup != null) {
                            workingProfile = dup
                            Toast.makeText(context, "Duplicated to ${dup.profileName}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                DropdownMenuItem(
                    text = { Text("Rename Current", color = Color(0xFFEEEEEE)) },
                    onClick = {
                        showProfileDropdown = false
                        showRenameProfileDialog = true
                    }
                )
                DropdownMenuItem(
                    text = { Text("Reset Layout to Default", color = Color(0xFFFFD600)) },
                    onClick = {
                        showProfileDropdown = false
                        val def = ProfileManager.createDefaultBattleRoyaleProfile().copy(profileName = workingProfile.profileName)
                        workingProfile = def
                        profileManager.saveProfile(def)
                        Toast.makeText(context, "Reset to PUBG Mobile default layout", Toast.LENGTH_SHORT).show()
                    }
                )
                if (allProfiles.size > 1) {
                    DropdownMenuItem(
                        text = { Text("Delete Current", color = Color(0xFFFF1744)) },
                        onClick = {
                            showProfileDropdown = false
                            showDeleteConfirmDialog = true
                        }
                    )
                }
            }
        }

        // 4. Floating Manipulation Toolbar (Bottom Dock when control is selected)
        if (selectedControl != null) {
            EditorControlPropertyToolbar(
                control = selectedControl,
                onUpdateSize = { newSize ->
                    val updated = workingProfile.controls.map {
                        if (it.id == selectedControl.id) {
                            if (it.type == "chip") it.copy(width = newSize)
                            else it.copy(width = newSize, height = newSize)
                        } else it
                    }
                    workingProfile = workingProfile.copy(controls = updated)
                },
                onUpdateOpacity = { newOpacity ->
                    val updated = workingProfile.controls.map {
                        if (it.id == selectedControl.id) it.copy(opacity = newOpacity) else it
                    }
                    workingProfile = workingProfile.copy(controls = updated)
                },
                onOpenInspector = { showInspectorDialog = true },
                onResetControl = {
                    val defaultRef = ProfileManager.createDefaultBattleRoyaleProfile().controls.find { it.id == selectedControl.id }
                    if (defaultRef != null) {
                        val updated = workingProfile.controls.map {
                            if (it.id == selectedControl.id) defaultRef else it
                        }
                        workingProfile = workingProfile.copy(controls = updated)
                        Toast.makeText(context, "Reset ${selectedControl.label} to default", Toast.LENGTH_SHORT).show()
                    }
                },
                onDeselect = { selectedControlId = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp, start = 24.dp, end = 24.dp)
            )
        }
    }

    // Modal Property Inspector Dialog
    if (showInspectorDialog && selectedControl != null) {
        ControlPropertyInspectorDialog(
            control = selectedControl,
            onDismiss = { showInspectorDialog = false },
            onApply = { updatedControl ->
                val updated = workingProfile.controls.map {
                    if (it.id == updatedControl.id) updatedControl else it
                }
                workingProfile = workingProfile.copy(controls = updated)
                showInspectorDialog = false
            },
            onResetToDefault = {
                val defaultRef = ProfileManager.createDefaultBattleRoyaleProfile().controls.find { it.id == selectedControl.id }
                if (defaultRef != null) {
                    val updated = workingProfile.controls.map {
                        if (it.id == selectedControl.id) defaultRef else it
                    }
                    workingProfile = workingProfile.copy(controls = updated)
                    showInspectorDialog = false
                    Toast.makeText(context, "Reset control to default", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Create New Profile Dialog
    if (showNewProfileDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewProfileDialog = false },
            title = { Text("Create New Profile", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Profile Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0x66FFFFFF)
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = newName.trim()
                        if (clean.isNotEmpty() && allProfiles.none { it.profileName == clean }) {
                            val newProfile = ProfileManager.createDefaultBattleRoyaleProfile().copy(profileName = clean)
                            profileManager.saveProfile(newProfile)
                            profileManager.setActiveProfile(clean)
                            workingProfile = newProfile
                            showNewProfileDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewProfileDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF161B26)
        )
    }

    // Rename Profile Dialog
    if (showRenameProfileDialog) {
        var renameValue by remember { mutableStateOf(workingProfile.profileName) }
        AlertDialog(
            onDismissRequest = { showRenameProfileDialog = false },
            title = { Text("Rename Profile", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0x66FFFFFF)
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (profileManager.renameProfile(workingProfile.profileName, renameValue)) {
                            workingProfile = workingProfile.copy(profileName = renameValue.trim())
                            showRenameProfileDialog = false
                        } else {
                            Toast.makeText(context, "Name already exists or invalid", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameProfileDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF161B26)
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Profile?", color = Color(0xFFFF1744)) },
            text = { Text("Are you sure you want to delete '${workingProfile.profileName}'?", color = Color.White) },
            confirmButton = {
                Button(
                    onClick = {
                        profileManager.deleteProfile(workingProfile.profileName)
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744), contentColor = Color.White)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF161B26)
        )
    }

    // Export Profile Dialog
    if (showExportDialog) {
        val jsonExport = remember(workingProfile) { profileManager.exportProfileToJson(workingProfile) }
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Profile JSON", color = Color(0xFF00E5FF)) },
            text = {
                Column {
                    Text("Copy this profile JSON string to share or back up:", color = Color(0xFFB0BEC5), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFF0B0E14),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = jsonExport,
                            color = Color(0xFF80D8FF),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("GamepadProfile", jsonExport))
                        Toast.makeText(context, "Profile JSON copied to clipboard", Toast.LENGTH_SHORT).show()
                        showExportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black)
                ) {
                    Text("Copy to Clipboard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Close", color = Color.White)
                }
            },
            containerColor = Color(0xFF161B26)
        )
    }

    // Import Profile Dialog
    if (showImportDialog) {
        var importInput by remember { mutableStateOf("") }
        var importError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Profile JSON", color = Color(0xFF00E5FF)) },
            text = {
                Column {
                    Text("Paste a valid GamepadLayoutProfile JSON string:", color = Color(0xFFB0BEC5), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importInput,
                        onValueChange = {
                            importInput = it
                            importError = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        placeholder = { Text("{\"profileName\": \"My Custom Layout\", ...}") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0x66FFFFFF)
                        )
                    )
                    if (importError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = importError!!,
                            color = Color(0xFFFF1744),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val result = profileManager.importProfileFromJson(importInput.trim())
                        result.fold(
                            onSuccess = { imported ->
                                workingProfile = imported
                                showImportDialog = false
                                Toast.makeText(context, "Imported '${imported.profileName}' successfully", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { err ->
                                importError = err.message ?: "Schema validation failed"
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black)
                ) {
                    Text("Validate & Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF161B26)
        )
    }
}

/**
 * Visual Grid overlay with optional 5% or 10% snap guide lines and notch exclusion boundaries.
 */
@Composable
private fun EditorGridAndBoundaryCanvas(
    snapMode: SnapMode,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        // 1. Safe boundary exclusion lines (Left and right system gesture zones, 4.5% notch margin)
        val leftSafeX = width * 0.045f
        val rightSafeX = width * 0.955f
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)

        // Subtle orange tinted safe exclusion boundaries
        drawRect(
            color = Color(0x0FFF9100),
            topLeft = Offset(0f, 0f),
            size = Size(leftSafeX, height)
        )
        drawRect(
            color = Color(0x0FFF9100),
            topLeft = Offset(rightSafeX, 0f),
            size = Size(width - rightSafeX, height)
        )

        drawLine(
            color = Color(0x4DFF9100),
            start = Offset(leftSafeX, 0f),
            end = Offset(leftSafeX, height),
            strokeWidth = 1.5f,
            pathEffect = dashEffect
        )
        drawLine(
            color = Color(0x4DFF9100),
            start = Offset(rightSafeX, 0f),
            end = Offset(rightSafeX, height),
            strokeWidth = 1.5f,
            pathEffect = dashEffect
        )

        // 2. Look Zone demarcation guide (x = 42%)
        val lookZoneStartX = width * 0.42f
        drawLine(
            color = Color(0x2E00E5FF),
            start = Offset(lookZoneStartX, 0f),
            end = Offset(lookZoneStartX, height),
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        )

        // 3. Grid Lines
        if (snapMode.stepPercent > 0f) {
            val stepFraction = snapMode.stepPercent / 100f
            val gridColor = Color(0x1A00E5FF)

            var xFrac = stepFraction
            while (xFrac < 1.0f) {
                val gx = width * xFrac
                drawLine(
                    color = gridColor,
                    start = Offset(gx, 0f),
                    end = Offset(gx, height),
                    strokeWidth = 1f
                )
                xFrac += stepFraction
            }

            var yFrac = stepFraction
            while (yFrac < 1.0f) {
                val gy = height * yFrac
                drawLine(
                    color = gridColor,
                    start = Offset(0f, gy),
                    end = Offset(width, gy),
                    strokeWidth = 1f
                )
                yFrac += stepFraction
            }
        }
    }
}

/**
 * Visual element representation of a control on the editor canvas with drag handles.
 */
@Composable
private fun EditorControlElement(
    control: ControlConfig,
    isSelected: Boolean,
    widthDp: Dp,
    heightDp: Dp,
    centerX: Dp,
    centerY: Dp,
    onSelect: () -> Unit,
    onDragDelta: (Float, Float) -> Unit
) {
    val borderColor = if (isSelected) Color(0xFF00E5FF) else Color(0x55FFFFFF)
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val shape = if (control.type == "chip") RoundedCornerShape(8.dp) else CircleShape

    val bindingBadge = when {
        control.boundMouseButton != "none" -> control.boundMouseButton
        control.boundKeyId != null -> KeyMap.getKeyName(control.boundKeyId)
        else -> ""
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset(x = centerX - widthDp / 2, y = centerY - heightDp / 2)
            .size(widthDp, heightDp)
            .clip(shape)
            .background(Color(0x3310141D).copy(alpha = control.opacity * 0.6f))
            .border(borderWidth, borderColor, shape)
            .pointerInput(control.id) {
                detectTapGestures { onSelect() }
            }
            .pointerInput(control.id) {
                detectDragGestures(
                    onDragStart = { onSelect() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount.x, dragAmount.y)
                    }
                )
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = control.label.ifBlank { control.id.uppercase() },
                color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (bindingBadge.isNotEmpty()) {
                Text(
                    text = bindingBadge,
                    color = Color(0xFFFFD600),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Corner reticles for selected item
        if (isSelected) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val reticleLen = 10.dp.toPx()
                val reticleColor = Color(0xFF00E5FF)
                val stroke = 2.5f

                // Top-Left
                drawLine(reticleColor, Offset(0f, 0f), Offset(reticleLen, 0f), stroke)
                drawLine(reticleColor, Offset(0f, 0f), Offset(0f, reticleLen), stroke)

                // Top-Right
                drawLine(reticleColor, Offset(size.width, 0f), Offset(size.width - reticleLen, 0f), stroke)
                drawLine(reticleColor, Offset(size.width, 0f), Offset(size.width, reticleLen), stroke)

                // Bottom-Left
                drawLine(reticleColor, Offset(0f, size.height), Offset(reticleLen, size.height), stroke)
                drawLine(reticleColor, Offset(0f, size.height), Offset(0f, size.height - reticleLen), stroke)

                // Bottom-Right
                drawLine(reticleColor, Offset(size.width, size.height), Offset(size.width - reticleLen, size.height), stroke)
                drawLine(reticleColor, Offset(size.width, size.height), Offset(size.width, size.height - reticleLen), stroke)
            }
        }
    }
}

/**
 * Top control bar with profile selector, snap toggle, export/import, save, and exit buttons.
 */
@Composable
private fun EditorTopBar(
    activeProfileName: String,
    snapMode: SnapMode,
    onCycleSnap: () -> Unit,
    onOpenProfileMenu: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onSave: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xE610141D))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        // Exit / Done Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onExit() }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text("DONE", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        // Active Profile Selector
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onOpenProfileMenu() }
                .background(Color(0x33FFFFFF))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                text = "Profile: $activeProfileName",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("▼", color = Color(0xFF00E5FF), fontSize = 9.sp)
        }

        // Action Tools (Snap, Export, Import, Save)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Snap toggle pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onCycleSnap() }
                    .background(if (snapMode != SnapMode.OFF) Color(0x4D00E5FF) else Color(0x22FFFFFF))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = snapMode.label,
                    color = if (snapMode != SnapMode.OFF) Color(0xFF00E5FF) else Color(0xFFB0BEC5),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Export / Import
            TextButton(
                onClick = onExport,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Export", color = Color(0xFFEEEEEE), fontSize = 11.sp)
            }
            TextButton(
                onClick = onImport,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Import", color = Color(0xFFEEEEEE), fontSize = 11.sp)
            }

            // Save layout
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676), contentColor = Color.Black),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Save", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Floating toolbar docked at screen bottom for selected control size, opacity, and binding inspector.
 */
@Composable
private fun EditorControlPropertyToolbar(
    control: ControlConfig,
    onUpdateSize: (Float) -> Unit,
    onUpdateOpacity: (Float) -> Unit,
    onOpenInspector: () -> Unit,
    onResetControl: () -> Unit,
    onDeselect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xF2161B26),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x4D00E5FF)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Selected info tag
            Column {
                Text(
                    text = "${control.label.ifBlank { control.id }} [${control.id}]",
                    color = Color(0xFF00E5FF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "X: ${control.x.roundToInt()}% Y: ${control.y.roundToInt()}%",
                    color = Color(0xFF90A4AE),
                    fontSize = 10.sp
                )
            }

            // Size Slider
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Size: ${control.width.roundToInt()}%", color = Color.White, fontSize = 11.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Slider(
                    value = control.width,
                    onValueChange = onUpdateSize,
                    valueRange = 3.0f..30.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00E5FF),
                        activeTrackColor = Color(0xFF00E5FF),
                        inactiveTrackColor = Color(0x33FFFFFF)
                    ),
                    modifier = Modifier.width(110.dp)
                )
            }

            // Opacity Slider
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Opacity: ${(control.opacity * 100).roundToInt()}%", color = Color.White, fontSize = 11.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Slider(
                    value = control.opacity,
                    onValueChange = onUpdateOpacity,
                    valueRange = 0.1f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00E676),
                        activeTrackColor = Color(0xFF00E676),
                        inactiveTrackColor = Color(0x33FFFFFF)
                    ),
                    modifier = Modifier.width(110.dp)
                )
            }

            // Inspector Button
            Button(
                onClick = onOpenInspector,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Inspector", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            // Reset Control Button
            OutlinedButton(
                onClick = onResetControl,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Reset", color = Color(0xFFFFD600), fontSize = 11.sp)
            }

            // Deselect (X)
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF))
                    .clickable { onDeselect() },
                contentAlignment = Alignment.Center
            ) {
                Text("✕", color = Color.White, fontSize = 11.sp)
            }
        }
    }
}

/**
 * Property Inspector Dialog for rebinding keys (searchable KeyId 0..127), mouse buttons, and behavior.
 */
@Composable
private fun ControlPropertyInspectorDialog(
    control: ControlConfig,
    onDismiss: () -> Unit,
    onApply: (ControlConfig) -> Unit,
    onResetToDefault: () -> Unit
) {
    var labelValue by remember { mutableStateOf(control.label) }
    var selectedBehavior by remember { mutableStateOf(control.behavior) }
    var selectedMouseButton by remember { mutableStateOf(control.boundMouseButton) }
    var selectedKeyId by remember { mutableStateOf(control.boundKeyId) }
    var keySearchQuery by remember { mutableStateOf("") }
    var activeBindingTab by remember {
        mutableStateOf(
            if (control.boundMouseButton != "none") 1
            else if (control.boundKeyId != null) 0
            else 2
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF161B26),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x4D00E5FF)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "Inspector: ${control.id.uppercase()}",
                            color = Color(0xFF00E5FF),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Type: ${control.type}  •  Current: ${
                                when {
                                    selectedMouseButton != "none" -> "Mouse $selectedMouseButton"
                                    selectedKeyId != null -> "Key: ${KeyMap.getKeyName(selectedKeyId)}"
                                    else -> "Unbound"
                                }
                            }",
                            color = Color(0xFF90A4AE),
                            fontSize = 11.sp
                        )
                    }
                    TextButton(onClick = onResetToDefault) {
                        Text("Reset to Default", color = Color(0xFFFFD600), fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Label and Behavior row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = labelValue,
                        onValueChange = { labelValue = it },
                        label = { Text("Display Label") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0x66FFFFFF)
                        )
                    )

                    // Behavior Chips
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Behavior Mode", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("hold" to "HOLD", "toggle" to "TOGGLE", "tap" to "TAP").forEach { (bVal, bLabel) ->
                                val isSelected = (selectedBehavior == bVal)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color(0xFF00E5FF) else Color(0x22FFFFFF))
                                        .clickable { selectedBehavior = bVal }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = bLabel,
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Input Binding Tabs [Keyboard Key | Mouse Button | None]
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("Keyboard Key" to 0, "Mouse Button" to 1, "Unbound / None" to 2).forEach { (tabTitle, tabIndex) ->
                        val isTabActive = (activeBindingTab == tabIndex)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isTabActive) Color(0xFF00E5FF) else Color(0x1AFFFFFF))
                                .clickable {
                                    activeBindingTab = tabIndex
                                    when (tabIndex) {
                                        1 -> {
                                            selectedKeyId = null
                                            if (selectedMouseButton == "none") selectedMouseButton = "LMB"
                                        }
                                        2 -> {
                                            selectedKeyId = null
                                            selectedMouseButton = "none"
                                        }
                                        0 -> {
                                            selectedMouseButton = "none"
                                            if (selectedKeyId == null) selectedKeyId = 0
                                        }
                                    }
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = tabTitle,
                                color = if (isTabActive) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tab Content
                Box(modifier = Modifier.weight(1f)) {
                    when (activeBindingTab) {
                        0 -> {
                            // Searchable Key Selector
                            Column(modifier = Modifier.fillMaxSize()) {
                                OutlinedTextField(
                                    value = keySearchQuery,
                                    onValueChange = { keySearchQuery = it },
                                    placeholder = { Text("Search key (e.g. Space, Shift, C, W, F1)...") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF00E5FF),
                                        unfocusedBorderColor = Color(0x33FFFFFF)
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                val filteredKeys = remember(keySearchQuery) { KeyMap.searchKeys(keySearchQuery) }
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 70.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(filteredKeys) { keyDef ->
                                        val isKeySelected = (selectedKeyId == keyDef.keyId)
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isKeySelected) Color(0xFF00E5FF) else Color(0x22FFFFFF))
                                                .border(1.dp, if (isKeySelected) Color.White else Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                                                .clickable {
                                                    selectedKeyId = keyDef.keyId
                                                    selectedMouseButton = "none"
                                                }
                                                .padding(vertical = 8.dp, horizontal = 4.dp)
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = keyDef.name,
                                                    color = if (isKeySelected) Color.Black else Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center
                                                )
                                                Text(
                                                    text = keyDef.category,
                                                    color = if (isKeySelected) Color(0xFF004D40) else Color(0xFF90A4AE),
                                                    fontSize = 8.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            // Mouse Button Selector
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Select Bound Mouse Button:", color = Color.White, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    listOf("LMB" to "Left Click (LMB)", "RMB" to "Right Click (RMB)", "MMB" to "Middle (MMB)", "X1" to "Side 1 (X1)", "X2" to "Side 2 (X2)").forEach { (btnCode, btnLabel) ->
                                        val isBtnSelected = (selectedMouseButton == btnCode)
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isBtnSelected) Color(0xFF00E5FF) else Color(0x22FFFFFF))
                                                .border(1.dp, if (isBtnSelected) Color.White else Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                                                .clickable {
                                                    selectedMouseButton = btnCode
                                                    selectedKeyId = null
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                        ) {
                                            Text(
                                                text = btnLabel,
                                                color = if (isBtnSelected) Color.Black else Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        2 -> {
                            // Unbound
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text("This control will not emit keyboard or mouse inputs.", color = Color(0xFF90A4AE), fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Dialog Actions
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val updated = control.copy(
                                label = labelValue.trim(),
                                behavior = selectedBehavior,
                                boundKeyId = if (activeBindingTab == 0) selectedKeyId else null,
                                boundMouseButton = if (activeBindingTab == 1) selectedMouseButton else "none"
                            )
                            onApply(updated)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Apply Changes", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun snapCoordinate(value: Float, snapStep: Float): Float {
    if (snapStep <= 0.0f) return value
    return (Math.round(value / snapStep) * snapStep).coerceIn(0.0f, 100.0f)
}
