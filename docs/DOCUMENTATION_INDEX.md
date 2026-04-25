# Comprehensive Documentation Index

**Date:** April 25, 2026
**Prepared for:** Edward, MIT Hong Kong Innovation Node

This document catalogs ALL official and unofficial LEGO and App Inventor documentation sources collected across all 18 task sessions (including the current one). It serves as the definitive reference guide for the SPIKE Prime App Inventor Extension project.

## 1. Official LEGO Documentation & Repositories

These are the authoritative sources for the SPIKE Prime 3.x BLE protocol and Python API.

- **[SPIKE Prime Protocol Documentation](https://lego.github.io/spike-prime-docs/)**
  The official specification for the SPIKE Prime 3.x BLE protocol, detailing the COBS encoding, message framing, and UUIDs.
- **[SPIKE Prime Connection Setup](https://lego.github.io/spike-prime-docs/connect.html)**
  Official guide on establishing the BLE connection and the required handshake (InfoRequest).
- **[SPIKE Prime Messages Reference](https://lego.github.io/spike-prime-docs/messages.html)**
  Detailed breakdown of all binary message types (0x00 to 0x47) used in the protocol.
- **[LEGO Official Python Examples](https://github.com/LEGO/spike-prime-docs/tree/main/examples/python)**
  The official reference implementation in Python, demonstrating how to connect, encode/decode COBS, and upload programs.
- **[LEGO Official messages.py](https://github.com/LEGO/spike-prime-docs/blob/main/examples/python/messages.py)**
  Specific reference file showing how to serialize and deserialize the binary messages.
- **[LEGO GitHub Issue #3 (SteffenLEGO)](https://github.com/LEGO/spike-prime-docs/issues/3)**
  Critical confirmation from a LEGO developer that direct motor control is not supported via BLE commands, and that the TunnelMessage (0x32) approach must be used.
- **[MINDSTORMS Robot Inventor Hub API (BT_VCP)](https://lego.github.io/MINDSTORMS-Robot-Inventor-hub-API/class_bt_vcp.html)**
  Documentation for the older Classic Bluetooth SPP approach (used in the Korean FUNERS video), which is NOT compatible with SPIKE Prime 3.x firmware.
- **[SPIKE Essential Python Help](https://spike.legoeducation.com/essential/help/lls-help-python)**
  Reference for the older LEGO Wireless Protocol 3.0, which is fundamentally different from the SPIKE Prime protocol.

## 2. Official MIT App Inventor Documentation & Repositories

These sources guide the development of the extension itself.

- **[App Inventor Extension Development Guide](https://appinventor.mit.edu/explore/ai2/create-extensions.html)**
  The official starting point for building App Inventor extensions.
- **[App Inventor Extensions Repository](https://github.com/mit-cml/appinventor-extensions)**
  The official source code for MIT-maintained extensions.
- **[App Inventor BluetoothLE Extension Source](https://github.com/mit-cml/appinventor-extensions/tree/extension/bluetoothle)**
  Critical reference for understanding how App Inventor handles BLE connections, scanning, and characteristics.
- **[App Inventor Micro:bit Extension Source](https://github.com/mit-cml/appinventor-extensions/tree/extension/microbit)**
  Reference used for architectural decisions regarding component structure and event handling.
- **[App Inventor BluetoothLE Reference](https://iot.appinventor.mit.edu/iot/reference/bluetoothle)**
  Documentation for the blocks and methods available in the standard BLE extension.

## 3. Community & Forum Discussions

Insights from the developer community regarding SPIKE Prime and App Inventor.

- **[App Inventor Community: LEGO SPIKE Prime Extension Request](https://community.appinventor.mit.edu/t/lego-spike-prime-extension/146410)**
  A thread with 1,500 views demonstrating massive community demand for this exact project, confirming no existing solution exists.
- **[App Inventor Community: Extension Development Category](https://community.appinventor.mit.edu/t/about-the-extension-development-category/1938)**
  General guidelines and help for extension developers.
- **[App Inventor Community: BluetoothLE Updates 2024](https://community.appinventor.mit.edu/t/bluetoothle-updates-2024/134483)**
  Important context on recent changes to the BLE extension that affect our implementation.
- **[Facebook SPIKE Community: Connecting to App Inventor](https://www.facebook.com/groups/SPIKEcommunity/posts/2099543697090518/)**
  Discussion highlighting the Korean FUNERS video and the community's inability to replicate it on modern firmware.
- **[Reddit FLL: RemoteBrick Announcement](https://www.reddit.com/r/FLL/comments/1p0lvuz/remotebrick_the_first_java_library_for_the/)**
  Discussion about the RemoteBrick Java library, confirming it uses Classic Bluetooth (SPP) and is Windows-only.

## 4. Third-Party GitHub Repositories & Open Source Projects

Working code and experiments from other developers trying to solve the same problem.

- **[etomasfe/SpikeRemoteControl](https://github.com/etomasfe/SpikeRemoteControl)**
  **CRITICAL:** The only known working implementation of the SPIKE Prime 3.x protocol (HTML+JS). Proves the TunnelMessage architecture works.
- **[gpdaniels/spike-prime](https://github.com/gpdaniels/spike-prime)**
  Older Android controller implementation. Analysis confirmed it uses Classic Bluetooth SPP, not BLE.
- **[gpdaniels bluetooth.java](https://github.com/gpdaniels/spike-prime/blob/master/controller/controller-android/source/com/gpdaniels/controller/service/bluetooth.java)**
  Specific file showing the use of `createInsecureRfcommSocketToServiceRecord`, proving it's not BLE.
- **[JuniorJacki/RemoteBrick](https://github.com/JuniorJacki/RemoteBrick)**
  A Java library for controlling the hub. Analysis confirmed it is Windows-only and uses Classic Bluetooth (SPP).
- **[tuftsceeo/SPIKEPythonDocs](https://github.com/tuftsceeo/SPIKEPythonDocs)**
  Comprehensive documentation of the hub-side Python API.
- **[tuftsceeo/SPIKE-Web-Interface](https://github.com/tuftsceeo/SPIKE-Web-Interface)**
  Web interface experiments from Tufts University.
- **[GO-Robot-FLL/Python-for-Spike-Prime](https://github.com/GO-Robot-FLL/Python-for-Spike-Prime)**
  Python scripts and documentation for FLL teams.
- **[GianCann/SpikePrimeHub](https://github.com/GianCann/SpikePrimeHub)**
  Another Python-based interaction library.
- **[faisaltameesh/spikerc](https://github.com/faisaltameesh/spikerc)**
  Remote control experiments.
- **[gabrielsessions/pyrepl-js](https://github.com/gabrielsessions/pyrepl-js)**
  JavaScript REPL interface for MicroPython devices.
- **[sanjayseshan/spikeprime-tools](https://github.com/sanjayseshan/spikeprime-tools)**
  Tools for SPIKE Prime development.
- **[sanjayseshan/spikeprime-vscode](https://github.com/sanjayseshan/spikeprime-vscode)**
  VS Code extension for SPIKE Prime.
- **[bricklife/LEGO SPIKE Prime JSON command examples](https://gist.github.com/bricklife/13c7fe07c3145dd94f4f23d20ccf5a79)**
  Examples of the older 2.x JSON-RPC protocol.
- **[dctian/lego-spike-prime-py](https://github.com/dctian/lego-spike-prime-py)**
  Python library for SPIKE Prime.
- **[pybricks/technical-info assigned-numbers.md](https://github.com/pybricks/technical-info/blob/master/assigned-numbers.md)**
  Reference for LEGO device IDs and UUIDs.

## 5. Other Resources, Articles & Videos

- **[Tufts CEEO SPIKE 3 Python API Reference](https://tuftsceeo.github.io/SPIKEPythonDocs/SPIKE3.html)**
  **CRITICAL:** Proves that SPIKE 3.x firmware has no `bt_vcp` module, confirming the Korean video approach is obsolete.
- **[Anton's Mindstorms: Remote Control Android App](https://www.antonsmindstorms.com/2026/01/09/how-to-remote-control-your-mindstorms-hub-with-an-android-app-in-python/)**
  Analysis showed this requires custom firmware (`mpy_robot_tools`) and only works on SPIKE 2.0.
- **[YouTube: Korean FUNERS Group App Inventor Demo 1](https://www.youtube.com/watch?v=0QyTuA4AUjg)**
  Video showing App Inventor controlling SPIKE Prime. Analysis proved it uses the obsolete `BT_VCP` Classic Bluetooth approach on 2.x firmware.
- **[YouTube: Korean FUNERS Group App Inventor Demo 2](https://www.youtube.com/watch?v=rQm1uk4JV2E)**
  Second demonstration video from the same group.
- **[Chrome Developer Blog: LEGO Education SPIKE Web Bluetooth](https://developer.chrome.com/blog/lego-education-spike-web-bluetooth-web-serial)**
  Article discussing Web Bluetooth integration with SPIKE.
- **[dwalton76/spikedev](https://dwalton76.github.io/spikedev/index.html)**
  Documentation for the spikedev Python library.
- **[PeterStaev/lego-spikeprime-mindstorms-vscode](https://github.com/PeterStaev/lego-spikeprime-mindstorms-vscode)**
  VS Code extension documentation.
- **[Android Studio Command Line Tools](https://developer.android.com/studio#command-tools)**
  Reference used for setting up the compilation environment.
