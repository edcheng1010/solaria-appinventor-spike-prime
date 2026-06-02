# Implementation Plan — LEGO SPIKE Prime App Inventor Extension

> **Unofficial integration.** Independent open-source project, not affiliated with the LEGO Group or the Massachusetts Institute of Technology. See [NOTICE](../NOTICE) for trademark and licensing details.

**Last revised:** 2026-06-03
**Targeting:** SSP v0.8
**Status:** Phase 1, Phase 2, and Phase 3 complete · Phase 4 next
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

This plan targets **SSP v0.8** as the spec stands today. v0.8 absorbed every remaining edge case from the SPIKE Prime bridge perspective:

| Wishlist | Status | Notes |
|---|---|---|
| v0.2 | ✅ Integrated | Transport profiles, binary encoding reserved, movement category, capability schema, request_id, heartbeat, sensor flow-control |
| v0.3 | ✅ Integrated | led.matrix, display port, orientation port, sound.play payloads, speaker port, system.subscribe |
| v0.4 | ✅ Integrated | Parameter constraints (`int`/`float`/`enum`/`bool`), gesture event consistency |
| v0.5 | ✅ Integrated | Button format formalised, `array` + `string` constraints, gesture constraint enum, implicit-coordinate-constraints from port dimensions |
| v0.6 | ✅ Integrated | RFCOMM transport, binary encoding finalised, batch commands, motor duration / stop_action, sound.set_volume, led.matrix.brightness / .orientation |
| v0.7 | ✅ Integrated | `face_orientation` + `angular_velocity` features, new `orientation.*` command category (set_yaw/reset_yaw/set_reference), `touch` on display, sensor-attached LED displays, `tank_drive` (left_speed/right_speed) on movement, `sound.read` getter, `motor.run mode:"power"` |
| v0.8 | ✅ Integrated | `motor.set_acceleration` + `movement.set_acceleration`, `sound.play wait:true` + `sound_complete` event, MIDI canonical schema (notes/chords/instruments/drums), `motor.goto mode:"relative"`, `motor.run` indefinite-duration clarification |
| v0.9+ | 📝 Future | DFU, stream multiplexing, auth — not needed for SPIKE Prime bridge |

**No outstanding wishlist dependencies for Phases 3–5.** The Phase 3 expansion can ship fully v0.8-compliant with zero `x_` extensions. **Phase 2 currently ships v0.6**; Phase 3 will bump the bridge to v0.8 as part of the new-feature work.

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
- Hub-side Python `hub_controller.py` bumped to declare `"ssp_version": "0.8"` and includes all v0.7+v0.8 features (`face_orientation`, `angular_velocity`, `power`, `touch`, `tank_drive`, `acceleration`, `goto_modes`, `sound_wait_supported`)
- New `orientation.*` command category handlers (`set_yaw`, `reset_yaw`, `set_reference`) on the hub
- `motor.set_acceleration` / `movement.set_acceleration` handlers
- `sound.play wait:true` with `sound_complete` event emission
- `motor.goto mode:"absolute"|"relative"` dispatching
- `motor.run` with omitted `duration` runs indefinitely (per v0.8 §6.1 clarification)
- `architecture_multicomponent.md` and `mvp_status_and_postmvp.md` memory files updated

### 3.1 `LegoSpikeMotors` expansion

Every block from the post-MVP roadmap, mapped to v0.8 commands:

| Block | SSP v0.8 mapping |
|---|---|
| `RunMotorForDuration(direction, amount, unit)` | `motor.run` with `duration` + `duration_unit` ("ms"/"degrees"/"rotations") — bridge-side timing |
| `MotorGoToPosition(position, direction)` | `motor.goto` with `position` + `direction`, `mode:"absolute"` (v0.8 default) |
| `GetMotorPosition()` → `MotorPositionRead` event | `sensor.subscribe` on motor port (`position` feature) |
| `GetMotorSpeed()` → `MotorSpeedRead` event | `sensor.subscribe` on motor port (`speed` feature) |
| `GoToRelativeMotorPosition(degrees)` | `motor.goto` with `position:<degrees>`, `mode:"relative"` *(v0.8 §6.1)* — bridge declares `"relative"` in `goto_modes` array |
| `ResetRelativeMotorPosition()` | `motor.reset` |
| `RelativeMotorPosition()` | `sensor.read` on motor port (`position` feature) |
| `StartMotorWithPower(power)` | `motor.run` with `speed` value and `mode:"power"` *(v0.7 §6.1)* — bridge declares `power` feature on motor port |
| `MotorPower()` | `sensor.read` on motor port (`load` feature) |
| `StopAndCoastMotor(port)` | `motor.stop` with `stop_action:"coast"` |
| `SetMotorAcceleration(rate)` | `motor.set_acceleration` with `rate:<ms>` *(v0.8 §6.1)* — bridge declares `acceleration` feature on motor port. Rate is milliseconds to ramp from 0–100% speed |

### 3.2 `LegoSpikeMovement` expansion

| Block | SSP v0.8 mapping |
|---|---|
| `MoveForDuration(direction, amount, unit)` | `movement.drive` with `duration` + `duration_unit` |
| `MoveWithSteeringForDuration(steering, amount, unit)` | `movement.drive` with `steering` + `duration` |
| `StartMovingAtSpeed(leftSpeed, rightSpeed)` | `movement.drive` with `left_speed` + `right_speed` *(v0.7 §6.1 tank drive)* — bridge declares `tank_drive` capability flag |
| `SetMotorRotationDistance(distance)` | Client-side config affecting subsequent `movement.drive` `duration` math when `duration_unit:"rotations"` |
| `SetMovementBrakeAtStop(mode)` | Client-side config sticking the `stop_action` parameter onto subsequent `movement.stop` calls |
| `SetMovementAcceleration(rate)` | `movement.set_acceleration` with `rate:<ms>` *(v0.8 §6.1)* — applies to the configured motor pair |

### 3.3 `LegoSpikeLight` expansion (display port)

| Block | SSP v0.7 mapping |
|---|---|
| `SetPixel(x, y, brightness)` | `led.matrix.pixel` |
| `ShowImage(name)` | `led.matrix.image` |
| `ShowText(text, scroll)` | `led.matrix.text` |
| `ClearMatrix()` | `led.matrix.clear` |
| `TurnOnLightMatrixForSeconds(image, seconds)` | `led.matrix.image` then `led.matrix.clear` after delay |
| `SetLightMatrixBrightness(level)` | `led.matrix.brightness` |
| `RotateLightMatrix(rotation)` / `SetLightMatrixOrientation(orientation)` | `led.matrix.orientation` |
| Light matrix animation (multiple pixels per frame) | `cmd:"batch"` with `atomic:true` per frame — bridge adds `supports_batch: true` to capability |
| `LightUpDistanceSensor(topLeft, topRight, bottomLeft, bottomRight)` | Distance sensor as a `display` port (`width:2, height:2, depth:grayscale`) per *v0.7 §5.2.1*. Bridge declares it as a separate port; use `led.matrix.pixel` targeting that port id. |
| 3×3 Color Matrix accessory blocks | New component `LegoSpikeColorMatrix`. Accessory advertises as `display` port (`width:3, height:3, depth:rgb`) per *v0.7 §5.2.1*. Use `led.matrix.pixel` with `color:[r,g,b]`. Only enumerated when the accessory is physically connected. |
| `WhenLightMatrixTapped` → `LightMatrixTapped(x, y)` event | `sensor.subscribe` on `display` port with `type:"touch"` *(v0.7 `touch` feature)*. Event payload: `{"event":"sensor","port":"display","type":"touch","value":{"x":2,"y":3}}` |

**Acceptance criteria additional:** light matrix animation perf benchmark — 25 pixels updated at 10 Hz, single `batch` command per frame, no payload drops.

### 3.4 `LegoSpikeSensors` expansion (IMU + threshold events)

**IMU / orientation:**

| Block | SSP v0.7 mapping |
|---|---|
| `GetHubAcceleration()` | `sensor.read` on `imu` port (`acceleration` feature) |
| `GetHubAngularVelocity()` | `sensor.read` on `imu` port (`angular_velocity` feature) *(v0.7 §5.1)* — returns `{x, y, z}` |
| `GetHubOrientation()` | Aggregates pitch/roll/yaw client-side |
| `GetHubFaceOrientation()` | `sensor.read` on `imu` port (`face_orientation` feature) *(v0.7 §5.1)* — enum value: `face_up`/`face_down`/`port_a_up`/`port_a_down`/`port_e_up`/`port_e_down` |
| `GetGesture()` | Last cached gesture event value |
| `WhenGesture(type)` | Gesture event from `imu` port; dropdown values populated from v0.7 capability constraint enum |
| `WhenHubShaken` | Convenience block — `WhenGesture("shake")` |
| `WhenFaceOrientationChanges(orientation)` | `sensor.subscribe` on `imu` with `type:"face_orientation"`, `mode:"on_change"` |
| `ResetYaw()` | `orientation.reset_yaw` *(v0.7 §6.4)* — new `orientation` command category |
| `SetYaw(degrees)` | `orientation.set_yaw` with `angle:<degrees>` *(v0.7 §6.4)* |
| `SetHubSensorOrientation(face)` | `orientation.set_reference` with `face:"<face>"` *(v0.7 §6.4)* |

**Port-attached sensors (color / distance / force):**

| Block | SSP v0.7 mapping |
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

| Block | SSP v0.8 mapping |
|---|---|
| `Beep(freq, duration)` | `sound.beep` |
| `PlayBeepForSeconds(pitch, seconds)` | `sound.beep` with `duration` |
| `StartPlayingBeep(freq)` | `sound.beep` with no duration — runs indefinitely per v0.8 §6.1 omitted-duration clarification |
| `StopAllSounds()` | `sound.stop` |
| `SetVolume(level)` | `sound.set_volume` — bridge adds `volume` feature on speaker port |
| `GetVolume()` | `sound.read metric:"volume"` *(v0.7 §6.3)* — returns current volume level |
| `PlayBuiltin(name)` / `StartSound(name)` | `sound.play` with `sound` field, `wait:false` (default non-blocking) *(v0.8 §6.3)* — dropdown populated from `builtin_sounds` capability array |
| `PlaySoundUntilDone(name)` | `sound.play` with `sound` field and `wait:true` *(v0.8 §6.3)* — client waits for `{"event":"sound_complete"}` correlated by `request_id`. Bridge declares `"sound_wait_supported": true` in capability |

**Open question:** SPIKE FW 3.x `hub.sound` API surface — needs verification of which built-in sounds exist and whether `play_sound` supports a wait-for-completion mode.

### 3.6 `LegoSpikeMusic` *(new component, conditional on `midi` feature)*

Component ships if and only if the bridge declares `"midi"` in its `speaker` features. SPIKE Prime's `hub.sound` API on FW 3.x must be verified — if it supports `notes:` strings, full Music component ships; otherwise the component is omitted and users compose via `LegoSpikeSound.Beep()` directly.

v0.8 §6.3.1 defines the canonical note string format: `<note>[<accidental>]<octave>:<duration>` (e.g. `"C4:200 E4:200 G4:400"`), rests as `R:<duration>`, chords as `[C4 E4 G4]:<duration>`.

| Block | SSP v0.8 mapping |
|---|---|
| `PlayNote(note, duration)` | `sound.play` with `notes:` field containing single note string per v0.8 §6.3.1 format |
| `PlayChord(notes, duration)` | `sound.play` with `notes:` field using `[N1 N2 N3]:duration` chord syntax |
| `PlayDrum(name)` | `sound.play` with drum name from capability `drums` enum *(v0.8 standard names: kick/snare/hi_hat_closed/hi_hat_open/crash/ride/tom_low/tom_mid/tom_high/clap/cowbell)* |
| `Rest(duration)` | `sound.play` with `notes:"R:<duration>"` (single rest token) |
| `SetInstrument(name)` | Client-side state; included as `instrument:` field in subsequent `sound.play notes` calls. Dropdown populated from capability `instruments` enum |
| `SetTempo(bpm)` / `ChangeTempo(delta)` / `GetTempo()` | Client-side state, applied as `tempo:` field in `sound.play` |
| `PlayMusicalPhrase(notes)` | Direct passthrough of v0.8 `notes:` string format — advanced users compose with full syntax |

**FW verification still required:** does SPIKE FW 3.x `hub.sound.play()` accept the v0.8 `notes:` string format directly, or does the hub-side Python need to parse and synthesise notes via individual frequency beeps? If the latter, Music ships but with degraded quality (no envelope/instrument fidelity).

### 3.7 `LegoSpikeSystem` *(new component)*

| Block | SSP v0.7 mapping |
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

### 3.10 v0.7 + v0.8 wishlists — STATUS: ✅ ALL INTEGRATED

**v0.7 wishlist** (solaria-hub #8 → [SSP v0.7](https://github.com/edcheng1010/solaria-hub/blob/main/spec/SSP-v0.7.md)):

| Wishlist item | v0.7 resolution |
|---|---|
| `face_orientation` feature on `orientation` port | ✅ §5.1 + enum constraint in §5 capability example |
| `angular_velocity` feature on `orientation` port | ✅ §5.1 |
| `orientation.set_yaw` / `orientation.reset_yaw` commands | ✅ §6.4 (new `orientation` category) |
| `orientation.set_reference` for hub mounting | ✅ §6.4 |
| `touch` feature on `display` port (matrix tap) | ✅ §5.1 |
| Sensor-attached LED displays + 3×3 Color Matrix | ✅ §5.2.1 explicit examples |
| Movement `left_speed`/`right_speed` tank drive | ✅ §6.1 |
| `sound.read` for volume getter | ✅ §6.3 |
| `motor.run mode:"power"` | ✅ §6.1 + `power` feature in §5.1 |

**v0.8 wishlist** (solaria-hub #9 → [SSP v0.8](https://github.com/edcheng1010/solaria-hub/blob/main/spec/SSP-v0.8.md)):

| Wishlist item | v0.8 resolution |
|---|---|
| `motor.set_acceleration` + `movement.set_acceleration` | ✅ §6.1 + `acceleration` feature in §5.1 |
| `sound.play wait:true` + `sound_complete` event | ✅ §6.3 + `sound_wait_supported` capability flag |
| MIDI canonical schema (notes/chords/instruments/drums) | ✅ §6.3.1 with `<note>[<accidental>]<octave>:<duration>` format |
| `motor.goto mode:"relative"` | ✅ §6.1 + `goto_modes` array in capability |
| `motor.run` indefinite-duration clarification | ✅ §6.1 (omitting `duration` = indefinite; `duration:0` = stop) |

**Nothing still open** for the SPIKE Prime bridge after v0.8. Phase 3 can ship 100% canonical with zero `x_` extensions.

### 3.11 Hub-side Python (`hub_controller.py`) — v0.6 → v0.8 bump tasks

Bumping the bridge from v0.6 to v0.8 is part of Phase 3:

**Capability declaration:**
- **3.11.1** Update declaration shape:
  - `ssp_version: "0.8"`
  - Motor ports: add `power`, `acceleration`, `goto_modes` to features; declare `goto_modes: ["absolute", "relative"]`
  - `imu` port: add `face_orientation` (with enum constraint) and `angular_velocity` to features
  - `display` port: add `touch` to features (verify SPIKE FW exposes it)
  - `movement`: add `tank_drive: true` top-level capability flag
  - `speaker` port: add `sound_wait_supported: true`; declare `instruments` and `drums` enums if MIDI is supported by SPIKE FW
  - Distance-sensor display port: enumerate when distance sensor present (`width:2, height:2, depth:grayscale`)
  - 3×3 Color Matrix display port: enumerate when accessory present (`width:3, height:3, depth:rgb`)

**New v0.7 commands:**
- **3.11.2** `orientation.set_yaw` with `angle` param
- **3.11.3** `orientation.reset_yaw`
- **3.11.4** `orientation.set_reference` with `face` param
- **3.11.5** `motor.run mode:"power"` (raw duty cycle, scaled differently from speed mode)
- **3.11.6** `movement.drive left_speed`/`right_speed` branch (tank drive)
- **3.11.7** `sound.read` handler returning cached volume
- **3.11.8** `sensor.read`/`sensor.subscribe` dispatch for `face_orientation` and `angular_velocity` on `imu` port
- **3.11.9** `touch` event emitter for the light matrix (only if FW 3.x exposes tap events)
- **3.11.10** ✅ `led.matrix.pixel` routing for sensor-attached display ports — implemented as `led.distance` command dispatching to `distance_sensor.show(port, [tl,tr,bl,br])`. Exposed as `LightUpDistanceSensor(tl,tr,bl,br)` in LegoSpikeSensors using `DistanceSensorPort`. 3×3 color matrix accessory deferred as future bonus feature.

**New v0.8 commands:**
- **3.11.11** `motor.set_acceleration` with `rate` param (ms to ramp 0→100% speed)
- **3.11.12** `movement.set_acceleration` with `rate` param
- **3.11.13** `motor.goto mode:"relative"` — advance by N degrees from current position (vs absolute default)
- **3.11.14** `sound.play wait:true` semantics — emit `{"event":"sound_complete","request_id":<id>}` when playback finishes
- **3.11.15** `sound.play` with `notes:` field — parse v0.8 §6.3.1 format (single notes, rests, chords, instrument switching). Verify SPIKE FW supports MIDI playback natively; if not, fall back to frequency-from-note-name beep synthesis
- **3.11.16** Explicit `motor.run` indefinite-duration handling — omitting `duration` runs forever; `duration:0` or negative immediately stops

---

## Phase 4 — Client/bridge architectural split + multi-frontend (PR 2)

**Goal (two-fold):**
1. **Vertical split** — extract the SSP client/transport logic out of the App Inventor Java code so the
   extension becomes a thin wrapper over a reusable, language-agnostic bridge.
2. **Horizontal reach** — that same bridge contract then powers **non-Android frontends**: a browser-based
   **Scratch** extension first (widest classroom reach, zero-install via Web Bluetooth), then a **Python**
   library, then a **Web/JS** API. The hub-side `hub_controller.py` (SSP v0.8) is already frontend-agnostic
   and stays the single source of truth for the hub side.

**Approach (Option A — spec-first):** formalise an *SSP client contract* (what any bridge must do:
discovery, COBS framing, program upload, heartbeat, capability handshake), then provide one reference
implementation per platform rather than one runtime ported everywhere. Block/command naming stays
consistent with the LEGO SPIKE naming established in Phase 3 across all frontends.

**Effort:** 4a (bridge extraction + Scratch) ~2–3 weeks; 4b (Python) ~1 week; 4c (Web/JS) mostly falls
out of 4b's TypeScript core.

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
  - `profileMetadata()` — returns the v0.8 §2.1 profile JSON
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
- Bridge version skew — client speaks one SSP version, bridge speaks another: every client (Android,
  Scratch, Python, Web) must read `ssp_version` in the capability handshake and degrade gracefully

### 4.6 Multi-frontend rollout (Scratch → Python → Web)

The SSP client contract (4.1) lets the same hub + protocol serve frontends beyond App Inventor.
Priority order chosen for classroom reach and validation value: **Scratch first** (largest install base,
zero-install via Web Bluetooth in Chrome), then **Python**, then **Web/JS** (mostly free once the JS core exists).

**Prerequisite — 4.6.0 Client contract spec.** Add `spec/SSP-CLIENT-v0.8.md` to solaria-hub defining what
any bridge MUST implement, independent of language: BLE discovery filter (FD02 service), COBS+XOR framing,
TunnelMessage 0x32 wrapping, program upload (ClearSlot → StartFileUpload → TransferChunk → ProgramFlow),
capability handshake (`ssp_version` negotiation), heartbeat (`system.ping` ≤5s), and the JSON command/event
envelope. The Android implementation from 4.1–4.2 is the reference.

**4.6a — Scratch extension** (`solaria-scratch-spike-prime`)
- **4.6a.1** `solaria-bridge-js` — TypeScript client implementing the 4.6.0 contract over the Web Bluetooth
  API (browser-native; no Scratch Link needed for BLE on Chrome/Edge). Lives in `solaria-lib-spike-prime/web/`.
- **4.6a.2** Scratch 3.0 extension wrapping `solaria-bridge-js`; block set mirrors our 8 App Inventor
  components 1:1 where Scratch's block grammar allows (same LEGO-aligned names). Reporter blocks for
  sensor reads, hat/`when` blocks map to our subscription events.
- **4.6a.3** Host as an unofficial Scratch extension (loadable via URL / TurboWarp) — document the
  Web Bluetooth browser requirement (Chrome/Edge; not Safari/Firefox).
- **4.6a.4** Parity test: run the Phase 3 hardware checklist (motors, light, sensors, sound, music) through
  the Scratch blocks against a real hub.

**4.6b — Python library** (`pip install solaria-spike`)
- **4.6b.1** `solaria-lib-spike-prime/python/` — client implementing the 4.6.0 contract over `bleak`
  (cross-platform Python BLE). Async API mirroring the SSP command set.
- **4.6b.2** Thin sync wrapper + examples for classroom/Raspberry-Pi use.
- **4.6b.3** Publish to PyPI; parity test against the Phase 3 checklist.

**4.6c — Web/JS API** (bonus)
- **4.6c.1** Promote `solaria-bridge-js` (from 4.6a.1) to a standalone documented npm package
  (`@solaria/spike-prime`) usable directly in web apps, not just Scratch.
- **4.6c.2** Minimal demo page (connect, drive a motor, read a sensor) as living documentation.

**Cross-frontend invariants:**
- One hub program (`hub_controller.py`, SSP v0.8) serves all frontends unchanged.
- Block/command names stay consistent across frontends (the Phase 3 LEGO-aligned naming is canonical).
- Each frontend negotiates `ssp_version` and fails gracefully on mismatch.

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
| v0.7 | #8 | ✅ Integrated | `face_orientation`, `angular_velocity`, `orientation.*` command category, display `touch`, sensor-attached LED displays, tank drive, `sound.read`, motor power mode |
| v0.8 | #9 | ✅ Integrated | `motor.set_acceleration` + `movement.set_acceleration`, `sound.play wait:true` + `sound_complete` event, MIDI canonical schema (§6.3.1), `motor.goto mode:"relative"`, indefinite-`duration` clarification |
| v0.9+ | TBD | 📝 Future | DFU, stream multiplexing, auth — not needed for SPIKE Prime bridge |

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

Every block listed in that memory is mapped to a Phase-3 section in §3.1–§3.7 above. The original memory groups blocks by component; this plan groups them the same way and adds the v0.8 SSP mapping for each.

### From the PR 1 + PR 2 plans drafted in this session

| Original PR | Now in this plan |
|---|---|
| PR 1 — SSP-compatible hub-side Python + Java JSON emit | Phase 2 |
| PR 2 — full client/bridge separation + SSP proposals | Phase 4 (with Phase 5 as the payoff) |

---

## Open questions (active)

These remain unresolved and should be answered before / during the relevant phase:

1. **SPIKE Prime FW 3.x sound API surface** — Which built-in sounds exist on the hub? Spec semantics are now clear (v0.8 §6.3 `wait:true` + `sound_complete`); what remains is verifying which named sounds the hub firmware actually has so they can be enumerated in capability `builtin_sounds`. (affects §3.5)
2. **SPIKE Prime FW 3.x MIDI support** — Does `hub.sound` accept note sequences directly, or must the hub-side Python parse the v0.8 §6.3.1 `notes:` string into individual frequency beeps? Affects whether `LegoSpikeMusic` ships with full instrument fidelity or beep-fallback quality. (affects §3.6)
3. **Minimum reliable subscription interval over SPIKE BLE** — Memory says ~50 ms; needs benchmark with actual sensor load (affects §2.1 constraint declaration)
4. ~~**`motor.run` indefinite-duration encoding**~~ — RESOLVED in v0.8 §6.1 (omitting `duration` = indefinite; `duration:0` or negative = immediate stop)
5. ~~**`motor.goto` relative vs absolute**~~ — RESOLVED in v0.8 §6.1 (`mode:"absolute"|"relative"` + `goto_modes` capability array)
6. ~~**`movement.drive` left/right speeds**~~ — RESOLVED in v0.7 §6.1 (`left_speed` / `right_speed` parameters)
7. **SPIKE Prime 5×5 matrix max simultaneous-update rate** — for §3.3 light matrix animation benchmark
8. **`@Options` enum vs capability-driven dropdowns** — Phase 4 may need real designer-side investigation of App Inventor extension UI capabilities
9. **SPIKE FW 3.x light matrix touch detection** — does the hardware/firmware expose tap events on the 5×5 grid? Affects whether `touch` feature can be declared on the `display` port (§3.3 `WhenLightMatrixTapped`)
10. **SPIKE FW 3.x motor acceleration ramping** — does `motor.run` already support hardware-level acceleration ramps, or does our Python need to implement client-side stepped velocity? Affects §3.1 `SetMotorAcceleration` fidelity

---

## Instructions for whoever is implementing this

1. Read this document and `ARCHITECTURE.md` before starting any task.
2. Within a phase, tasks must be completed in section order. Within §2.3 (component migration), Connectivity must land first — other components depend on `CapabilityStore` integration.
3. Do not mark a phase complete until all acceptance criteria are verified on a physical hub.
4. v0.7 wishlist already filed and integrated (solaria-hub #8). Open a v0.8 wishlist only if Phase 3 implementation surfaces concrete needs for motor acceleration, MIDI semantics, or sound-completion events — don't file speculatively.
5. Commit messages: plain, no Co-Authored-By trailer (project owner preference).
7. Do not push to remote without explicit per-commit approval from the project owner.
8. Phase 2 perf gate (§2.5.5) is a hard merge blocker — if JSON drops payloads, switch to binary encoding (v0.6 §3.2) before merging.
