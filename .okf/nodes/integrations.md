---
id: node:integrations
title: Third-Party Services, Environment & Integration Specifications
version: 1.0.0
framework: google-okf
category: architecture
tags: [integrations, env, apis, nuget, gradle, win32, android-sdk, vigem, qr-scanner]
relations: [node:architecture]
summary: Authoritative third-party SDK dependencies, NuGet packages, Gradle coordinates, Win32 P/Invoke APIs, Android OS services, and network firewall configuration.
---

<!--
@aegis-contract
@claim Third-party dependencies, P/Invoke signatures, Android OS services, and network firewall configuration
@true Implements pinned NuGet and Gradle dependencies, Win32 P/Invoke signatures, native Android service APIs, and UDP firewall binding with zero plaintext credentials
@false Permits dummy placeholder stubs, unpinned dependency ranges, unhandled network exceptions, or hardcoded secrets
-->

# Third-Party Services, Environment & Integration Specifications

## 1. Environment & Runtime Configuration

### 1.1 Secrets & Cryptographic Key Storage
- **Zero Static Credentials:** No hardcoded secrets, API tokens, or pre-shared keys are committed to source control or `.env`.
- **Dynamic Key Generation:** The Windows receiver generates a cryptographically secure 256-bit random pairing key (`RandomNumberGenerator.GetBytes(32)`).
- **Windows Key Storage:** Encrypted at rest using Windows Data Protection API (DPAPI) via `ProtectedData.Protect` scoped to `DataProtectionScope.CurrentUser`.
- **Android Key Storage:** Encrypted at rest using hardware-backed AndroidKeyStore with AES-256-GCM authenticated cipher.
- **Runtime CLI Flags:**
  - `--debug-key`: Debug-only switch enabling a hardcoded session/key for Phase 1 & 2 unit/integration tests before pairing is implemented. Strictly excluded from release builds via preprocessor directives (`#if DEBUG`).

### 1.2 Network & Windows Firewall Integration
- **UDP Port:** Default `47789` (configurable via settings).
- **Firewall Inbound Rule Command (Elevated PowerShell):**
  ```powershell
  netsh advfirewall firewall add rule name="PhoneGamepadReceiver" dir=in action=allow protocol=UDP localport=47789 profile=private,domain
  ```
- **Binding Policy:** Receiver socket binds exclusively to local private network interfaces (`IPAddress.Any` restricted to private subnet ranges RFC 1918) and rejects traffic arriving from non-LAN interfaces.

---

## 2. Windows Receiver Dependencies & SDK Contracts

### 2.1 Pinned NuGet Package Registry (.NET 10 WPF)
```xml
<ItemGroup>
  <!-- System tray integration with modern WPF taskbar notification support -->
  <PackageReference Include="H.NotifyIcon.Wpf" Version="2.1.3" />

  <!-- Zero-dependency high performance QR code generation -->
  <PackageReference Include="QRCoder" Version="1.6.0" />

  <!-- DPAPI data protection for sensitive pairing key storage -->
  <PackageReference Include="System.Security.Cryptography.ProtectedData" Version="9.0.0" />

  <!-- Virtual Xbox 360 Gamepad fallback client (Phase 8 only) -->
  <PackageReference Include="Nefarius.ViGEm.Client" Version="1.21.256" />
</ItemGroup>
```

### 2.2 Win32 Native P/Invoke Contracts (`user32.dll`)
- **`SendInput`:**
  ```csharp
  [DllImport("user32.dll", SetLastError = true)]
  public static extern uint SendInput(uint nInputs, [In] INPUT[] pInputs, int cbSize);
  ```
- **Foreground Focus Guard:**
  ```csharp
  [DllImport("user32.dll")]
  public static extern IntPtr GetForegroundWindow();

  [DllImport("user32.dll", SetLastError = true)]
  public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);
  ```
- **Global Kill-Switch Hotkey:**
  ```csharp
  [DllImport("user32.dll", SetLastError = true)]
  public static extern bool RegisterHotKey(IntPtr hWnd, int id, uint fsModifiers, uint vk);

  [DllImport("user32.dll", SetLastError = true)]
  public static extern bool UnregisterHotKey(IntPtr hWnd, int id);
  ```

---

## 3. Android Controller Dependencies & OS Service Contracts

### 3.1 Pinned Gradle Dependency Registry (Kotlin & Jetpack Compose)
```kotlin
dependencies {
    // Jetpack Compose BOM & Core
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    // Google Play Services Code Scanner (Zero camera permission required)
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    // Kotlinx Serialization for Layout JSON profiles
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // AndroidX Core & Navigation
    implementation("androidx.core:core-ktx:1.13.1")
}
```

### 3.2 Android OS Native Services
- **Google Code Scanner:**
  ```kotlin
  val options = GmsBarcodeScannerOptions.Builder()
      .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
      .build()
  val scanner = GmsBarcodeScanning.getClient(context, options)
  scanner.startScan()
      .addOnSuccessListener { barcode -> handleQrPayload(barcode.rawValue) }
      .addOnFailureListener { error -> logScanError(error) }
  ```
- **Low-Latency Wi-Fi Lock:**
  ```kotlin
  val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
  val wifiLock = wifiManager.createWifiLock(
      WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
      "Gamepad:LowLatencyLock"
  )
  wifiLock.acquire()
  ```
- **Haptic Actuation:**
  ```kotlin
  val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
      manager.defaultVibrator
  } else {
      @Suppress("DEPRECATION")
      context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
  }
  vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
  ```
- **System Gesture Exclusion:**
  ```kotlin
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      view.systemGestureExclusionRects = listOf(leftExclusionRect, rightExclusionRect)
  }
  ```
- **Unbuffered Touch Dispatch:**
  ```kotlin
  view.requestUnbufferedDispatch(motionEvent)
  ```

---

## 4. Skill Discovery via FastMCP
Skills are dynamically discovered on-demand using FastMCP tool `okf_list_available_skills(query=...)` and retrieved via `okf_get_skill_content(skill_name=...)`. Static hardcoded skill registries are strictly prohibited.
