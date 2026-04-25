# CRITICAL FINDINGS: SPIKE Prime 3.x BLE Protocol
## Date: 2026-04-25

## GAME-CHANGING DISCOVERY

LEGO has published an **official Python reference implementation** of the SPIKE Prime BLE protocol at:
https://github.com/LEGO/spike-prime-docs/tree/main/examples/python

This is the AUTHORITATIVE source for how to communicate with SPIKE Prime hubs over BLE.

## KEY PROTOCOL DETAILS

### 1. Connection UUIDs (CONFIRMED)
- **Service UUID:** `0000fd02-0000-1000-8000-00805f9b34fb`
- **RX Characteristic (write to hub):** `0000fd02-0001-1000-8000-00805f9b34fb`
- **TX Characteristic (read from hub):** `0000fd02-0002-1000-8000-00805f9b34fb`

### 2. Message Framing: COBS (Consistent Overhead Byte Stuffing)
- NOT raw binary, NOT JSON-RPC
- All messages are COBS-encoded before transmission
- Delimiter: `0x02`
- XOR mask: `0x03` (applied to prevent ctrl+C interference)
- Max block size: 84 bytes (including code word)
- Messages are packed: COBS encode → XOR with 0x03 → append delimiter 0x02
- Messages are unpacked: strip delimiter → XOR with 0x03 → COBS decode

### 3. Message Types (Binary Protocol)
| ID | Message | Direction | Purpose |
|----|---------|-----------|---------|
| 0x00 | InfoRequest | → Hub | Get hub info (firmware, max sizes) |
| 0x01 | InfoResponse | ← Hub | Hub info response |
| 0x0C | StartFileUploadRequest | → Hub | Begin program upload |
| 0x0D | StartFileUploadResponse | ← Hub | Upload acknowledgment |
| 0x10 | TransferChunkRequest | → Hub | Send program chunk |
| 0x11 | TransferChunkResponse | ← Hub | Chunk acknowledgment |
| 0x1E | ProgramFlowRequest | → Hub | Start/stop program |
| 0x1F | ProgramFlowResponse | ← Hub | Program flow ack |
| 0x20 | ProgramFlowNotification | ← Hub | Program state change |
| 0x21 | ConsoleNotification | ← Hub | Console text output |
| 0x28 | DeviceNotificationRequest | → Hub | Subscribe to sensor data |
| 0x29 | DeviceNotificationResponse | ← Hub | Subscription ack |
| 0x32 | TunnelMessage | ↔ Both | Bidirectional data tunnel |
| 0x3C | DeviceNotification | ← Hub | Sensor/motor data updates |
| 0x46 | ClearSlotRequest | → Hub | Clear program slot |
| 0x47 | ClearSlotResponse | ← Hub | Clear slot ack |

### 4. Device Notification Sub-Messages (0x3C payload)
| Sub-ID | Device | Data Fields |
|--------|--------|-------------|
| 0x00 | Battery | Level (0-100%) |
| 0x01 | IMU | Face up, yaw face, yaw/pitch/roll, accel XYZ, gyro XYZ |
| 0x02 | 5x5 Matrix | 25 pixel values |
| 0x0A | Motor | Port, abs position, power, speed, position |
| 0x0B | Force Sensor | Port, value (0-100), pressed flag |
| 0x0C | Color Sensor | Port, color enum, raw RGB (0-1023) |
| 0x0D | Distance Sensor | Port, distance mm (40-2000, -1 if none) |
| 0x0E | 3x3 Color Matrix | Port, 9 pixel values |

### 5. Program Upload Flow
1. Send InfoRequest (0x00) → get max_chunk_size, max_packet_size
2. Send ClearSlotRequest (0x46) → clear target slot
3. Send StartFileUploadRequest (0x0C) → with filename, slot, CRC32
4. Send TransferChunkRequest (0x10) → in chunks of max_chunk_size, with running CRC
5. Send ProgramFlowRequest (0x1E) → start the program

### 6. CRC32 Algorithm
Standard CRC32 with polynomial 0xEDB88320 (reflected)

## IMPACT ON OUR EXTENSION

### What Our Current Code Does WRONG:
1. **Our COBSEncoder.java** - Need to verify it matches the OFFICIAL COBS implementation (delimiter=0x02, XOR=0x03, max_block=84)
2. **Our message format** - Need to verify we're using the correct binary message format, not JSON-RPC
3. **Our motor control** - The official docs show motor data comes VIA DeviceNotification (0x3C), but sending motor COMMANDS likely goes through TunnelMessage (0x32) or by uploading/running Python programs

### What This Means for Motor/LED Control:
The official reference implementation shows how to:
- Upload and run Python programs on the hub
- Read sensor/motor data via DeviceNotification
- But it does NOT show direct motor/LED control commands

**This suggests the intended approach is:**
1. Upload a Python program to the hub that listens for commands via console/tunnel
2. Send commands from the app via TunnelMessage (0x32)
3. The Python program on the hub interprets commands and controls motors/LEDs

### Competitive Advantage:
- The Korean FUNERS group (YouTube video) uses the OLD SPIKE 2.x JSON-RPC protocol
- RemoteBrick uses the old LEGO Wireless Protocol 3.0 (different UUIDs entirely)
- **We can be the FIRST App Inventor extension that implements the SPIKE 3.x binary protocol correctly**
- We have the OFFICIAL reference implementation to work from

## FILES TO REFERENCE
- `/home/ubuntu/research/spike_prime_app.py` - Official connection and upload example
- `/home/ubuntu/research/spike_prime_messages.py` - Official message serialization
- `/home/ubuntu/research/spike_prime_cobs.py` - Official COBS implementation
- `/home/ubuntu/research/spike_prime_crc.py` - Official CRC32 implementation
