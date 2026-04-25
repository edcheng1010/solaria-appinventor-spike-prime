# LEGO SPIKE Prime App Inventor Extension - Architecture Document

## 1. Project Overview
This project is an MIT App Inventor extension designed to provide reliable, intuitive Bluetooth Low Energy (BLE) communication with LEGO SPIKE Prime hubs. It is built specifically for educational environments (classrooms, maker spaces) where multiple hubs operate simultaneously and reliability is paramount.

## 2. Current Architectural State (The "Split")
The codebase currently contains two distinct architectural approaches. **Claude Code must understand this split before making any changes.**

### 2.1 The Active Architecture (MVP)
This is the working, self-contained implementation that must be used for the MVP.
- **Location:** `io.github.appinventor.legospikeprime` package
- **Files:** `LegoSpikePrime.java`, `BluetoothInterfaceImpl.java`
- **Status:** Fully functional, tested, and stable.
- **Characteristics:** Uses the App Inventor `BluetoothLE` component for underlying communication. Handles its own device list, RSSI staleness, and basic command sending.

### 2.2 The Planned Architecture (Future Extensibility)
This is a more sophisticated protocol implementation that was developed but **not yet integrated** into the main extension.
- **Location:** `io.github.appinventor.legospike` package
- **Files:** 13 helper classes (`SpikeProtocol.java`, `COBSEncoder.java`, `MessageHandler.java`, etc.)
- **Status:** Code complete but disconnected from the main extension.
- **Purpose:** Designed to handle the full complexity of the SPIKE Prime protocol (COBS encoding, CRC32, message parsing) and to allow future extensibility to other LEGO hubs.

**Rule for Claude Code:** For immediate bug fixes or minor features on the MVP, modify the Active Architecture. For major protocol upgrades, begin the work of integrating the Planned Architecture into the Active Architecture.

## 3. Critical Design Decisions and Fixes (DO NOT REVERT)

Over 17 iterations, several critical decisions were made to solve specific hardware/BLE issues. **These must be preserved.**

### 3.1 Device Detection: RSSI Staleness (Not Blacklists)
- **Problem:** BLE devices that are turned off often remain in the Android BLE cache, appearing as "ghost" devices that cannot be connected to.
- **Failed Approach:** Using a timeout blacklist. It was too complex and buggy.
- **Current Solution:** Hybrid RSSI Staleness.
  - Real devices have fluctuating RSSI values.
  - Cached/ghost devices have static RSSI values.
  - The `LegoHub` class tracks `rssiStaleCount`. If the RSSI has not changed for 3 consecutive scans AND the timestamp is old, the device is hidden from the UI.
  - **Code Location:** `LegoSpikePrime.java` -> `LegoHub.update()` and `CheckAllDevices()`.

### 3.2 Null Pointer Protection
- **Problem:** Asynchronous BLE events often trigger when device addresses or names are null, causing hard crashes.
- **Current Solution:** Strict null checking before any HashMap access or list iteration.
- **Code Location:** `LegoSpikePrime.java` -> `HubListChanged` event logic (lines 646-684). It uses a safe nested loop approach rather than relying on HashMaps with potentially null keys.

### 3.3 UUID Authentication
- **Problem:** Connecting to the wrong BLE device.
- **Current Solution:** Strict UUID verification using the official SPIKE Prime UUIDs, NOT the general LEGO Wireless Protocol UUIDs.
- **Correct UUIDs:**
  - Service: `0000fd02-0000-1000-8000-00805f9b34fb`
  - RX (write to hub): `0000fd02-0001-1000-8000-00805f9b34fb`
  - TX (read from hub): `0000fd02-0002-1000-8000-00805f9b34fb`
- **Code Location:** `BluetoothInterfaceImpl.java`.

### 3.4 Scanning State Management
- **Problem:** Scanning while trying to connect causes connection failures.
- **Current Solution:** The extension explicitly stops scanning before initiating a connection, and uses a `wasScanningBeforeConnection` flag to resume scanning if the connection fails or drops.

## 4. CRITICAL PROTOCOL CORRECTION (Discovered April 25, 2026)

**This is the most important finding since the project began.** Competitive analysis and review of LEGO's official documentation revealed that our current motor/LED control approach is fundamentally incorrect for SPIKE Prime 3.x firmware.

### 4.1 The Problem
Our current code attempts to send direct motor/LED commands via BLE. This approach works for LEGO Wireless Protocol 3.0 devices (SPIKE Essential, Boost, Technic) but does NOT work for SPIKE Prime 3.x firmware.

### 4.2 The Correct Architecture (Confirmed by LEGO Developer)
SPIKE Prime 3.x uses a **two-part architecture**:

**Part 1: Upload a Python controller program to the hub**
1. Clear slot: Send `0x46 0x00` (ClearSlotRequest)
2. Start file upload: Send `0x0C` + filename + slot + CRC32 (StartFileUploadRequest)
3. Transfer chunks: Send `0x10` + running_CRC32 + chunk_size + chunk_data (TransferChunkRequest) x N
4. Start program: Send `0x1E 0x00 0x00` (ProgramFlowRequest)

**Part 2: Real-time control via TunnelMessage**
1. Send commands: `0x32` + payload_size(2 bytes) + command_data (TunnelMessage)
2. Receive responses: Hub-side program sends back data via `tunnel.send()`
3. Hub-side Python uses `hub.config['module_tunnel']` for bidirectional communication

### 4.3 Hub-Side Python Program
The extension must embed a Python controller program that runs on the hub. The hub-side program receives commands via the tunnel callback and controls motors, LEDs, and sensors accordingly. It sends acknowledgments and sensor data back via `tunnel.send()`.

Key hub-side API:
- `hub.config['module_tunnel']` - Access the tunnel module
- `tunnel.callback(handler_function)` - Register callback for incoming messages
- `tunnel.send(bytes)` - Send data back to the connected device
- `motor.run(port.X, speed)` - Control motor on port X
- `light_matrix.set_pixel(x, y, brightness)` - Control LED matrix

### 4.4 COBS Encoding Constants (Verified from Working Implementation)

| Constant | Value | Description |
|----------|-------|-------------|
| DELIMITER | 0x02 | Frame delimiter byte |
| NO_DELIMITER | 255 | Code word when no delimiter in block |
| MAX_BLOCK_SIZE | 84 | Maximum bytes per COBS block |
| COBS_CODE_OFFSET | 2 | Offset added to code words |
| XOR | 0x03 | XOR mask applied after COBS encoding |

**Pack:** data -> COBS encode -> XOR each byte with 0x03 -> append delimiter 0x02

**Unpack:** strip delimiter -> XOR each byte with 0x03 -> COBS decode

### 4.5 TunnelMessage Format

| Byte | Content | Description |
|------|---------|-------------|
| 0 | 0x32 | TunnelMessage type identifier |
| 1-2 | payload_size | uint16 little-endian (e.g., 0x0a 0x00 = 10 bytes) |
| 3+ | payload_data | The command string or binary data |

### 4.6 Reference Implementations
- **LEGO Official:** https://github.com/LEGO/spike-prime-docs/tree/main/examples/python (app.py, messages.py, cobs.py)
- **Working WebBluetooth:** https://github.com/etomasfe/SpikeRemoteControl (ControlSpike.html)
- **Protocol Docs:** https://lego.github.io/spike-prime-docs/

### 4.7 Impact on Development
- The BLE scanning, connection, RSSI staleness, and null pointer protections are ALL CORRECT and should be preserved
- The motor/LED control methods need to be rewritten to use TunnelMessage
- A program upload capability needs to be added
- The hub-side Python program needs to be designed and embedded
- COBSEncoder.java constants MUST be verified against the values in Section 4.4

**Rule for Claude Code:** Before implementing any motor/LED/sensor control, you MUST use the TunnelMessage approach. Direct BLE commands for hardware control do NOT work on SPIKE Prime 3.x.

## 5. Future Extensibility Strategy
To support older/legacy LEGO robotics products in the future:
1. The `BluetoothInterfaceImpl` should be abstracted into an interface (`ILegoBluetooth`).
2. Create specific implementations (e.g., `SpikePrimeBluetoothImpl`, `Ev3BluetoothImpl`).
3. The main `LegoSpikePrime` class should act as a facade, routing commands to the appropriate implementation based on the connected device's advertised services.
4. The Planned Architecture (`io.github.appinventor.legospike`) provides the foundation for this modular approach.

## 6. Key Technical References

| Resource | URL | Purpose |
|----------|-----|---------|
| SPIKE Prime BLE Protocol | https://lego.github.io/spike-prime-docs/ | Official protocol specification |
| LEGO Python Examples | https://github.com/LEGO/spike-prime-docs/tree/main/examples/python | Reference implementation |
| etomasfe SpikeRemoteControl | https://github.com/etomasfe/SpikeRemoteControl | Working WebBluetooth proof-of-concept |
| SPIKE Prime Python API (Tufts) | https://tuftsceeo.github.io/SPIKEPythonDocs/SPIKE3.html | Hub-side Python API reference |
| LEGO Issue #3 (TunnelMessage) | https://github.com/LEGO/spike-prime-docs/issues/3 | LEGO developer confirms approach |
