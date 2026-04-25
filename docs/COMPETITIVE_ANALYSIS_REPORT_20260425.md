# Competitive Analysis: LEGO SPIKE Prime + App Inventor Extension
## Date: April 25, 2026
## Prepared for: Edward, MIT Hong Kong Innovation Node

---

## Executive Summary

After deep research across YouTube demonstrations, GitHub repositories, LEGO official documentation, community forums, and working implementations, the competitive landscape reveals that **no one has yet built a polished, production-ready App Inventor extension for SPIKE Prime 3.x firmware**. However, several proof-of-concept implementations exist that demonstrate the correct protocol approach. Most critically, our current extension architecture needs a **fundamental protocol correction** to work with SPIKE Prime 3.x firmware.

---

## 1. Existing Implementations Found

### 1.1 etomasfe/SpikeRemoteControl (GitHub)
**Status:** WORKING proof-of-concept
**Platform:** HTML + JavaScript (WebBluetooth API)
**What it does:** Controls two SPIKE Prime motors via a web-based remote control interface
**Protocol:** SPIKE Prime 3.x BLE protocol with COBS encoding, program upload, and TunnelMessage

**Architecture (PROVEN WORKING):**
1. Connect via BLE to Service UUID `0000fd02-0000-1000-8000-00805f9b34fb`
2. Upload a Python "controller" program to the hub
3. Start the program on the hub
4. Send real-time commands via TunnelMessage (0x32)
5. Hub-side Python program receives commands via `hub.config['module_tunnel']` callback
6. Hub-side program controls motors/LEDs based on received commands

**Limitations:** Rough code, only motor control, no error handling, no sensor reading, WebBluetooth only (not App Inventor)

### 1.2 Korean FUNERS Group (YouTube Videos)
**Video 1:** https://www.youtube.com/watch?v=0QyTuA4AUjg
**Status:** WORKING demonstration
**Platform:** App Inventor + SPIKE Prime
**Protocol:** Uses BT_VCP (Bluetooth Virtual COM Port) from the **SPIKE Prime 2.x unofficial library** (NOT 3.x firmware)
**What it does:** Sends text messages from App Inventor to SPIKE Prime via Classic Bluetooth, SPIKE Prime reads and acts on messages

**Key Detail:** This uses the **older SPIKE Prime 2.x firmware** with MicroPython and the unofficial `BT_VCP` class. This approach does NOT work on SPIKE Prime 3.x firmware (current firmware).

**Video 2:** https://www.youtube.com/watch?v=rQm1uk4JV2E
**Status:** WORKING demonstration
**Platform:** App Inventor + SPIKE Prime
**What it does:** Similar BLE-based communication with SPIKE Prime

### 1.3 RemoteBrick (GitHub: JuniorJacki/RemoteBrick)
**Status:** Active development (Java library)
**Platform:** Java library for Android (NOT App Inventor)
**Protocol:** LEGO Wireless Protocol 3.0 (for SPIKE Essential, Technic, Boost)
**What it does:** Java library for controlling LEGO hubs from Android apps

**Key Detail:** RemoteBrick uses the **LEGO Wireless Protocol 3.0** which works with SPIKE Essential but NOT with SPIKE Prime 3.x firmware. SPIKE Prime uses a completely different protocol.

### 1.4 gpdaniels/spike-prime (GitHub)
**Status:** Older project, Android controller
**Platform:** Native Android app
**Protocol:** Classic Bluetooth SPP (Serial Port Profile) - uses UUID `00001101-0000-1000-8000-00805F9B34FB`
**What it does:** Sends JSON-RPC commands to SPIKE Prime

**Key Detail:** This was for SPIKE Prime 2.x firmware which supported JSON-RPC over Classic Bluetooth. Does NOT work on 3.x firmware.

### 1.5 Anton's Mindstorms (Blog)
**Status:** Working for MINDSTORMS Robot Inventor
**Platform:** Python (bleak library) + custom Android app
**Protocol:** LEGO Wireless Protocol 3.0
**What it does:** Remote control of MINDSTORMS hub from Android

**Key Detail:** Uses LEGO Wireless Protocol 3.0, which is for MINDSTORMS/Boost/Technic hubs, NOT SPIKE Prime 3.x.

### 1.6 LEGO Official Python Examples (GitHub: LEGO/spike-prime-docs)
**Status:** Official reference implementation
**Platform:** Python (bleak library)
**Protocol:** SPIKE Prime 3.x BLE protocol (COBS encoding)
**Files:** `app.py`, `messages.py`, `cobs.py`
**What it does:** Complete reference implementation for connecting to SPIKE Prime 3.x via BLE

---

## 2. Critical Protocol Discovery

### Two Different LEGO Protocols (NOT Interchangeable)

| Feature | LEGO Wireless Protocol 3.0 | SPIKE Prime 3.x Protocol |
|---------|---------------------------|--------------------------|
| **Used by** | SPIKE Essential, Boost, Technic, MINDSTORMS | SPIKE Prime (current firmware) |
| **Service UUID** | `00001623-1212-EFDE-1623-785FEABCD123` | `0000FD02-0000-1000-8000-00805F9B34FB` |
| **Communication** | Direct commands (port output, hub properties) | Program upload + TunnelMessage |
| **Motor control** | Direct: send port output command | Indirect: upload Python program, then send TunnelMessage |
| **Encoding** | Length-prefixed binary messages | COBS encoding with XOR(0x03) |
| **Hub-side code** | Not required | Required (Python program must be uploaded) |

### The Two-Part Architecture (Confirmed by LEGO Developer SteffenLEGO)

From GitHub issue LEGO/spike-prime-docs#3, a LEGO official developer confirmed:

> Motor/LED control on SPIKE Prime 3.x requires:
> 1. Upload a Python program to the hub that uses `hub.config['module_tunnel']`
> 2. Start the program
> 3. Send commands via TunnelMessage (0x32)
> 4. The Python program receives commands and controls hardware

This means **you cannot directly control motors/LEDs via BLE commands alone** on SPIKE Prime 3.x. You MUST upload a controller program first.

---

## 3. Impact on Our Extension

### What Our Current Code Does (NEEDS CORRECTION)
Based on the file verification of LegoSpikePrime.java (1,576 lines):
- BLE scanning with RSSI staleness-based detection (CORRECT)
- Connection management with null pointer protections (CORRECT)
- Uses correct SPIKE Prime Service UUID `0000FD02-...` (CORRECT)
- Has COBS encoding (COBSEncoder.java) (NEEDS VERIFICATION against working implementation)
- Has CRC32 (SpikeCRC32.java) (NEEDS VERIFICATION)
- Attempts to send motor/LED commands directly (INCORRECT for 3.x)

### What We Need to Change
1. **Add Program Upload capability** - Upload a Python controller program to the hub
2. **Add TunnelMessage support** - Send commands via message type 0x32
3. **Embed hub-side Python program** - Store the controller Python code as a string constant
4. **Add bidirectional communication** - Listen for TunnelMessage responses from hub
5. **Verify COBS encoding** - Compare our COBSEncoder.java against the working constants:
   - DELIMITER = 0x02
   - NO_DELIMITER = 255
   - MAX_BLOCK_SIZE = 84
   - COBS_CODE_OFFSET = 2
   - XOR = 0x03

---

## 4. Competitive Position Assessment

### Are We Still Ahead?

**YES, but with a critical caveat.** Here's why:

| Factor | Our Position | Competition |
|--------|-------------|-------------|
| **Platform** | App Inventor extension (unique) | WebBluetooth, native Android, Python scripts |
| **Target audience** | K-12 education (MIT CSAIL presentation) | Hobbyists, FLL teams |
| **Protocol correctness** | Needs TunnelMessage fix | etomasfe has working TunnelMessage |
| **Code quality** | Production-grade with error handling | Proof-of-concept quality |
| **User experience** | Block-based programming (drag & drop) | Code-based (HTML/JS/Python) |
| **Documentation** | Comprehensive ARCHITECTURE.md | Minimal or none |
| **Multi-hub support** | Designed for it | Single hub only |
| **Sensor support** | Planned | Not implemented anywhere |
| **Future extensibility** | Architected for multiple LEGO products | Single product only |

### What Makes Us Unique
1. **No one has built a working App Inventor extension for SPIKE Prime 3.x** - We would be FIRST
2. **Block-based interface** - Accessible to K-12 students who can't write JavaScript or Python
3. **MIT backing** - Credibility and distribution through MIT App Inventor community
4. **Extensible architecture** - Designed to support multiple LEGO products in the future

### What We Must Fix to Stay Ahead
1. **Protocol correction** - Implement the program upload + TunnelMessage architecture
2. **COBS verification** - Ensure our encoding matches the working implementation
3. **Hub-side program** - Create a comprehensive Python controller program (not just motor control)

---

## 5. Recommended Next Steps (Priority Order)

### Phase 1: Protocol Correction (CRITICAL)
1. Verify COBSEncoder.java constants match: DELIMITER=0x02, NO_DELIMITER=255, MAX_BLOCK_SIZE=84, COBS_CODE_OFFSET=2, XOR=0x03
2. Implement file upload protocol (ClearSlot → StartFileUpload → TransferChunk)
3. Implement program start (ProgramFlowRequest 0x1E)
4. Implement TunnelMessage send (0x32) and receive

### Phase 2: Hub-Side Python Program
1. Create a comprehensive Python controller program that handles:
   - Motor control (all 6 ports, forward/reverse, power levels)
   - LED matrix control (set pixels, patterns)
   - Color sensor reading (send sensor data back via tunnel)
   - Distance sensor reading
   - Force sensor reading
   - Hub status (battery, orientation)
2. Embed this program as a string constant in the extension

### Phase 3: App Inventor Block Design
1. Design intuitive blocks for:
   - ConnectToHub / DisconnectFromHub
   - SetMotorPower(port, power)
   - SetLEDColor(r, g, b)
   - ReadColorSensor(port) → event callback
   - ReadDistanceSensor(port) → event callback
2. Each block sends a TunnelMessage command to the hub-side program

### Phase 4: Testing and Validation
1. Test with physical SPIKE Prime hub on SPIKE Prime 3.x firmware
2. Verify COBS encoding/decoding matches
3. Test program upload reliability
4. Test TunnelMessage latency and reliability
5. Test reconnection after disconnect

---

## 6. Key Technical References

| Resource | URL | Purpose |
|----------|-----|---------|
| LEGO SPIKE Prime BLE Protocol Docs | https://lego.github.io/spike-prime-docs/ | Official protocol specification |
| LEGO Official Python Examples | https://github.com/LEGO/spike-prime-docs/tree/main/examples/python | Reference implementation (app.py, messages.py, cobs.py) |
| etomasfe SpikeRemoteControl | https://github.com/etomasfe/SpikeRemoteControl | Working WebBluetooth implementation |
| SPIKE Prime Python API (Tufts CEEO) | https://tuftsceeo.github.io/SPIKEPythonDocs/SPIKE3.html | Hub-side Python API reference |
| LEGO GitHub Issue #3 | https://github.com/LEGO/spike-prime-docs/issues/3 | SteffenLEGO confirms TunnelMessage approach |
| COBS Encoding Constants | etomasfe ControlSpike.html lines 368-372 | Verified working COBS constants |

---

## 7. Conclusion

We are still ahead of the competition because **no one has built a working App Inventor extension for SPIKE Prime 3.x**. However, the etomasfe implementation proves the correct protocol approach, and we must adopt the same two-part architecture (program upload + TunnelMessage) to make our extension work. The Korean FUNERS group's approach only works on older 2.x firmware and is not forward-compatible.

Our key competitive advantage is the **App Inventor platform** - making SPIKE Prime accessible to K-12 students through block-based programming. Combined with MIT CSAIL backing, this positions us uniquely in the educational robotics space.

**The critical path is: Fix the protocol → Test with physical device → Present to MIT CSAIL.**
