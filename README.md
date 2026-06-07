<p align="center">
  <img src="assets/logo.png" alt="Solaria Bridge — LEGO SPIKE Prime" width="200" />
</p>

# LEGO SPIKE Prime — App Inventor BLE Extension

A multi-component MIT App Inventor extension that enables real-time Bluetooth Low Energy communication with LEGO® SPIKE™ Prime hubs. Control motors, read sensors, drive the LED matrix, and play sounds — all from App Inventor blocks on your phone or tablet.

This extension is the **App Inventor client** in the [Solaria](https://github.com/edcheng1010/solaria-hub) open-source robotics ecosystem. It implements the [Solaria Standard Protocol (SSP)](https://github.com/edcheng1010/solaria-hub/blob/main/spec/SSP-v0.8.md), which means the same robot capabilities — motor control, sensor reading, real-time feedback — are available across every Solaria-supported platform. Student code in App Inventor is App Inventor-specific (stateful, component-based blocks); the capabilities it exposes are consistent with what students using Scratch or other Solaria clients can do on the same hardware.

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

> Part of the [Solaria](https://github.com/edcheng1010/solaria-hub) open-source robotics platform.

> **Unofficial integration.** This is an independent open-source project. It is **not affiliated with, endorsed by, or sponsored by the LEGO Group or the Massachusetts Institute of Technology.** "LEGO", "SPIKE Prime", and "App Inventor" are trademarks of their respective owners; references in this project are nominative — used solely to describe hardware compatibility. See [NOTICE](NOTICE) for full trademark and licensing notices.

---

## Features

- **BLE scanning with ghost-device filtering** — RSSI staleness detection ensures only powered-on hubs appear in the device list
- **Multi-hub classroom support** — designed for environments with many hubs operating simultaneously
- **SPIKE Prime 3.x protocol** — uses the correct TunnelMessage architecture (program upload + real-time commands)
- **COBS encoding** — full implementation of LEGO's custom framing protocol
- **Modular component design** — 8 components: Connectivity, Motors, Movement, Light, Sensors, Sound, System, and Music

## Components

| Component | Purpose |
| --- | --- |
| `LegoSpikeConnectivity` | BLE scanning, connection, hub management |
| `LegoSpikeMotors` | Individual motor control (speed, position, stall detection) |
| `LegoSpikeMovement` | Coordinated drive base movement |
| `LegoSpikeLight` | LED matrix and status light control |
| `LegoSpikeSensors` | Color, distance, force, and tilt sensor readings |
| `LegoSpikeSound` | Hub speaker beeps and tone control |
| `LegoSpikeSystem` | Battery, temperature, charging, and RSSI reads |
| `LegoSpikeMusic` | Note sequences and musical playback |

## Requirements

- Android device with BLE support (API 18+)
- [MIT App Inventor](https://ai2.appinventor.mit.edu)
- MIT App Inventor BluetoothLE extension (`.aix`) — must be added to your project separately
- LEGO SPIKE Prime hub with firmware 3.x

## Quick Start

1. Download the latest `.aix` release from [Releases](../../releases).
2. In App Inventor, go to **Extensions → Import Extension** → upload the `.aix` file.
3. Also import the [MIT BluetoothLE extension](https://mit-cml.github.io/extensions/).
4. Wire `LegoSpikeConnectivity1.HubConnected` to initialize your other components.
5. Use `LegoSpikeConnectivity1.StartScanning` to find nearby hubs.
6. Connect, and start controlling your robot!

## Demo

See the extension in action:

- **Demo video** — watch a SPIKE Prime hub being controlled live from App Inventor
- **Sample project (.aia)** — import directly into App Inventor to get started immediately

Both are available on the [v0.1.0 release page](https://github.com/edcheng1010/solaria-appinventor-spike-prime/releases/tag/v0.1.0).

---

## How It Works

SPIKE Prime 3.x uses a two-part communication architecture:

1. **Program Upload** — the extension uploads a lightweight Python controller program to the hub
2. **TunnelMessage** — real-time commands are sent through a bidirectional tunnel between the app and the running Python program

This is different from older LEGO hubs (Boost, SPIKE Essential) which accept direct BLE commands. See [ARCHITECTURE.md](ARCHITECTURE.md) for the full technical breakdown.

## Project Status

This extension implements [SSP v0.8](https://github.com/edcheng1010/solaria-hub/blob/main/spec/SSP-v0.8.md) — the Solaria Standard Protocol — for full bidirectional communication with the hub. Gen 1 is complete (full feature set, 103 hardware tests passed); Gen 2 work (client/bridge split and multi-hub support) is next. Current state:

- [x] BLE scanning and connection (stable)
- [x] Ghost device filtering via RSSI staleness
- [x] COBS encoding/decoding
- [x] Program upload protocol (ClearSlot → StartFileUpload → TransferChunk → ProgramFlow)
- [x] TunnelMessage send/receive
- [x] Full motor control API (phase 2 complete)
- [x] Sensor streaming (phase 2 complete)
- [x] LED matrix patterns (phase 2 complete)
- [x] Sound, System, and Music components (phase 3 complete — 103 hardware tests passed)
- [ ] Multi-hub simultaneous control (planned)

## Building from Source

Prerequisites:

- Java JDK 8+
- Apache Ant
- App Inventor source tree (for extension compilation)

```bash
ant build
```

See [docs/COMPILATION_AND_DEBUGGING.md](docs/COMPILATION_AND_DEBUGGING.md) for detailed build instructions.

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting a PR.

If you're interested in building extensions for other client platforms or firmware for other hardware, see the [Solaria ecosystem hub](https://github.com/edcheng1010/solaria-hub) for the full roadmap, architecture, and contribution guide. Solaria supports multiple hardware platforms (SPIKE Prime, ESP32, StackChan, and more) and multiple programming environments (App Inventor, Scratch/TurboWarp, and more) — all sharing the same robot capabilities through SSP.

---
**Trademark Notice:** LEGO® and SPIKE™ are trademarks of the LEGO Group. App Inventor is a trademark of MIT. This project is not affiliated with or endorsed by any trademark holder. See [NOTICE](./NOTICE) for details.

## License

Apache License 2.0 — see [LICENSE](LICENSE) for the full text.

Copyright © 2026 Edward Cheng

## Acknowledgements

- [LEGO SPIKE Prime Protocol Documentation](https://lego.github.io/spike-prime-docs/)
- [etomasfe/SpikeRemoteControl](https://github.com/etomasfe/SpikeRemoteControl) — WebBluetooth reference implementation
- [MIT App Inventor](https://appinventor.mit.edu/) — the block-based programming platform
