# SPIKE Prime App Inventor Extension: Project Brain

**Date:** April 25, 2026
**Purpose:** This document serves as the central knowledge base and persistent memory for the SPIKE Prime App Inventor Extension project. It contains the distilled essence of 18 research and development sessions. **Claude Code and future Manus agents MUST read this document before making any architectural or protocol-level changes.**

---

## 1. The Critical Protocol Discovery (SPIKE 3.x)

The most important finding of this project is that **SPIKE Prime 3.x firmware DOES NOT support direct motor/LED control via simple BLE commands.** 

The older SPIKE 2.x firmware supported JSON-RPC or `BT_VCP` (Classic Bluetooth SPP), but 3.x uses a completely different binary protocol.

### The Required Two-Part Architecture
To control hardware on a SPIKE Prime 3.x hub, the extension MUST implement a two-part architecture:

1. **Hub-Side Controller (Python):** The extension must upload a MicroPython program to the hub. This program uses `hub.config['module_tunnel']` to listen for incoming messages, parse them, and execute hardware commands (e.g., `motor.run()`).
2. **App-Side Communicator (Java):** The App Inventor extension connects via BLE, uploads the Python program, starts it, and then sends real-time commands using the `TunnelMessage` (0x32) protocol.

*Source: Confirmed by LEGO official developer SteffenLEGO in GitHub issue #3.*

---

## 2. Official SPIKE Prime BLE Protocol Details

### Connection UUIDs
- **Service UUID:** `0000fd02-0000-1000-8000-00805f9b34fb`
- **RX Characteristic (App writes to Hub):** `0000fd02-0001-1000-8000-00805f9b34fb`
- **TX Characteristic (App reads from Hub):** `0000fd02-0002-1000-8000-00805f9b34fb`

*(Note: Do NOT confuse this with the LEGO Wireless Protocol 3.0 `00001623-...` which is for SPIKE Essential/Boost and does not work for SPIKE Prime).*

### Message Framing: COBS Encoding
All messages sent to/from the hub are encoded using Consistent Overhead Byte Stuffing (COBS) with specific constants:
- **Delimiter:** `0x02`
- **XOR Mask:** `0x03` (applied to all bytes to prevent ctrl+C interference)
- **Max Block Size:** `84` bytes

**Pack Flow:** Raw Data → COBS Encode → XOR each byte with 0x03 → Append Delimiter (0x02)
**Unpack Flow:** Strip Delimiter → XOR each byte with 0x03 → COBS Decode

### Key Binary Message Types
| ID | Name | Purpose |
|----|------|---------|
| `0x00` | InfoRequest | Handshake to get max chunk sizes |
| `0x46` | ClearSlotRequest | Clear a program slot before upload |
| `0x0C` | StartFileUploadRequest | Begin Python program upload |
| `0x10` | TransferChunkRequest | Send program data chunks |
| `0x1E` | ProgramFlowRequest | Start the uploaded program |
| `0x32` | TunnelMessage | **Bidirectional real-time communication** |

---

## 3. Current Codebase State & Architecture Split

The repository currently contains two distinct package structures. **Claude Code must understand this split to avoid breaking the working connection logic.**

### A. The Active Architecture (`io.github.appinventor.legospikeprime`)
- **Files:** `LegoSpikePrime.java`, `BluetoothInterfaceImpl.java`
- **Status:** WORKING connection and scanning logic.
- **Key Features:** Contains critical fixes for RSSI staleness (hybrid check: `rssiStaleCount >= 3` AND `timestampStale`), null pointer protections during `HubListChanged`, and correct UUID filtering.
- **Deficiency:** Currently attempts to send direct motor commands (which fails on 3.x).

### B. The Planned Protocol Architecture (`io.github.appinventor.legospike`)
- **Files:** 13 helper classes including `SpikeProtocol.java`, `MessageHandler.java`, `COBSEncoder.java`, `SpikeCRC32.java`.
- **Status:** WRITTEN but **NOT INTEGRATED** into the active architecture.
- **Purpose:** These classes contain the complex logic for COBS encoding, CRC32 calculation, and message formatting required for the 3.x protocol.

---

## 4. Development Roadmap for Claude Code

When Claude Code takes over development, it should follow this exact sequence:

### Phase 1: Protocol Integration
1. **Verify COBS Constants:** Ensure `COBSEncoder.java` uses the exact constants listed above (Delimiter 0x02, XOR 0x03).
2. **Integrate Packages:** Merge the protocol handling from the `legospike` package into the active `legospikeprime` package without breaking the working BLE connection/scanning logic.

### Phase 2: The Upload Flow
Implement the sequence to upload the controller program:
1. Connect to Hub
2. Send `InfoRequest` (0x00)
3. Send `ClearSlotRequest` (0x46) for slot 0
4. Send `StartFileUploadRequest` (0x0C) with CRC32
5. Send `TransferChunkRequest` (0x10) until complete
6. Send `ProgramFlowRequest` (0x1E) to start it

### Phase 3: The Controller Program & Tunnel
1. Write the Python controller program (using `hub.config['module_tunnel']`).
2. Embed the Python code as a string constant in the Java extension.
3. Implement the `TunnelMessage` (0x32) sender in Java to pass commands (e.g., "M:A:50" for Motor A at 50% power) to the running Python program.

---

## 5. Competitive Context
- **Why we are building this:** There is currently NO working App Inventor extension for SPIKE Prime 3.x.
- **The Korean FUNERS Video:** Uses obsolete `BT_VCP` on 2.x firmware. Do not attempt to replicate this.
- **RemoteBrick:** Uses LEGO Wireless Protocol 3.0. Do not copy their UUIDs or message formats.
- **etomasfe/SpikeRemoteControl:** A working HTML/JS implementation that proves the TunnelMessage architecture works. Use this as the primary reference for protocol behavior.

---
*End of Project Brain. For a full list of source URLs, see `DOCUMENTATION_INDEX.md`.*
