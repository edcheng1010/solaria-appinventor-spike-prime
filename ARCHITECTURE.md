# LEGO SPIKE Prime App Inventor Extension - Architecture Document

> **Unofficial integration.** This is an independent open-source project. It is **not affiliated with, endorsed by, or sponsored by the LEGO Group or the Massachusetts Institute of Technology.** "LEGO", "SPIKE Prime", and "App Inventor" are trademarks of their respective owners; references in this project are nominative — used solely to describe hardware compatibility. See [NOTICE](NOTICE) for full trademark and licensing notices.

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

## 4. SSP v0.6 WIRE PROTOCOL (Phase 2 — current implementation)

**This section supersedes the old "CRITICAL PROTOCOL CORRECTION" note.** The extension now implements [SSP v0.6](https://github.com/edcheng1010/solaria-hub/blob/main/spec/SSP-v0.6.md) — the Solaria Standard Protocol — for all motor, sensor, and LED commands. See `docs/SSP_BRIDGE_GUIDE.md` for the full SPIKE Prime → SSP mapping.

### 4.1 Transport (unchanged from MVP)
All communication uses TunnelMessage (opcode `0x32`) over BLE, COBS-encoded.

**On first connection to a new hub:**
1. Clear slot: `0x46 0x00` (ClearSlotRequest)
2. Start file upload: `0x0C` + filename + slot + CRC32 (StartFileUploadRequest)
3. Transfer chunks: `0x10` + running_CRC32 + chunk_size + chunk_data × N
4. Start program: `0x1E 0x00 0x00` (ProgramFlowRequest)

**On reconnect to the same hub:** the upload is skipped — a ProgramFlow probe is sent directly and the hub restarts the cached program (saves ~3–4 s).

### 4.2 SSP message format
Commands are UTF-8 JSON newline-terminated strings wrapped in TunnelMessage:

```
{"cmd":"motor.run","port":"A","speed":75}\n
{"cmd":"sensor.read","port":"C","type":"color"}\n
{"cmd":"system.ping"}\n
```

Responses from the hub are the same format:

```
{"type":"capability","device":"spike-prime","ssp_version":"0.6","ports":[...]}\n
{"event":"sensor","port":"C","type":"color","value":"red"}\n
{"event":"pong"}\n
```

### 4.3 Hub-side Python (`src/resources/hub_controller.py`)
The Python program runs on the hub, parses incoming SSP JSON via `json.loads()`, and dispatches to motor, LED, sensor, and system handlers. It sends a capability declaration on startup and responds to heartbeat pings every 5 s.

Key hub-side API used:
- `hub.config['module_tunnel']` — tunnel module access
- `tunnel.callback(fn)` — register SSP command handler
- `tunnel.send(bytes)` — send SSP events back to client
- `motor.run(port, velocity)` — motor at –1100 to +1100 deg/s (speed × 11)
- `motor_pair.move(PAIR_1, steering, velocity=v)` — coordinated drive base
- `light_matrix.set_pixel(x, y, brightness)` — 5×5 LED matrix

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

**Rule for Claude Code:** All motor/LED/sensor control goes through `sendSSP(SSPMessage)` in `LegoSpikeConnectivity`. Never use `sendCommand(String)` with custom strings. The wire format is SSP v0.6 JSON — see CLAUDE.md Rule 5 and `docs/SSP_BRIDGE_GUIDE.md`.

## 5. BLE Connection Mechanism (Critical — Discovered April 2026)

This section documents how the extension actually communicates with the BluetoothLE component
and why the original approach was entirely broken.

### 5.1 BluetoothLE is a Separate Extension, Not a Built-in

The `edu.mit.appinventor.ble.BluetoothLE` component is a separately distributed `.aix` extension,
not part of the App Inventor runtime. Our extension cannot import its classes at compile time.
All interaction must use Java reflection.

### 5.2 Why `BluetoothLE_*` Methods Were Dead Code

The original code contained public methods like `BluetoothLE_Connected(String address)`,
`BluetoothLE_Disconnected()`, and `BluetoothLE_DeviceFound(String, String, int)`.
These were **never called** and never could be, because:

- `EventDispatcher.dispatchEvent(bleComponent, "Connected")` fires events **only to App Inventor
  block handlers registered on the BluetoothLE component itself**. It does not call methods on
  other extensions.
- The BLE `Connected` event also has **0 parameters** — the dead `BluetoothLE_Connected(String)`
  had the wrong signature anyway.
- There is no mechanism in App Inventor for one extension to automatically intercept another
  extension's events.

**Consequence:** `isConnected` never became `true`, `HubConnected` never fired. This bug existed
across all 17 original Manus development sessions.

### 5.3 The Fix: Java Dynamic Proxy as BluetoothConnectionListener

`BluetoothLE.java` exposes a `BluetoothConnectionListener` interface and
`addConnectionListener(listener)` / `removeConnectionListener(listener)` methods.
When GATT connection + service discovery complete, `BluetoothLEint.java` calls
`listener.onConnected(bleInstance)` on every registered listener — this IS a direct Java call,
not EventDispatcher.

Since we cannot import `BluetoothConnectionListener` at compile time, we create it at runtime
using `java.lang.reflect.Proxy`:

```java
Class<?> iface = ble.getClass().getClassLoader()
    .loadClass("edu.mit.appinventor.ble.BluetoothLE$BluetoothConnectionListener");
Object proxy = Proxy.newProxyInstance(ble.getClass().getClassLoader(),
    new Class<?>[]{ iface },
    (p, method, args) -> {
        if ("onConnected".equals(method.getName()))   handleBleConnected(args[0]);
        if ("onDisconnected".equals(method.getName())) handleBleDisconnected();
        return null;
    });
ble.getClass().getMethod("addConnectionListener", iface).invoke(ble, proxy);
```

This proxy is registered in the `BluetoothDevice(Component ble)` property setter and removed
when the BLE component changes.

### 5.4 Connection Polling Fallback

As a safety net against older BLE extension builds or proxy registration failures, a
`Timer connectionPollTimer` checks `IsDeviceConnected()` every 500 ms for up to 10 seconds
after `ConnectWithAddress` is called. If it returns `true` while `isConnected` is `false`,
`onConnected()` is called directly. The guard `if (isConnected) return` in `onConnected()`
prevents double-firing if both mechanisms trigger.

### 5.5 User-Wired Block Requirements

Two events from BluetoothLE **cannot** be intercepted automatically and **must** be wired in
App Inventor blocks by the user:

| BluetoothLE event | Wire to | Effect |
|---|---|---|
| `BluetoothLE1.BytesReceived(serviceUuid, charUuid, byteValues)` | `LegoSpikePrime1.OnBytesReceivedFromHub(serviceUuid, charUuid, byteValues)` | Feeds incoming BLE bytes into the SPIKE Prime frame buffer |
| `BluetoothLE1.ConnectionFailed(reason)` | `LegoSpikePrime1.OnConnectionFailed(reason)` | Stops polling immediately; resumes scanning; fires ErrorOccurred |

Without the `BytesReceived` wiring, no data from the hub (InfoResponse, rdy, TunnelMessage)
will ever be received.

## 6. Future Extensibility Strategy
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
