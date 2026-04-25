# LEGO SPIKE Prime App Inventor Extension - Documentation Index
## Date: April 25, 2026
## Prepared for: Edward, MIT Hong Kong Innovation Node

This document serves as a comprehensive index of all official and unofficial documentation, reference implementations, and community discussions related to the LEGO SPIKE Prime BLE protocol and App Inventor integration. It was compiled across 17+ research and development sessions.

---

## 1. Official LEGO Documentation

These are the authoritative sources for the SPIKE Prime protocol and Python API.

| Resource | Description | URL |
|----------|-------------|-----|
| **SPIKE Prime Protocol Docs** | Official documentation for the SPIKE Prime BLE protocol, including message formats and connection flow. | [lego.github.io/spike-prime-docs](https://lego.github.io/spike-prime-docs/) |
| **SPIKE Prime Python Examples** | Official Python reference implementation for connecting to SPIKE Prime via BLE, uploading programs, and sending messages. | [github.com/LEGO/spike-prime-docs/tree/main/examples/python](https://github.com/LEGO/spike-prime-docs/tree/main/examples/python) |
| **SPIKE Prime Messages** | Detailed breakdown of the binary message types (InfoRequest, TunnelMessage, etc.). | [lego.github.io/spike-prime-docs/messages.html](https://lego.github.io/spike-prime-docs/messages.html) |
| **LEGO GitHub Issue #3** | Critical confirmation from a LEGO developer (SteffenLEGO) about the required two-part architecture (program upload + TunnelMessage) for motor/LED control. | [github.com/LEGO/spike-prime-docs/issues/3](https://github.com/LEGO/spike-prime-docs/issues/3) |

---

## 2. Unofficial & Community Documentation

These sources provide additional context, alternative APIs, and community findings.

| Resource | Description | URL |
|----------|-------------|-----|
| **Tufts CEEO SPIKE 3 Python Docs** | Comprehensive documentation of the hub-side Python API for SPIKE 3.x firmware. Confirms the absence of a built-in Bluetooth module. | [tuftsceeo.github.io/SPIKEPythonDocs/SPIKE3.html](https://tuftsceeo.github.io/SPIKEPythonDocs/SPIKE3.html) |
| **bricklife JSON Commands Gist** | Examples of JSON commands used in older SPIKE 2.x firmware. Useful for historical context but obsolete for 3.x. | [gist.github.com/bricklife/13c7fe07c3145dd94f4f23d20ccf5a79](https://gist.github.com/bricklife/13c7fe07c3145dd94f4f23d20ccf5a79) |
| **MINDSTORMS Hub API (BT_VCP)** | Documentation for the `bt_vcp` module used in SPIKE 2.x and MINDSTORMS Robot Inventor. Obsolete for SPIKE 3.x. | [lego.github.io/MINDSTORMS-Robot-Inventor-hub-API/class_bt_vcp.html](https://lego.github.io/MINDSTORMS-Robot-Inventor-hub-API/class_bt_vcp.html) |

---

## 3. Reference Implementations & Competitors

These projects demonstrate various approaches to controlling LEGO hubs, highlighting the evolution of the protocols.

| Project | Description | URL |
|---------|-------------|-----|
| **etomasfe/SpikeRemoteControl** | **CRITICAL REFERENCE:** A working HTML+JS (WebBluetooth) implementation that successfully uses the SPIKE 3.x protocol (program upload + TunnelMessage). | [github.com/etomasfe/SpikeRemoteControl](https://github.com/etomasfe/SpikeRemoteControl) |
| **JuniorJacki/RemoteBrick** | A Java desktop library for controlling the Inventor Hub (51515) using Bluetooth Classic (SPP). Not applicable to our BLE/Android use case. | [github.com/JuniorJacki/RemoteBrick](https://github.com/JuniorJacki/RemoteBrick) |
| **gpdaniels/spike-prime** | An older Android controller app that uses Classic Bluetooth (SPP) and JSON-RPC. Obsolete for SPIKE 3.x. | [github.com/gpdaniels/spike-prime](https://github.com/gpdaniels/spike-prime) |
| **Anton's Mindstorms App** | An Android app for MINDSTORMS/SPIKE 2.0 that requires a custom Python library (`mpy_robot_tools`) on the hub. Does not work with SPIKE 3.x. | [antonsmindstorms.com/...](https://www.antonsmindstorms.com/2026/01/09/how-to-remote-control-your-mindstorms-hub-with-an-android-app-in-python/) |

---

## 4. Community Discussions & Videos

| Resource | Description | URL |
|----------|-------------|-----|
| **FUNERS App Inventor Video** | A demonstration by a Korean LEGO Education partner using App Inventor to control SPIKE Prime. Uses the obsolete `BT_VCP` approach (SPIKE 2.x). | [youtube.com/watch?v=0QyTuA4AUjg](https://www.youtube.com/watch?v=0QyTuA4AUjg) |
| **MIT App Inventor Community Thread** | A user asking how to control SPIKE Prime with App Inventor. The consensus was that no direct solution existed at the time. | [community.appinventor.mit.edu/t/lego-spike-prime-extension/146410](https://community.appinventor.mit.edu/t/lego-spike-prime-extension/146410) |
| **Facebook SPIKE Community Post** | A user struggling to replicate the FUNERS video approach, highlighting the confusion caused by the firmware transition from 2.x to 3.x. | [facebook.com/groups/SPIKEcommunity/posts/2099543697090518/](https://www.facebook.com/groups/SPIKEcommunity/posts/2099543697090518/) |

---

## 5. Internal Project Documentation

These documents are maintained within our repository to guide development.

| Document | Description | Location |
|----------|-------------|----------|
| **ARCHITECTURE.md** | The single source of truth for the extension's architecture, including the critical protocol correction for SPIKE 3.x. | `/ARCHITECTURE.md` |
| **CLAUDE.md** | Persistent memory and strict rules for Claude Code development. | `/CLAUDE.md` |
| **COMPETITIVE_ANALYSIS_REPORT_20260425.md** | A detailed analysis of existing solutions and our competitive advantage. | `/docs/COMPETITIVE_ANALYSIS_REPORT_20260425.md` |
| **CORRECT_SPIKE_PRIME_UUIDS.md** | Verification of the correct BLE Service and Characteristic UUIDs for SPIKE Prime. | `/docs/CORRECT_SPIKE_PRIME_UUIDS.md` |
| **CRITICAL_MOTOR_CONTROL_APPROACH.md** | Detailed explanation of the two-part architecture required for motor/LED control on SPIKE 3.x. | `/docs/CRITICAL_MOTOR_CONTROL_APPROACH.md` |
| **SPIKE_REMOTE_CONTROL_ANALYSIS.md** | Deep dive into the working `etomasfe` implementation. | `/docs/SPIKE_REMOTE_CONTROL_ANALYSIS.md` |
| **COMPILATION_AND_DEBUGGING.md** | Instructions for compiling the extension and debugging on Android. | `/docs/COMPILATION_AND_DEBUGGING.md` |
| **CLAUDE_CODE_SETUP_AND_WORKFLOW_GUIDE.md** | Guide for setting up and using Claude Code in the three-party workflow. | `/docs/CLAUDE_CODE_SETUP_AND_WORKFLOW_GUIDE.md` |
| **VERSION_VERIFICATION_REPORT.md** | Verification that all source files are the latest versions containing all fixes. | `/docs/VERSION_VERIFICATION_REPORT.md` |
