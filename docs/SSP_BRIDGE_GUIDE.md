> *Unofficial — independent open-source project, not affiliated with the LEGO Group or the Massachusetts Institute of Technology. See [NOTICE](../NOTICE) for trademark and licensing details.*

# SSP v0.6 Bridge Guide — LEGO SPIKE Prime

This document maps every App Inventor block to its [SSP v0.6](https://github.com/edcheng1010/solaria-hub/blob/main/spec/SSP-v0.6.md) command and describes the transport profile used by this bridge.

---

## Transport Profile

This bridge implements the `spike-prime-3.x` transport profile (SSP v0.6 §2.1):

| Field | Value |
|---|---|
| `profile_id` | `spike-prime-3.x` |
| `transport` | `ble` |
| `discovery.service_uuid` | `0000fd02-0000-1000-8000-00805f9b34fb` |
| `framing` | `cobs-xor` — COBS encode, then XOR each byte with `0x03`, append delimiter `0x02` |
| `wrapper` | TunnelMessage opcode `0x32` — `[0x32][size_low][size_high][payload]` |
| `ssp_payload_encoding` | `json-utf8-newline` |

All SSP JSON messages are UTF-8 strings terminated by `\n`, wrapped in TunnelMessage, COBS-encoded.

---

## Connection Lifecycle

```
App Inventor                          SPIKE Prime Hub
      |                                     |
      |-- BLE connect ------------------>   |
      |-- InfoRequest (0x00) ----------->   |
      |<- InfoResponse (0x01) ----------    |
      |                                     |
      | [if first connection / new version] |
      |-- ClearSlot (0x46) ------------->   |
      |-- StartFileUpload (0x0C) ------->   |
      |-- TransferChunk (0x10) x N ----->   |
      |-- ProgramFlow start (0x1E) ----->   |
      |                                     |
      | [on reconnect: skip upload]         |
      |-- ProgramFlow start (0x1E) ----->   |
      |                                     |
      |<- {"type":"capability",...} ------  |  ← OnCapabilityReceived fires
      |                                     |
      |-- {"cmd":"motor.run",...}\n ----->  |
      |<- {"event":"sensor",...}\n -------  |
      |-- {"cmd":"system.ping"}\n ------->  |  ← every 5 s (heartbeat)
      |<- {"event":"pong"}\n -------------  |
```

---

## Capability Declaration

On startup, the hub sends a capability declaration (received via `OnCapabilityReceived`):

```json
{
  "type": "capability",
  "device": "spike-prime",
  "firmware": "3.x",
  "ssp_version": "0.6",
  "encodings": ["json-utf8-newline"],
  "supports_batch": false,
  "system_metrics": ["battery","charging","temperature",
                     "button.left","button.right","button.center"],
  "ports": [
    {"id":"A","type":"motor","features":["speed","position","stall"],
     "constraints":{"speed":{"type":"int","min":-100,"max":100},
                    "position":{"type":"int","min":0,"max":359,"wraps":true}}},
    {"id":"display","type":"display","width":5,"height":5,"depth":"grayscale",
     "features":["pixel","image","text","brightness","orientation"]},
    {"id":"status","type":"led","features":["set"],
     "constraints":{"color":{"type":"enum","values":["red","orange","yellow",
                    "green","cyan","blue","violet","magenta","white","off"]}}},
    {"id":"imu","type":"orientation","features":["pitch","roll","yaw","gesture"],
     "constraints":{"gesture":{"type":"enum","values":["shake","tap","double_tap",
                               "fall","face_up","face_down"]}}},
    {"id":"speaker","type":"speaker","features":["beep"]}
  ]
}
```

Motor ports A–F are enumerated dynamically based on what's physically connected at startup.

---

## Block → SSP Command Mapping

### LegoSpikeConnectivity

| Block | SSP command / event |
|---|---|
| `StartScanning` | BLE scan for service UUID `0000fd02-…` |
| `ConnectToHub(name)` | BLE connect + upload `hub_controller.py` if needed |
| `DisconnectFromHub` | BLE disconnect |
| `OnCapabilityReceived(deviceType, sspVersion)` | `{"type":"capability",...}` received |
| `OnError(code, message, requestId)` | `{"event":"error",...}` received |
| `OnHeartbeatLost` | No `{"event":"pong"}` within 10 s of last ping |
| `GetDeviceType()` | Returns `capability.device` |
| `GetAvailablePorts()` | Returns comma-joined `capability.ports[].id` |
| `GetSupportedEncodings()` | Returns comma-joined `capability.encodings` |
| `GetSSPVersion()` | Returns `capability.ssp_version` |

### LegoSpikeMotors

| Block | SSP command sent |
|---|---|
| `StartMotor()` | `{"cmd":"motor.run","port":"A","speed":75}` |
| `StopMotor()` | `{"cmd":"motor.stop","port":"A"}` |
| `SetMotorSpeed(n)` | Stored locally; applied on next `StartMotor()` |

Speed range: –100 to +100 (negative = counterclockwise). Mapped to hub velocity by ×11 in Python.

### LegoSpikeMovement

| Block | SSP command sent |
|---|---|
| `StartMoving()` | `{"cmd":"movement.drive","left":"A","right":"B","speed":50,"steering":0}` |
| `StartMovingWithSteering(s)` | `{"cmd":"movement.drive",...,"steering":s}` |
| `StopMoving()` | `{"cmd":"movement.stop","left":"A","right":"B"}` |
| `SetMovementSpeed(n)` | Stored locally |

Throttled to 20 Hz (50 ms minimum between sends).

### LegoSpikeLight

| Block | SSP command sent |
|---|---|
| `TurnOnLightMatrix()` | `{"cmd":"led.matrix.image","port":"display","image":"HAPPY"}` |
| `TurnOffLightMatrix()` | `{"cmd":"led.matrix.clear","port":"display"}` |
| `WriteOnLightMatrix(text)` | `{"cmd":"led.matrix.text","port":"display","text":"Hi"}` |
| `SetPixelBrightness(x,y,b)` | `{"cmd":"led.matrix.pixel","port":"display","x":2,"y":2,"brightness":80}` |

`x` and `y` are 1-based in the block (1–5), converted to 0-based (0–4) before sending.

### LegoSpikeSensors

Sensors use one-shot `sensor.read` — call the getter block, response fires as an event.

| Block | SSP command sent | Event fired |
|---|---|---|
| `GetColor()` | `{"cmd":"sensor.read","port":"C","type":"color"}` | `ColorRead(port, color)` |
| `GetDistance()` | `{"cmd":"sensor.read","port":"D","type":"distance"}` | `DistanceRead(port, mm)` |
| `GetPressure()` | `{"cmd":"sensor.read","port":"E","type":"force"}` | `PressureRead(port, value)` |
| `IsPressed()` | `{"cmd":"sensor.read","port":"E","type":"touched"}` | `PressureChecked(port, bool)` |
| `GetTiltAngle()` | `{"cmd":"sensor.read","port":"imu","type":"pitch"}` | `TiltAngleRead(axis, degrees)` |
| `GetTimer()` | Client-side elapsed time (no hub round-trip) | `TimerRead(seconds)` |
| `ResetTimer()` | Client-side reset (no hub round-trip) | — |

Sensor event JSON format received from hub:
```json
{"event":"sensor","port":"C","type":"color","value":"red"}
{"event":"sensor","port":"D","type":"distance","value":235}
{"event":"sensor","port":"imu","type":"pitch","value":12}
```

---

## Error Codes (SSP v0.6 §7)

| Code range | Category | Example |
|---|---|---|
| 100–199 | Connection errors | Unknown port |
| 200–299 | Command errors | Out-of-range speed value |
| 300–399 | Hardware errors | Motor stall |
| 400–499 | Protocol errors | Malformed JSON |

Fires `OnError(code, message, requestId)` on `LegoSpikeConnectivity`. Rate-limited to one event per (port, parameter) per second to avoid flooding in tight loops.

---

## Heartbeat

The extension sends `{"cmd":"system.ping"}` every 5 seconds after capability is received. The hub responds with `{"event":"pong"}`. If no pong arrives within 10 seconds, `OnHeartbeatLost` fires once and the heartbeat stops. To test: press the center button on the hub to kill the Python program — `OnHeartbeatLost` fires ~10 s later.

---

## Notes for Future Bridge Implementations

If you are adding support for a new hardware platform following the Solaria Type 2 pattern:

1. Define a transport profile (SSP v0.6 §2.1) for the new hardware's BLE/Serial framing.
2. Write a hub-side program (or firmware) that emits a capability declaration on startup and handles SSP JSON commands.
3. Create a `SolariaXxx.aix` App Inventor extension that wraps the SSP JSON commands with the new transport profile.
4. Reference the [solaria-hub ARCHITECTURE.md](https://github.com/edcheng1010/solaria-hub/blob/main/ARCHITECTURE.md) for the full Type 1 / Type 2 hybrid model.
