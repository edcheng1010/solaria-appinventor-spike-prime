> *Unofficial — independent open-source project, not affiliated with the LEGO Group or the Massachusetts Institute of Technology. See [NOTICE](../NOTICE) for trademark and licensing details.*

# Compilation and Debugging Guide

This guide provides instructions for compiling the LEGO SPIKE Prime App Inventor extension on a Windows machine and debugging it on an Android device.

## 1. Compilation Instructions (Windows)

### Prerequisites
1. **Java Development Kit (JDK):** Ensure you have JDK 8 or 11 installed. App Inventor extensions typically require older Java versions for compatibility.
2. **Apache Ant:** Download and install Apache Ant. Add its `bin` directory to your system's `PATH` environment variable.
3. **Git:** Ensure Git is installed for version control.

### Directory Structure
Your local repository should look like this:
```
solaria-appinventor-spike-prime/
├── build.xml
├── config/
│   └── extension.json
├── src/
│   └── solaria/appinventor/spikeprime/
│       ├── LegoSpikeConnectivity.java
│       ├── BluetoothInterfaceImpl.java
│       └── ... (other component + protocol helper classes)
```

### Compilation Steps
1. Open the Windows Command Prompt (`cmd.exe`).
2. Navigate to the root directory of your cloned repository:
   ```cmd
   cd path\to\solaria-appinventor-spike-prime
   ```
3. Run the Ant build command:
   ```cmd
   ant extensions
   ```
   *(Note: If you have a specific `build.xml` target for extensions, use that. The standard App Inventor extension build command is usually `ant extensions`.)*
4. If the build is successful, the compiled `.aix` file will typically be located in a `build/` or `out/` directory, depending on your `build.xml` configuration.

## 2. Debugging on Android Device

### Prerequisites
1. **Android Device:** A physical Android device with Bluetooth LE support.
2. **MIT AI2 Companion App:** Install the latest MIT AI2 Companion app from the Google Play Store on your Android device.
3. **App Inventor Project:** An App Inventor project where you have imported the compiled `.aix` extension.

### Debugging Steps
1. **Import Extension:** In your App Inventor project, go to the "Extension" palette, click "Import extension", and upload the compiled `.aix` file.
2. **Connect Device:**
   - Ensure your Android device and your computer are on the same Wi-Fi network.
   - In App Inventor, click "Connect" -> "AI Companion".
   - Open the MIT AI2 Companion app on your device and scan the QR code or enter the 6-character code.
3. **Testing:**
   - Once connected, interact with the extension blocks in your app.
   - **Important:** Ensure your Android device has Bluetooth enabled and Location permissions granted (required for BLE scanning on Android).
4. **Viewing Logs (Logcat):**
   - To see detailed debug logs from the extension (e.g., `Log.d`, `Log.e` statements in the Java code), you need to use Android's Logcat.
   - **Option A (Android Studio):** If you have Android Studio installed, connect your device via USB, open Android Studio, and view the "Logcat" tab. Filter by your app's package name or specific tags used in the extension (e.g., "LegoSpikePrime").
   - **Option B (ADB Command Line):** Install the Android SDK Platform-Tools. Connect your device via USB, open Command Prompt, and run:
     ```cmd
     adb logcat -s LegoSpikePrime
     ```
     *(Replace "LegoSpikePrime" with the actual log tag used in your Java code).*

### Common Issues
- **Extension not loading:** Ensure the `.aix` file was compiled correctly without errors.
- **Bluetooth not scanning:** Verify Location permissions are granted to the Companion app.
- **Connection failing:** Double-check the UUIDs in `BluetoothInterfaceImpl.java` against the official SPIKE Prime documentation. Ensure the hub is turned on and not connected to another device.
