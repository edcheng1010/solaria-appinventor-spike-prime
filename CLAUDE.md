# Claude Code Project Memory

> **Unofficial integration.** Independent open-source project, not affiliated with the LEGO Group or the Massachusetts Institute of Technology. See [NOTICE](NOTICE) for trademark and licensing details.

## Project Context
This is an MIT App Inventor extension for LEGO SPIKE Prime hubs, developed by Edward Cheng. The goal is to provide a highly reliable, crash-free Bluetooth Low Energy (BLE) connection experience for classrooms with multiple hubs.

## Critical Architectural Rules (DO NOT VIOLATE)

1. **NEVER overwrite the SPIKE Prime UUIDs.**
   - Service: `0000fd02-0000-1000-8000-00805f9b34fb`
   - RX (write to hub): `0000fd02-0001-1000-8000-00805f9b34fb`
   - TX (read from hub): `0000fd02-0002-1000-8000-00805f9b34fb`
   - *Note: These are specific to SPIKE Prime 3.x. Do not use the general LEGO Wireless Protocol UUIDs (`00001623...`). Those are for SPIKE Essential, Boost, and Technic.*

2. **NEVER remove the RSSI Staleness Logic.**
   - Device detection relies on RSSI staleness, NOT timeouts or blacklists.
   - If a device's RSSI does not change for 3 consecutive scans, it is considered a "ghost" device (turned off but cached by Android) and must be hidden.
   - This logic is in `LegoSpikeConnectivity.java` -> the `LegoHub` inner class (`updateRssi()` tracks `rssiStaleCount`; `isVisible()` applies the 3-consecutive-scans rule).

3. **ALWAYS check for nulls in asynchronous BLE events.**
   - Android BLE callbacks often fire with null device names or addresses.
   - Before accessing any HashMap or list with a device address, verify `if (address != null)`.
   - The `HubListChanged` event logic specifically uses a safe nested loop approach to avoid `NullPointerException`s. Do not refactor this into a HashMap-based approach without strict null safety.

4. **ALWAYS manage scanning state before connecting.**
   - You must stop scanning before initiating a connection to a hub.
   - Use the `wasScanningBeforeConnection` flag to resume scanning if the connection fails or drops.

5. **ALWAYS use SSP v0.8 JSON commands over TunnelMessage — never custom binary strings.**
   - SPIKE Prime 3.x does NOT support direct hardware control via raw BLE commands.
   - The wire format is SSP v0.8 JSON (e.g. `{"cmd":"motor.run","port":"A","speed":75}`) sent as a newline-terminated UTF-8 string via TunnelMessage (opcode 0x32), COBS-encoded.
   - Use `LegoSpikeConnectivity.sendSSP(SSPMessage)` — never `sendCommand(String)` with custom binary strings.
   - The hub-side Python controller (`hub_controller.py`) parses the SSP JSON; it is automatically uploaded on first connection to a new hub.
   - See `docs/SSP_BRIDGE_GUIDE.md` for the complete SSP v0.8 mapping and `spec/SSP-v0.8.md` in solaria-hub for the protocol specification.

6. **ALWAYS use correct COBS encoding constants.**
   - DELIMITER = 0x02
   - NO_DELIMITER = 255
   - MAX_BLOCK_SIZE = 84
   - COBS_CODE_OFFSET = 2
   - XOR = 0x03
   - Pack: data -> COBS encode -> XOR each byte with 0x03 -> append delimiter 0x02
   - Unpack: strip delimiter -> XOR each byte with 0x03 -> COBS decode

7. **ALWAYS use exact parameter types when calling BluetoothLE methods via reflection.**
   - Reflection requires parameter types to match the declared signature exactly.
   - `RegisterForBytes` takes 3 params: `(String, String, boolean)` — not 2.
   - `WriteBytes` 4th param is `Object.class`, not `YailList.class`.
   - Before adding or changing any `getMethod(...)` call, verify the signature against
     `BluetoothLE.java` source (in the AI2 tree at
     `appinventor/components/src/edu/mit/appinventor/ble/BluetoothLE.java`).

8. **NEVER assume BluetoothLE events call methods on this extension.**
   - `EventDispatcher.dispatchEvent(bleComponent, "Connected")` fires only to App Inventor
     block handlers. It does NOT call Java methods on other extensions.
   - Connection state MUST be tracked via a `BluetoothConnectionListener` dynamic proxy
     registered through `addConnectionListener()` — see ARCHITECTURE.md Section 5.
   - Byte reception and ConnectionFailed require user-wired blocks in App Inventor:
       `BluetoothLE1.BytesReceived → LegoSpikePrime1.OnBytesReceivedFromHub`
       `BluetoothLE1.ConnectionFailed → LegoSpikePrime1.OnConnectionFailed`
   - Any method named `BluetoothLE_*` that is not called from a user block or a registered
     listener is dead code and must be removed.

## Codebase Structure

All Java sources live in a single package: **`solaria.appinventor.spikeprime`**
(under `src/solaria/appinventor/spikeprime/`, tests under
`src/test/java/solaria/appinventor/spikeprime/`).

> **History:** the repo formerly carried a two-package "Split" —
> `io.github.appinventor.legospikeprime` (the active MVP: `LegoSpikePrime.java`,
> `BluetoothInterfaceImpl.java`) and `io.github.appinventor.legospike` (helper
> protocol classes). The two packages were **merged into the single
> `solaria.appinventor.spikeprime` package** (this fixed a `NoClassDefFoundError`
> where the AI2 build only included classes matching the extension's declared
> package, excluding the separate helper package), and the whole tree was later
> renamed to the `solaria.*` namespace. Do not reintroduce the split.

- The 8 component classes (`LegoSpikeConnectivity`, `LegoSpikeMotors`, etc.) and
  `BluetoothInterfaceImpl.java` are the active extension. **Modify these for
  immediate bug fixes or minor features.**
- The protocol helper classes (`COBSEncoder.java`, `SpikeCRC32.java`,
  `MessageFramer.java`, etc.) implement the SSP/COBS wire format.
  COBSEncoder.java constants MUST match the values in Rule 6 above.

## SPIKE Prime 3.x Communication Protocol

### Connection Flow
1. Scan for BLE devices advertising service UUID `0000fd02-...`
2. Connect to GATT server
3. Get primary service (`0000fd02-0000-...`)
4. Get RX characteristic (`0000fd02-0001-...`) for writing to hub
5. Get TX characteristic (`0000fd02-0002-...`) for reading from hub
6. Enable notifications on TX characteristic

### Program Upload Flow (Required Before Motor/LED Control)
1. Clear slot: Send `0x46 0x00` via RX (COBS encoded)
2. Start file upload: Send `0x0C` + "program.py\0" + slot(0x00) + CRC32 via RX (COBS encoded)
3. Transfer chunks: Send `0x10` + running_CRC32 + chunk_size + chunk_data via RX (COBS encoded)
4. Start program: Send `0x1E 0x00 0x00` via RX (COBS encoded)

### Real-Time Control Flow
1. Send TunnelMessage: `0x32` + payload_size(uint16 LE) + command_data via RX (COBS encoded)
2. Receive response: Hub-side program sends back via TX (COBS encoded)
3. Wait for "rdy" acknowledgment before sending next command

### Hub-Side Python Program (Must Be Embedded in Extension)
The extension must contain a Python program string that gets uploaded to the hub. This program:
- Imports `hub.config['module_tunnel']` for bidirectional communication
- Registers a callback via `tunnel.callback(handler_function)`
- Parses incoming command bytes and controls motors/LEDs/sensors
- Sends acknowledgments and sensor data back via `tunnel.send()`

## Development Workflow

1. **Read `ARCHITECTURE.md`** before proposing any major refactoring.
2. **Compile frequently** using the provided Ant build script (`build.xml` or `compile_windows.bat`).
3. **Test on physical devices.** The App Inventor emulator cannot test BLE extensions reliably.
4. **Commit small, logical changes.** Use Git branching for experimental features.
5. **Never modify files without explicit approval from Edward (project owner).**

## Development Epics

> **Terminology:** "Epic SPIKE-N" = repo-internal milestone. Ecosystem eras are **Generations (Gen 1–4)** — see [solaria-hub ROADMAP](https://github.com/edcheng1010/solaria-hub/blob/main/ROADMAP.md).

### Epic SPIKE-1: Foundation ✅ Complete
Protocol correction, COBS encoding, BLE connection reliability.

### Epic SPIKE-2: SSP v0.8 Migration ✅ Complete
Hub-side Python rewrite (SSP v0.8), Java SSP infrastructure, full component migration.
103 hardware test cases passed. 161+ Java unit tests passing.

### Epic SPIKE-3: Post-MVP Block Expansion ✅ Complete
Sound, System, and Music components added. Full SPIKE Prime standard block coverage:
58/68 leaf blocks implemented (10 hardware-impossible: 7 need 3x3 color matrix accessory, 3 need hub named audio).

### Epic SPIKE-4: Client/Bridge Split ⏳ Next
TransportProfile abstraction, bridge extraction from the extension.

### Future: Multi-Hub Support
- Extend to support LEGO Boost, EV3, SPIKE Essential (different protocols)
- Abstract BluetoothInterfaceImpl into an interface with product-specific implementations

## Reference Implementations
- LEGO Official Python: https://github.com/LEGO/spike-prime-docs/tree/main/examples/python
- Working WebBluetooth: https://github.com/etomasfe/SpikeRemoteControl
- Protocol Docs: https://lego.github.io/spike-prime-docs/
- LEGO Issue #3 (TunnelMessage): https://github.com/LEGO/spike-prime-docs/issues/3
