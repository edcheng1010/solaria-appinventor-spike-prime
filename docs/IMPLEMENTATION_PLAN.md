# Implementation Plan — LEGO SPIKE Prime App Inventor Extension

> **Unofficial integration.** Independent open-source project, not affiliated with the LEGO Group or the Massachusetts Institute of Technology. See [NOTICE](../NOTICE) for trademark and licensing details.

**Last revised:** 2026-05-24
**Targeting:** SSP v0.6
**Status:** Phase 1 complete · Phase 2 next
**Author:** Edward Cheng

---

## Overview

This document is the working roadmap for the extension. It consolidates:
- The original pre-MVP plan (now Phase 1, complete)
- The post-MVP block expansion (now Phase 3, from `mvp_status_and_postmvp.md`)
- The SSP migration work (PR 1 = Phase 2; PR 2 = Phase 4)
- Multi-hub support (Phase 5)

Five phases, executed in order. Nothing from the prior plans has been dropped — see the "Inheritance from prior plans" section at the bottom for traceability.

| Phase | Scope | Status |
|---|---|---|
| 1 | Foundation — BLE, COBS, TunnelMessage, custom binary protocol, 5 working components | ✅ Complete |
| 2 | SSP v0.6 migration — same blocks, SSP wire format, capability queries, docs | ✅ Complete |
| 3 | Post-MVP block expansion — Sound, System, Music, IMU, full motor/movement/light/sensor blocks | ⏳ Next |
| 4 | Client/bridge architectural split — `TransportProfile`, lib extraction to `solaria-lib-spike-prime`, download-on-connect | After Phase 3 |
| 5 | Classroom multi-hub — N simultaneous SPIKE Prime hubs on one App Inventor app | Long-term |

---

## Decisions made (2026-05-24, still in force)

1. **Validation strategy:** Client-side validation fires `OnError` event and refuses to send. Out-of-range parameters surface immediately rather than silently clamping or waiting for a bridge round-trip.
2. **JSON parse performance:** Commit to JSON for Phase 2. Perf benchmark gate before merging — sustained 20 Hz movement updates for 60 s with no payload drops. If perf fails, fall back to v0.6 §3.2 binary encoding (now fully spec'd); mitigate temporarily by throttling movement updates to 10 Hz.
3. **Bridge program embedding:** End state is download-on-connect (versioned bridge releases, decoupled from the .aix). Implement during Phase 4. Phase 2 keeps the bridge program embedded as a Java string.
4. **Phase ordering:** Phase 2 before Phase 3. Doing post-MVP block expansion *after* SSP migration avoids duplicating new commands in two wire formats.

---

## SSP version targeting

This plan targets **SSP v0.6** as the spec stands today. v0.6 absorbed every blocker we'd surfaced from the SPIKE Prime bridge perspective:

| Wishlist | Status | Notes |
|---|---|---|
| v0.2 | ✅ Integrated | Transport profiles, binary encoding reserved, movement category, capability schema, request_id, heartbeat, sensor flow-control |
| v0.3 | ✅ Integrated | led.matrix, display port, orientation port, sound.play payloads, speaker port, system.subscribe |
| v0.4 | ✅ Integrated | Parameter constraints (`int`/`float`/`enum`/`bool`), gesture event consistency |
| v0.5 | ✅ Integrated | Button format formalised, `array` + `string` constraints, gesture constraint enum, implicit-coordinate-constraints from port dimensions |
| v0.6 | ✅ Integrated | RFCOMM transport, binary encoding finalised, batch commands, motor duration / stop_action, sound.set_volume, led.matrix.brightness / .orientation |
| v0.7+ | 📝 Future | DFU, stream multiplexing, auth — not needed for SPIKE Prime bridge |

**No outstanding wishlist dependencies for Phases 2–5.** The bridge can ship fully v0.6-compliant with zero `x_` extensions.

Potential v0.7 candidates surfaced while planning Phase 3 (not yet filed):
- `display` port on sensors (e.g., the SPIKE distance sensor's 4-LED indicator — `LightUpDistanceSensor` block)
- `SetMotorAcceleration` as canonical motor feature
- `ResetYaw` as orientation port command (currently no analogue in spec)
- 3×3 color sensor matrix accessory (would need `display` port type `rgb` with `width:3, height:3`)
- Music/MIDI semantics (notes string parsing, drum-kit specification)

---

## Phase 1 — Foundation *(COMPLETE ✓)*

Originally the pre-MVP "Protocol Correction" plan in CLAUDE.md.

**Shipped:**
- BLE scan with RSSI staleness ghost-filtering (CLAUDE.md Rule 2)
- COBS encoding with verified constants — delimiter `0x02`, XOR `0x03`, MAX_BLOCK_SIZE 84
- CRC32 with running-CRC support and 4-byte alignment
- Message framing: COBS → XOR → delimiter
- File upload protocol: `ClearSlot` → `StartFileUpload` → `TransferChunk` → `ProgramFlow`
- TunnelMessage send/receive (opcode `0x32`)
- Hub-side Python controller program embedded in Java as string constant
- Auto-upload of controller program on connection
- 5 working App Inventor components: Connectivity, Motors, Movement, Light, Sensors
- Custom `MTR` / `MOV` / `LGT` / `SEN` binary command protocol over TunnelMessage
- BLE connection tested on physical SPIKE Prime 3.x hubs in classroom conditions
- Reconnect-after-disconnect tested (RSSI staleness solves ghost-device cache)

**Known limitations carrying into Phase 2:**
- Center button LED is owned by firmware during BLE/TunnelMessage mode — cannot be re-coloured live during a running tunnel; block removed
- `hub.motion_sensor.tilt_angles()` may return 0 on some firmware revisions (IMU fallback)
- `BluetoothLE.BytesReceived → LegoSpikeConnectivity.OnBytesReceivedFromHub` must be wired manually (`BluetoothLE` extension's auto-wiring fails on the version we depend on)
- `color.CYAN` / `color.VIOLET` not defined in firmware's color module; integer fallback works
- BLE perf: maxChunkSize 960 bytes, maxPacketSize 20 bytes (RequestMTU removed — caused TX subscription invalidation)

---

## Phase 2 — SSP v0.6 migration (PR 1)

**Goal:** swap the wire protocol from custom binary `MTR/MOV/LGT/SEN` to SSP v0.6. The App Inventor block API surface stays identical from the user's perspective. Internal protocol becomes hardware-agnostic.

**Effort:** ~2 weeks one developer. Dominated by the hub-side Python rewrite and end-to-end physical-hub testing.

**Acceptance criteria:**
- Every existing block produces equivalent behaviour on a physical hub
- Bridge emits SSP v0.6 capability declaration on connect, with constraints per §5.2
- Sustained 20 Hz movement updates work (decision #2 — or perf-gate fallback to binary encoding)
- New `OnError` event fires for out-of-range parameters before any bytes go on wire
- Heartbeat (5 s ping, 10 s disconnect on missed pongs) works end-to-end
- No regression in connection stability vs current MVP
- All connection/upload/reconnection tests from the original CLAUDE.md Phase 3 still pass

### 2.1 Hub-side Python rewrite

- **2.1.1** Replace `MTR/MOV/LGT/SEN` binary parser with `json.loads()` on newline-delimited frames
- **2.1.2** Implement command dispatcher for SPIKE-relevant SSP commands:
  - `motor.run`, `motor.stop`, `motor.goto`, `motor.reset`
  - `movement.configure`, `movement.drive`, `movement.turn`, `movement.stop`
  - `led.set` (status LED), `led.off`
  - `led.matrix.pixel`, `led.matrix.image`, `led.matrix.text`, `led.matrix.clear`
  - `sound.beep`, `sound.stop`
  - `sensor.subscribe`, `sensor.unsubscribe`, `sensor.read`
  - `system.ping` (→ `pong`), `system.info`, `system.subscribe`, `system.read`, `system.unsubscribe`
- **2.1.3** Capability declaration builder — emit on tunnel-ready:
  - Enumerate motor ports (A–F) by inspecting `hub.port.<id>` for `Motor` instances
  - Enumerate sensor ports (color, distance, force) by device type
  - Always declare virtual ports: `display`, `status`, `imu`, `speaker`
  - Always declare `system_metrics` array: battery, charging, temperature, button.left/right/center, connection_rssi
  - Set `ssp_version: "0.6"`, `encodings: ["json-utf8-newline"]`
  - Include canonical constraints per v0.6 §5.2 (full table in 2.1.7 below)
  - Light matrix declares `features: ["pixel","image","text"]` initially (brightness/orientation added in Phase 3)
  - Speaker declares `features: ["beep"]` initially (builtin/midi/volume added in Phase 3 after FW capability verification)
- **2.1.4** `SubscriptionManager` class — single async loop, emits `{"event":"sensor",...}` per registered subscription, honors `mode`/`interval`/`min_change` per v0.6 §6.5
- **2.1.5** Heartbeat handler — respond to `system.ping` with `pong` event; auto-disconnect after 10 s without ping (only when subscriptions are active)
- **2.1.6** Gesture event emit in v0.6 sensor-form: `{"event":"sensor","port":"imu","type":"gesture","value":"shake"}`
- **2.1.7** Structured error events per v0.6 §7 with `request_id` echo

**Phase-2 constraints to declare** (matches v0.6 §5 SPIKE example):

| Port | Feature | Constraint |
|---|---|---|
| A–F motor | `speed` | `{"type":"int","min":-100,"max":100}` |
| A–F motor | `position` | `{"type":"int","min":0,"max":359,"wraps":true}` |
| `status` led | `color` | `{"type":"enum","values":["red","orange","yellow","green","cyan","blue","violet","magenta","white","off"]}` — matches `hub.light.color()` palette |
| `imu` orientation | `pitch`/`roll` | `{"type":"int","min":-180,"max":180}` |
| `imu` orientation | `yaw` | `{"type":"int","min":0,"max":360,"wraps":true}` |
| `imu` orientation | `gesture` | `{"type":"enum","values":["shake","tap","double_tap","fall","face_up","face_down"]}` |
| sensor `subscribe.interval` | minimum | `{"type":"int","min":50}` |
| `display.brightness` (per-pixel) | range | implicit via `depth:"grayscale"` → `[0,100]` |
| `display.x`, `display.y` | range | implicit via `width:5`/`height:5` → `[0,4]` |

### 2.2 Java side new infrastructure

- **2.2.1** `SSPMessage.java` — `JSONObject`-based command builder with fluent API (`withRequestId(id)`, `withPort(port)`, `withParam(key, value)`); serialises to `bytes + \n`
- **2.2.2** `SSPParser.java` — parses incoming newline-delimited JSON frames; dispatches to typed listeners (`onCapability`, `onSensor`, `onSystem`, `onError`, `onPong`)
- **2.2.3** `SSPClient.java` — *(deferred to Phase 4)* implemented inline in `LegoSpikeConnectivity.sendSSP()` for Phase 2; full class extraction in Phase 4
- **2.2.4** `SpikeTransportProfile.java` — *(deferred to Phase 4)* profile constant exists in `LegoSpikeConnectivity`; class extraction in Phase 4
- **2.2.5** `CapabilityStore.java` — caches parsed v0.6 capability data; queryable (`getDeviceType()`, `getPortType(portId)`, `getConstraint(portId, feature, attribute)`, `hasFeature(portId, feature)`, `getSystemMetrics()`, `supportsBatch()`)
- **2.2.6** `Validator.java` — consults `CapabilityStore` to validate `SSPMessage` before send; throws `ValidationException` for out-of-range / unsupported-feature / unknown-port; called by `SSPClient.send()`. Rate-limits `OnError` events to one per (port, parameter) per second to avoid classroom noise.
- **2.2.7** `HeartbeatManager.java` — *(deferred to Phase 4)* implemented as inline `Timer` in `LegoSpikeConnectivity`; fires `OnHeartbeatLost` correctly
- **2.2.8** Client-side `SubscriptionManager.java` — *(deferred to Phase 4)* sensors use one-shot `sensor.read` in Phase 2; subscription push model with caching in Phase 3/4

### 2.3 Component migration

One commit per component, in this order (Connectivity must land first — others depend on `CapabilityStore`):

- **2.3.1** `LegoSpikeConnectivity`:
  - Existing `OnBytesReceivedFromHub` rewired through `SSPParser`
  - New events: `OnCapabilityReceived`, `OnError(code, message, requestId)`, `OnHeartbeatLost`
  - New blocks: `GetDeviceType()`, `GetAvailablePorts()`, `GetSupportedEncodings()`, `GetSSPVersion()`
- **2.3.2** `LegoSpikeMotors` — all methods build `SSPMessage` via `SSPClient` instead of binary payloads
- **2.3.3** `LegoSpikeMovement` — same; 1-to-1 with SSP `movement.*`
- **2.3.4** `LegoSpikeLight`:
  - Status LED methods → `led.set` / `led.off`
  - Light matrix methods → `led.matrix.pixel` / `led.matrix.image` / `led.matrix.text` / `led.matrix.clear`
- **2.3.5** `LegoSpikeSensors` — switch from on-demand `read` getters to background subscription:
  - Getter blocks (`GetColor()`, `GetDistance()`, `GetPressure()`, `IsPressed()`, `GetTiltAngle()`) auto-subscribe on first call (mode `interval`, default 100 ms)
  - Return last cached value
  - Explicit `Refresh()` block forces a one-shot `sensor.read`
  - `GetTimer()` / `ResetTimer()` stay client-side (no SSP equivalent needed)
  - All existing `ColorRead` / `DistanceRead` / `PressureRead` / etc. events fire from the subscription stream

### 2.4 Cleanup

- **2.4.1** ~~Delete `MessageBuilder.java`~~ — **NOT deleted**: `MessageBuilder` is still required by the upload protocol (`buildInfoRequest`, `buildStartFileUploadRequest`, `buildTransferChunkRequest`, `buildProgramFlowRequest`, `buildTunnelMessage`). Only the custom string commands (MTR/MOV/LGT/SEN) are obsolete, but these were never separate methods — they were just argument strings. No class deletion needed.
- **2.4.2** ~~Delete custom `MTR`/`MOV`/`LGT`/`SEN` command constants~~ — **N/A**: these were plain strings passed to `sendCommand()`, never constants. Now that components call `sendSSP()` instead, the old string format is simply unused.
- **2.4.3** Update embedded Python program string with the SSP v0.6 dispatcher
- **2.4.4** Update `ARCHITECTURE.md` — Rule 5 ("never send direct motor/LED commands") becomes obsolete; replace with SSP wire-format rules
- **2.4.5** Update `CLAUDE.md` Critical Architectural Rules to reference SSP v0.6
- **2.4.6** Add `docs/SSP_BRIDGE_GUIDE.md` documenting how this bridge maps to SSP v0.6

### 2.5 Testing

Phase 2 must re-run every test from the original CLAUDE.md Phase 3 plus new SSP-specific tests.

- **2.5.1** Unit tests for new Java classes: `SSPMessage`, `SSPParser`, `SSPClient`, `Validator`, `CapabilityStore`, `SubscriptionManager`, `HeartbeatManager`
- **2.5.2** Python-side unit tests on desktop MicroPython runtime:
  - Command dispatcher coverage
  - Subscription manager with all three modes (`interval`/`on_change`/`hybrid`)
  - Capability declaration shape correctness
  - Error event format with/without `request_id`
- **2.5.3** End-to-end integration on physical hub (carries forward CLAUDE.md original tests):
  - Test BLE connection ✓ (original)
  - Test program upload reliability ✓ (original)
  - Test TunnelMessage latency — now JSON-payload perf benchmark, see 2.5.5
  - Test reconnection after disconnect ✓ (original)
  - Each existing block produces same behaviour as MVP
  - Capability declaration matches connected ports when hot-swapping sensors
  - Heartbeat survives an Android screen-lock cycle
- **2.5.4** Connection stability stress: 30-minute classroom-style session with disconnect/reconnect cycles, multiple hubs
- **2.5.5** **JSON perf benchmark gate** (decision #2): 60-second sustained 20 Hz movement updates, measure payload drop rate and latency. Acceptance: <1% drop rate, <100 ms p99 latency. If failed, escalate to v0.6 binary encoding via §3.2.

### 2.6 Risks

- **JSON parse cost on the hub** — mitigated by perf gate + v0.6 §3.2 binary fallback
- **Subscription model changes sensor block semantics** — "get current value" becomes "get last cached value"; mitigated by auto-subscribe-on-first-read and documented in tooltips
- **Capability declaration timing** — ~1 s delay between tunnel-open and capability emit; Connectivity blocks need to gate on `OnCapabilityReceived` rather than `HubConnected`
- **Validator noise** — fixed by per-(port, parameter) per-second rate-limit on `OnError`

---

## Phase 3 — Post-MVP block expansion

**Goal:** add the blocks documented in the post-MVP roadmap (`mvp_status_and_postmvp.md`), built on SSP from day one. Now significantly simplified because v0.6 covers nearly every block with a canonical command.

**Effort:** ~2–3 weeks, paced by App Inventor designer work and end-user testing.

**Acceptance criteria:**
- All blocks in §3.1–§3.7 below available in App Inventor palette
- 7-component architecture (5 existing + Sound + System; possibly 8 with Music)
- Light matrix animation perf benchmark passes (see §3.3)
- `architecture_multicomponent.md` and `mvp_status_and_postmvp.md` memory files updated

### 3.1 `LegoSpikeMotors` expansion

Every block from the post-MVP roadmap, mapped to v0.6 commands:

| Block | SSP v0.6 mapping |
|---|---|
| `RunMotorForDuration(direction, amount, unit)` | `motor.run` with `duration` + `duration_unit` ("ms"/"degrees"/"rotations") — bridge-side timing |
| `MotorGoToPosition(position, direction)` | `motor.goto` with `position` + `direction` |
| `GetMotorPosition()` → `MotorPositionRead` event | `sensor.subscribe` on motor port (`position` feature) |
| `GetMotorSpeed()` → `MotorSpeedRead` event | `sensor.subscribe` on motor port (`speed` feature) |
| `GoToRelativeMotorPosition(degrees)` | `motor.goto` with `relative: true` (verify v0.6 — may need `x_relative_goto` or `motor.run` with `duration_unit:"degrees"`) |
| `ResetRelativeMotorPosition()` | `motor.reset` |
| `RelativeMotorPosition()` | `sensor.read` on motor port (`position` feature) |
| `StartMotorWithPower(power)` | Open question — v0.6 doesn't separate speed/power. Investigate SPIKE FW. Likely `motor.run` with `speed` and `mode:"power"` extension (v0.7 candidate) |
| `MotorPower()` | `sensor.read` on motor port (`load` feature) |
| `StopAndCoastMotor(port)` | `motor.stop` with `stop_action:"coast"` |
| `SetMotorAcceleration(rate)` | Open — no canonical home in v0.6. Use `x_acceleration` extension, file v0.7 wishlist |

### 3.2 `LegoSpikeMovement` expansion

| Block | SSP v0.6 mapping |
|---|---|
| `MoveForDuration(direction, amount, unit)` | `movement.drive` with `duration` + `duration_unit` |
| `MoveWithSteeringForDuration(steering, amount, unit)` | `movement.drive` with `steering` + `duration` |
| `StartMovingAtSpeed(leftSpeed, rightSpeed)` | `movement.drive` with explicit `left_speed` + `right_speed` (verify v0.6 — spec only documents `speed` + `steering`; may need extension or fallback to dual `motor.run`) |
| `SetMotorRotationDistance(distance)` | Client-side config affecting subsequent `movement.drive` `duration` math when `duration_unit:"rotations"` |
| `SetMovementBrakeAtStop(mode)` | Client-side config sticking the `stop_action` parameter onto subsequent `movement.stop` calls |
| `SetMovementAcceleration(rate)` | Same as motor — `x_acceleration` extension until spec'd |

### 3.3 `LegoSpikeLight` expansion (display port)

| Block | SSP v0.6 mapping |
|---|---|
| `SetPixel(x, y, brightness)` | `led.matrix.pixel` |
| `ShowImage(name)` | `led.matrix.image` |
| `ShowText(text, scroll)` | `led.matrix.text` |
| `ClearMatrix()` | `led.matrix.clear` |
| `TurnOnLightMatrixForSeconds(image, seconds)` | `led.matrix.image` then `led.matrix.clear` after delay |
| `SetLightMatrixBrightness(level)` | `led.matrix.brightness` *(v0.6 new)* — bridge adds `brightness` feature on display port |
| `RotateLightMatrix(rotation)` / `SetLightMatrixOrientation(orientation)` | `led.matrix.orientation` *(v0.6 new)* — bridge adds `orientation` feature |
| Light matrix animation (multiple pixels per frame) | `cmd:"batch"` with `atomic:true` per frame — bridge adds `supports_batch: true` to capability |
| `LightUpDistanceSensor(topLeft, topRight, bottomLeft, bottomRight)` | Open — sensor-attached 4-LED indicator. v0.7 candidate as a `display` port on the distance sensor itself (width:2, height:2, depth:grayscale). Ship as `x_distance_led` extension until spec'd. |
| 3×3 Color Matrix accessory blocks | Open — would be a `display` port (3×3 rgb). Ship as separate component (`LegoSpikeColorMatrix`) only if the accessory is in scope; otherwise defer to v0.7+ |
| `WhenLightMatrixTapped` | Open — newer SPIKE firmware exposes the 5×5 matrix as touch-sensitive. Verify FW 3.x API; if supported, fires `system`-style event. v0.7 candidate for canonical `touch` feature on `display` port |

**Acceptance criteria additional:** light matrix animation perf benchmark — 25 pixels updated at 10 Hz, single `batch` command per frame, no payload drops.

### 3.4 `LegoSpikeSensors` expansion (IMU + threshold events)

**IMU / orientation:**

| Block | SSP v0.6 mapping |
|---|---|
| `GetHubAcceleration()` | `sensor.read` on `imu` port (`acceleration` feature) |
| `GetHubAngularVelocity()` | Open — no canonical feature in v0.6. Use `x_angular_velocity` or compute client-side from pitch/roll/yaw deltas |
| `GetHubOrientation()` | Aggregates pitch/roll/yaw client-side |
| `GetHubFaceOrientation()` | Discrete enum: `face_up`/`face_down`/`port_a_up`/`port_a_down`/`port_e_up`/`port_e_down`. Computed client-side from pitch/roll OR filed as v0.7 canonical `face_orientation` feature on `orientation` port |
| `GetGesture()` | Last cached gesture event value |
| `WhenGesture(type)` | Gesture event from `imu` port; dropdown values populated from v0.6 capability constraint enum |
| `WhenHubShaken` | Convenience block — `WhenGesture("shake")` |
| `ResetYaw()` | Open — no canonical command in v0.6. Use `x_reset_yaw` extension; file v0.7 wishlist |
| `SetYaw(degrees)` | Open — no canonical command in v0.6. `x_set_yaw` extension; file v0.7 wishlist |
| `SetHubSensorOrientation(orientation)` | Open — similar, `x_set_orientation` extension |

**Port-attached sensors (color / distance / force):**

| Block | SSP v0.6 mapping |
|---|---|
| `GetRelativeMotorPosition()` | Listed under sensors in roadmap but actually a motor sensor; covered by §3.1 |
| `GetReflectedLight()` | `sensor.read` on color sensor port (`reflected` feature) |
| `GetColorRGB()` → `ColorRGBRead(port, r, g, b)` event | `sensor.read` on color sensor port (`rgb` feature). Event value is a 3-element array `[r, g, b]`. |
| `IsColor(name)` / `IsDistance(threshold)` / `IsPressed()` | Client-side boolean derived from last subscription value |
| `WhenColorIs(name)` / `WhenCloserThan(cm)` / `WhenPressureIs(threshold)` / `WhenTilted(direction)` | `sensor.subscribe` with `mode:"on_change"`; threshold evaluated client-side, event fires when crossing |
| `WhenColorChanges` / `WhenDistanceChanges` / `WhenPressureChanges` | `sensor.subscribe` with `mode:"on_change"`, no threshold — fires whenever the value changes at all |
| `WhenTimer(seconds)` | Client-side timer (no SSP equivalent needed) |

### 3.5 `LegoSpikeSound` *(new component)*

Phase-2 bridge declared `speaker` with only `beep`. Phase 3 adds full speaker support after FW capability verification.

| Block | SSP v0.6 mapping |
|---|---|
| `Beep(freq, duration)` | `sound.beep` |
| `PlayBeepForSeconds(pitch, seconds)` | `sound.beep` with `duration` |
| `StartPlayingBeep(freq)` | `sound.beep` with no duration (verify spec — may need `duration:0` or `duration:Infinity` convention) |
| `StopAllSounds()` | `sound.stop` |
| `SetVolume(level)` | `sound.set_volume` — bridge adds `volume` feature on speaker port |
| `GetVolume()` | Client-side cache of last `SetVolume` value. v0.6 has no `sound.get_volume`; file v0.7 wishlist if a real getter is needed |
| `PlayBuiltin(name)` / `StartSound(name)` | `sound.play` with `sound` field; dropdown populated from `builtin_sounds` capability array |
| `PlaySoundUntilDone(name)` | Send `sound.play`, block on completion event from bridge (verify bridge emits `{"event":"sound_complete"}` or similar) |

**Open question:** SPIKE FW 3.x `hub.sound` API surface — needs verification of which built-in sounds exist and whether `play_sound` supports a wait-for-completion mode.

### 3.6 `LegoSpikeMusic` *(new component, conditional)*

Component exists ONLY if SPIKE FW 3.x supports MIDI-style note playback. If not, music blocks fall back to looped `sound.beep` with frequency-from-note-name conversion client-side.

| Block | SSP v0.6 mapping |
|---|---|
| `PlayNote(note, duration)` | If MIDI supported: `sound.play` with `notes:` field; else client-side beep |
| `PlayDrum(name)` | `sound.play` with built-in percussion sound names |
| `Rest(duration)` | Client-side delay |
| `SetInstrument(name)` | Client-side state affecting subsequent `PlayNote` rendering (if MIDI: include in `notes:` payload; else affects beep waveform — likely no-op for SPIKE) |
| `SetTempo(bpm)` / `ChangeTempo(delta)` / `GetTempo()` | Client-side state, applied as `tempo:` field in `sound.play` |

**Open question:** if SPIKE doesn't natively support MIDI, decide whether to ship `LegoSpikeMusic` at all. Falling back to beep-based music gives poor results — may be better to omit and let users compose via individual `Beep` calls.

### 3.7 `LegoSpikeSystem` *(new component)*

| Block | SSP v0.6 mapping |
|---|---|
| `GetBatteryLevel()` → `BatteryLevelRead(percent)` event | `system.subscribe metric=battery`, cached value returned |
| `GetTemperature()` | `system.subscribe metric=temperature`, cached |
| `IsCharging()` | `system.subscribe metric=charging`, cached |
| `GetRSSI()` | `system.subscribe metric=connection_rssi`, cached |
| `WhenButtonPressed(button)` / `WhenButtonReleased(button)` / `WhenButtonHeld(button)` | `system.subscribe metric=button.<name>`; event fires on state-transition |
| `WhenHubButtonPressed` | Convenience — equivalent to `WhenButtonPressed("center")` |

### 3.8 SPIKE Prime block categories handled at the App Inventor layer

The SPIKE Prime native app has several block categories that should NOT be reimplemented in this extension — they map directly to existing App Inventor primitives. Documenting here so future contributors don't redundantly add them:

| SPIKE category | App Inventor equivalent |
|---|---|
| **Control flow** — `repeat`, `forever`, `if/else`, `while` | App Inventor `Control` blocks (`while`, `if then`, `for each`, etc.) |
| **Wait blocks** — `wait N seconds`, `wait until <condition>` | App Inventor `Clock` component + a polling loop |
| **Operators** — math, logic, string ops, random | App Inventor `Math`, `Logic`, `Text` blocks |
| **Variables / globals** | App Inventor `Variables` blocks (initialize, set, get) |
| **My Blocks** — user-defined procedures | App Inventor `Procedures` blocks |
| **Boolean inputs** to event handlers | App Inventor parameter passing |

This means a user porting a SPIKE app project into App Inventor needs to translate hardware blocks via this extension AND control-flow / data blocks via App Inventor's built-ins. Worth documenting in `docs/SSP_BRIDGE_GUIDE.md` once Phase 3 ships.

### 3.9 Architecture update

After Phase 3 lands:
- **7 components default**: Connectivity, Motors, Movement, Light, Sensors, Sound, System
- **8 components if Music ships**: above + Music
- **Update memory files**: `architecture_multicomponent.md`, `mvp_status_and_postmvp.md`
- **Update `README.md`** Components table (currently lists 5)
- **Update `docs/SSP_BRIDGE_GUIDE.md`** with the full v0.6 mapping table

### 3.10 v0.7 wishlist candidates surfaced during Phase 3

To file as `SSP v0.7 wishlist` issue against `solaria-hub` once Phase 3 implementation surfaces concrete need:

- `motor.set_acceleration` action + `acceleration` feature on motor port
- `motor.run mode:"power"` to distinguish power-control from speed-control
- `orientation.reset` (or `imu.reset_yaw`) command for IMU
- `orientation.set_yaw` to set yaw to a specific value (not just reset to zero)
- `orientation.set_reference` for hub mounting orientation
- `face_orientation` feature on `orientation` port — discrete enum of which hub face is currently up (face_up, face_down, port_a_up, port_a_down, port_e_up, port_e_down)
- `angular_velocity` feature on `orientation` port — currently no canonical name
- 3×3 RGB matrix display support (already partly covered by `display` port `depth:"rgb"` — but `width:3, height:3` would need spec example)
- Sensor-attached LEDs (distance sensor 4-LED indicator) — likely a `display` port type on sensor ports
- `touch` feature on `display` port — for SPIKE 5×5 light matrix tap detection (newer FW)
- Movement `left_speed`/`right_speed` for tank-style control
- Sound `play_until_done` semantics — does `sound.play` return immediately or block? Spec should say.
- `sound.get_volume` getter — v0.6 only has `sound.set_volume`

---

## Phase 4 — Client/bridge architectural split (PR 2)

**Goal:** the Java extension stops being SPIKE-Prime-specific. Future Boost / EV3 / Arduino bridges work without changing the App Inventor side.

**Effort:** ~1–2 weeks. Most work is repo extraction and CI for the new bridge release artifact.

**Acceptance criteria:**
- `TransportProfile` interface exists; `SpikeTransportProfile` implements it
- Bridge Python lives in `solaria-bridge-spike-prime` repo with versioned releases
- This repo downloads the bridge program from a release artifact on first connection (decision #3)
- Existing users see no behaviour change — bridge cached locally after first download
- Falls back to baked-in default bridge if network unavailable on first run

### 4.1 Transport profile abstraction

- **4.1.1** Create `TransportProfile.java` interface:
  - `connect(BluetoothDevice device)`, `disconnect()`
  - `send(byte[] payload)`, `setOnReceive(Consumer<byte[]> handler)`
  - `discoveryFilter()` — what to look for in BLE scans / device discovery
  - `profileMetadata()` — returns the v0.6 §2.1 profile JSON
- **4.1.2** `SpikeTransportProfile` implementation — extract FD02 UUIDs, COBS+XOR framing, TunnelMessage 0x32 wrapping from `BluetoothInterfaceImpl` into one class
- **4.1.3** `SSPClient` takes a `TransportProfile`; everything above it becomes hardware-agnostic
- **4.1.4** Add `TransportProfileRegistry` (static) so future profiles register themselves

### 4.2 Protocol library extraction

Per Solaria architecture v2.0, SPIKE Prime is a **TYPE 2 hardware** (closed firmware). The new naming convention for Type 2 splits the work into two repos:

- **`solaria-lib-spike-prime`** — the protocol translation library: hub-side Python program + reference Java/Python/JS SDKs + protocol spec. Language-agnostic core; reference implementations per client language.
- **`solaria-appinventor-spike-prime`** — the App Inventor `.aix` wrapper that consumes the lib. (This repo, after rename.)

Tasks:

- **4.2.1** New repo: `solaria-lib-spike-prime`
- **4.2.2** Move hub-side Python out of the Java string constant into a versioned file in `solaria-lib-spike-prime`
- **4.2.3** Move the SSP infrastructure (`SSPMessage`, `SSPParser`, `SSPClient`, `SpikeTransportProfile`, `CapabilityStore`, `HeartbeatManager`, `SubscriptionManager`, `Validator`) into `solaria-lib-spike-prime/java/` as the reference Java SDK that wrapper repos consume
- **4.2.4** Set up GitHub Releases on `solaria-lib-spike-prime` with the bridge program + Java SDK JAR as release artifacts
- **4.2.5** This repo: replace embedded Python string with a `BridgeDownloader` class that fetches the appropriate version from `solaria-lib-spike-prime` Releases API on first connection
- **4.2.6** Cache downloaded bridge locally in Android app's private storage; re-download only when version mismatch detected
- **4.2.7** Bake a known-good default bridge into the .aix as a fallback when network is unavailable on first run
- **4.2.8** Add stub `python/` and `web/` directories in `solaria-lib-spike-prime` for future Python and Web client SDKs (don't implement; mark "future contribution welcome")

### 4.3 Repo and block naming

**Repo rename:**
- This repo: `appinventor-lego-spike-prime-extension` → **`solaria-appinventor-spike-prime`**
- GitHub redirects old URLs after rename — README link in `solaria-hub` and the v0.1.0 release URL should still work, but solaria-hub README/ROADMAP should be updated to reference the new name
- The .aix filename changes accordingly: `io.github.appinventor.legospikeprime.aix` → `SolariaSpikePrime.aix`

**Block rename: `LegoSpike*` → `SolariaSpikePrime*`** (settled — defer until this phase, not urgent):
- E.g., `LegoSpikeConnectivity` → `SolariaSpikePrimeConnectivity`, `LegoSpikeMotors` → `SolariaSpikePrimeMotors`, etc.
- Rationale: avoids trademark friction from LEGO, aligns with Solaria's `Solaria*` ecosystem prefix
- Tradeoff: less discoverable for students searching for "LEGO" in the App Inventor extension marketplace. Mitigated by clear naming in the .aix filename and repo README.
- Implementation: rename `@SimpleComponent` annotations; existing user `.aia` projects will need to be re-saved against the new component names (App Inventor handles this via the migration system)

### 4.4 Ecosystem alignment (per Solaria architecture v2.0)

Phase 4 explicitly aligns this repo with the [solaria-hub v2.0 architecture](https://github.com/edcheng1010/solaria-hub/blob/main/ARCHITECTURE.md):

- Repo classification: **TYPE 2 protocol bridge** (closed-firmware hardware)
- Repo split per convention: `solaria-lib-spike-prime` (library) + `solaria-appinventor-spike-prime` (App Inventor wrapper)
- Multi-platform LEGO support (Boost / Powered Up / SPIKE Essential / EV3) is **out of scope for this repo** — those become separate `solaria-lib-*` + `solaria-<platform>-*` repos in the Solaria ecosystem. Removed from our Phase 5 (which is now about classroom multi-hub, see Phase 5).

### 4.5 Risks

- App Inventor dynamic dropdowns from live capability data may need designer-side work (`@Options` enums are compile-time; dropdowns populated from runtime capability data requires `@DesignerProperty` arrays with editor-time defaults plus runtime override)
- Repo split is a major version bump; existing `.aix` keeps working but new releases need version-matched bridge program
- Network dependency on first connection — graceful fallback to baked-in default handles offline case; need to test airplane-mode behaviour
- Bridge version skew — client speaks v0.6, bridge speaks v0.5 (or vice versa): client must read `ssp_version` in capability and degrade gracefully

---

## Phase 5 — Classroom multi-hub

**Goal:** Support **N SPIKE Prime hubs connected simultaneously** to a single App Inventor app on a single Android device. This is the classroom use case — one teacher's phone driving multiple students' hubs, or one student's app interacting with multiple hubs in a single project.

Carried forward from the original `mvp_status_and_postmvp.md` roadmap line: *"Multi-hub simultaneous control (planned)"*.

**Out of scope:** Multi-platform LEGO support (Boost / Powered Up / SPIKE Essential / EV3). Per the Solaria v2.0 architecture, each hardware family gets its own `solaria-lib-*` + `solaria-<platform>-*` repos. This repo stays SPIKE-Prime-only. See [solaria-hub ROADMAP.md](https://github.com/edcheng1010/solaria-hub/blob/main/ROADMAP.md) for ecosystem-wide multi-hardware planning.

**Effort:** ~2 weeks. Mostly Java refactoring (per-hub state isolation) and block API redesign.

**Acceptance criteria:**
- 4+ SPIKE Prime hubs connected concurrently to one Android device, all responding to commands within 200 ms p99
- Each hub maintains its own capability declaration, subscription state, and heartbeat lifecycle
- Block API lets users target a specific hub by stable identifier (e.g., MAC address or user-assigned label)
- BLE connection management does not leak resources when hubs disconnect
- Classroom stress test passes: 30 minutes of mixed connect/disconnect across 4+ hubs with no extension crashes

### 5.1 Per-hub state isolation

Phase 2's singletons (`SSPClient`, `CapabilityStore`, `SubscriptionManager`, `HeartbeatManager`) currently assume one active hub. Refactor to per-hub instances keyed by hub identifier.

- **5.1.1** Introduce `HubInstance.java` — owns one hub's `SSPClient` + `CapabilityStore` + `SubscriptionManager` + `HeartbeatManager` + `TransportProfile`
- **5.1.2** `HubRegistry.java` — maps `hubId` (MAC address or label) → `HubInstance`; thread-safe access
- **5.1.3** `LegoSpikeConnectivity` (or renamed `SolariaSpikePrimeConnectivity`) becomes the hub manager:
  - New blocks: `ConnectHub(address, label)`, `DisconnectHub(hubId)`, `GetConnectedHubs()`, `GetActiveHub()`, `SetActiveHub(hubId)`
  - Existing single-hub blocks (`Connect`, `Disconnect`) become convenience wrappers around the multi-hub equivalents
- **5.1.4** All component method signatures gain an optional `hubId` parameter (defaults to "active hub" — backward-compatible for single-hub users)
- **5.1.5** Events fire with a `hubId` parameter so blocks can disambiguate which hub a sensor reading came from

### 5.2 BLE multi-connection on Android

Android has practical limits on concurrent BLE GATT connections (typically 4–8 depending on hardware/OS).

- **5.2.1** `BluetoothInterfaceImpl` refactored to manage N concurrent GATT instances
- **5.2.2** RSSI staleness logic (CLAUDE.md Rule 2) extended to track per-device staleness across the hub list
- **5.2.3** `wasScanningBeforeConnection` flag (CLAUDE.md Rule 4) becomes per-hub-connection-attempt
- **5.2.4** Connection retry / timeout policies decoupled per hub so one slow hub doesn't block others
- **5.2.5** Add diagnostic blocks: `GetHubRSSI(hubId)`, `GetHubConnectionState(hubId)`

### 5.3 Block API design for multi-hub

Three patterns to choose between (need designer-side prototyping in App Inventor):

- **Option A — Implicit active hub** (recommended for backward compat): One "active hub" at any time. All component blocks target the active hub. `SetActiveHub(hubId)` switches. Simplest for students who only use one hub; explicit for multi-hub.
- **Option B — Per-block hub parameter**: Every component block gains a `Hub` dropdown/parameter. More explicit; more typing.
- **Option C — Per-component hub binding**: Each component instance (`LegoSpikeMotors1`, `LegoSpikeMotors2`) binds to a specific hub at instantiation. User adds N components for N hubs.

Decide during 5.3 prototyping. Option A is the recommended default with Option B available as advanced.

### 5.4 Subscription multiplexing

Per-hub `SubscriptionManager` instances must aggregate sensor events from all hubs into a single event stream for the App Inventor app, tagged by `hubId`:

- **5.4.1** Sensor events emit `{hubId, port, type, value}` instead of just `{port, type, value}`
- **5.4.2** Existing single-hub event handlers continue to receive events only for the active hub (or all hubs, depending on a new `OnlyFromActiveHub` property — default true)
- **5.4.3** `WhenColorIs` / `WhenCloserThan` / etc. gain optional `Hub` parameter for targeted listeners

### 5.5 Heartbeat coordination

5-second ping per hub means 4 hubs = 4 pings/sec from the phone. Need to verify this doesn't exceed Android BLE throttling:

- **5.5.1** Stagger pings across hubs (don't fire all at once)
- **5.5.2** If many hubs (>4), consider extending the ping interval to 10 s and disconnect-after-3-missed (vs current 5 s / 2 missed) — adjust v0.6 §4.1 compliance documentation accordingly
- **5.5.3** Per-hub `OnHeartbeatLost(hubId)` events instead of a single global event

### 5.6 Testing

- **5.6.1** Stress test: 4 hubs, 30 minutes, mixed connect/disconnect/reconnect, sensor subscriptions active
- **5.6.2** Resource leak check: connect-disconnect 100 hub cycles, verify no Android BLE handle leak (`adb shell dumpsys bluetooth_manager`)
- **5.6.3** Subscription scaling: 4 hubs × 6 sensor subscriptions each = 24 active subscriptions, verify no event drops
- **5.6.4** Block-API usability test with a teacher running a real classroom scenario

### 5.7 Risks

- **Android BLE concurrency limit** varies by device (typically 4 max on older phones, 7 on Pixel/recent). Document the limit in README; provide graceful failure when limit is hit.
- **Block API churn** — multi-hub blocks change the signatures of existing single-hub blocks. Backward-compat strategy (Option A "active hub" default) mitigates but may still confuse existing users.
- **Per-hub state size** — N hubs × ~10 KB per `HubInstance` × 4 hubs = manageable; verify no memory pressure on Android Go devices.

---

## Cross-cutting concerns

### Documentation maintenance

| File | Updated in | Notes |
|---|---|---|
| `ARCHITECTURE.md` | Phase 2 | Rule 5 obsolete after SSP; rewrite to reference SSP wire format |
| `CLAUDE.md` | Phase 2 | Critical Architectural Rules updated for SSP v0.6 |
| `README.md` | Phase 3 | Components table from 5 → 7 (or 8 with Music) |
| `docs/SSP_BRIDGE_GUIDE.md` | Phase 2 | New file — full v0.6 mapping table |
| `docs/IMPLEMENTATION_PLAN.md` | Each phase end | This file |
| Memory `architecture_multicomponent.md` | Phase 3 | 5 → 7 components |
| Memory `mvp_status_and_postmvp.md` | Phase 3 | "Post-MVP" items moved to completed |
| Memory `hub_command_protocol.md` | Phase 2 | Custom binary protocol → SSP v0.6 JSON |
| Memory `protocol_facts.md` | Phase 2 | Add SSP framing facts |

### Testing strategy (carried from CLAUDE.md original Phase 3)

- Unit tests on every new Java class — JUnit, run on Ant build
- Python-side unit tests on desktop MicroPython runtime
- End-to-end integration on physical hub — manual, checklists per phase
- Stress / longevity testing — 30-minute classroom-style session before each phase merges
- App Inventor block-level testing — manual; reference `.aia` project per component on Android device
- Multi-hub stress (Phase 2+): 4+ hubs on the same Android device, simultaneous connection-stability test (validates RSSI staleness logic from CLAUDE.md Rule 2 still works under SSP)
- Reconnection-after-disconnect: explicit test that capability re-declaration works on re-connect

### Solaria ecosystem context

The broader strategic direction for the Solaria platform is documented in [VISION.md](https://github.com/edcheng1010/solaria-hub/blob/main/VISION.md) in the solaria-hub repository. Key points that inform the later phases of this plan:

- **Layer 3 (AI/Agent)** — the long-term goal is an agentic overlay across all clients and hardware, generating SSP commands from natural language ("vibe coding") and providing GUI-based electronics configuration. This is where Phase 5+ work eventually lands.
- **Open-core + hosted SaaS** — the platform is free/open for personal use (BYOK LLM key); a hosted paid tier for schools covers fleet management and curriculum integration.
- **Augmentation, not competition** — Solaria sits on top of existing tools (App Inventor, Scratch, Arduino IDE, MakeCode) rather than replacing them. This extension embodies that principle.

### SSP spec contributions tracker

| Wishlist | Issue | Status | Items integrated |
|---|---|---|---|
| v0.2 | #1 | ✅ Integrated | Transport profiles, binary encoding (reserved), movement category, capability schema, request_id, heartbeat, sensor flow-control |
| v0.3 | #2 | ✅ Integrated | led.matrix category, display port, orientation port, sound.play payloads, speaker port, system.subscribe |
| v0.4 | #3 | ✅ Integrated | Parameter constraints, gesture event consistency |
| v0.5 | #4 | ✅ Integrated | Button format, array constraint type, gesture constraints, display dimension implicit constraints, plus `string` constraint type as bonus |
| v0.6 | #5 | ✅ Integrated | RFCOMM transport, binary encoding finalised, batch commands, motor duration/stop_action, sound.set_volume, led.matrix.brightness/orientation |
| v0.7 | TBD | 📝 Pending | Items in §3.10 above to file once Phase 3 surfaces concrete need |

---

## Inheritance from prior plans

For traceability — every item from prior planning documents is accounted for in this plan.

### From original CLAUDE.md (Known Issues / Next Steps section)

| Original phase | Item | Where in this plan |
|---|---|---|
| Phase 1 — Protocol Correction | Verify COBSEncoder constants | ✅ Phase 1 (done) |
| Phase 1 | Implement file upload protocol | ✅ Phase 1 (done) |
| Phase 1 | Implement program start | ✅ Phase 1 (done) |
| Phase 1 | Implement TunnelMessage send/receive | ✅ Phase 1 (done) |
| Phase 1 | Create hub-side Python controller | ✅ Phase 1 (done); rewritten in Phase 2.1 for SSP |
| Phase 2 — Hub-Side Python | Motor control all 6 ports | ✅ Phase 1 (done); refined in Phase 2 + Phase 3 |
| Phase 2 | LED matrix control | ✅ Phase 1 (basic); expanded in Phase 3.3 |
| Phase 2 | Sensor reading | ✅ Phase 1 (basic); expanded in Phase 3.4 |
| Phase 2 | Hub status (battery, orientation) | ⏳ Phase 3.7 (System component) + Phase 3.4 (IMU) |
| Phase 3 — Testing | BLE connection | ✅ Phase 1 (done); re-tested in Phase 2.5.3 |
| Phase 3 | Program upload reliability | ✅ Phase 1 (done); re-tested in Phase 2.5.3 |
| Phase 3 | TunnelMessage latency | ✅ Phase 1 (done); now JSON-payload benchmark in Phase 2.5.5 |
| Phase 3 | Reconnection after disconnect | ✅ Phase 1 (done); re-tested in Phase 2.5.3 |
| Future — Multi-Hub | Boost / EV3 / Essential | 🔀 Moved out of this repo per Solaria v2.0 — those become separate `solaria-lib-*` + `solaria-<platform>-*` repos in the ecosystem (see solaria-hub ROADMAP) |
| Future | Abstract BluetoothInterfaceImpl | ⏳ Phase 4 |
| Post-MVP (memory) | Multi-hub simultaneous control | ⏳ Phase 5 (now its own dedicated phase — N concurrent SPIKE Prime hubs) |

### From mvp_status_and_postmvp.md (post-MVP block list)

Every block listed in that memory is mapped to a Phase-3 section in §3.1–§3.7 above. The original memory groups blocks by component; this plan groups them the same way and adds the v0.6 SSP mapping for each.

### From the PR 1 + PR 2 plans drafted in this session

| Original PR | Now in this plan |
|---|---|
| PR 1 — SSP-compatible hub-side Python + Java JSON emit | Phase 2 |
| PR 2 — full client/bridge separation + SSP proposals | Phase 4 (with Phase 5 as the payoff) |

---

## Open questions (active)

These remain unresolved and should be answered before / during the relevant phase:

1. **SPIKE Prime FW 3.x sound API surface** — Which built-in sounds exist? Does `play_sound` block until complete or return immediately? (affects §3.5 `builtin_sounds` and `PlaySoundUntilDone`)
2. **SPIKE Prime FW 3.x MIDI support** — Does `hub.sound` accept note sequences or just frequency beeps? (affects §3.6 `LegoSpikeMusic` viability)
3. **Minimum reliable subscription interval over SPIKE BLE** — Memory says ~50 ms; needs benchmark with actual sensor load (affects §2.1 constraint declaration)
4. **`motor.run` indefinite-duration encoding** — Spec says `duration` is optional; does omitting it mean "indefinite"? Or should there be `duration: 0` / `duration: -1` convention? (affects §2.1.2 and §3.5)
5. **`motor.goto` relative vs absolute** — v0.6 has `position` constraint with `wraps:true` suggesting absolute. SPIKE has both relative and absolute Python APIs. Does v0.6 cover relative? Or need `x_relative_goto`? (affects §3.1 `GoToRelativeMotorPosition`)
6. **`movement.drive` left/right speeds** — v0.6 example shows `speed` + `steering`. Some hubs prefer tank-style explicit left/right. File v0.7 wishlist? (affects §3.2 `StartMovingAtSpeed`)
7. **SPIKE Prime 5×5 matrix max simultaneous-update rate** — for §3.3 light matrix animation benchmark
8. **`@Options` enum vs capability-driven dropdowns** — Phase 4 may need real designer-side investigation of App Inventor extension UI capabilities

---

## Instructions for whoever is implementing this

1. Read this document and `ARCHITECTURE.md` before starting any task.
2. Within a phase, tasks must be completed in section order. Within §2.3 (component migration), Connectivity must land first — other components depend on `CapabilityStore` integration.
3. Do not mark a phase complete until all acceptance criteria are verified on a physical hub.
4. Open the v0.7 wishlist issue against `solaria-hub` (per §3.10) when Phase 3 surfaces concrete needs — don't file speculatively.
5. Before committing, check that no MIT Hong Kong Innovation Node references have crept back in (the repo is public).
6. Commit messages: plain, no Co-Authored-By trailer (project owner preference).
7. Do not push to remote without explicit per-commit approval from the project owner.
8. Phase 2 perf gate (§2.5.5) is a hard merge blocker — if JSON drops payloads, switch to binary encoding (v0.6 §3.2) before merging.
