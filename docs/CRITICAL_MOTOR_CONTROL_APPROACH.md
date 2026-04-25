# CRITICAL: How Motor/LED Control Actually Works on SPIKE Prime 3.x
## Source: SteffenLEGO (LEGO Official Developer) - GitHub Issue #3
## Date: 2026-04-25

## THE KEY REVELATION

**From SteffenLEGO (LEGO employee), June 7, 2024:**

> "There are no handles in the protocol to do anything with the hardware. Running motors, turning on lights.
> Instead what you have to do is transfer a micropython program (either as text or as bytecode) using the filetransfer handles in the protocol.
> I think there might be a hard dependency on the name `program.py` or `program.mpy` (depending on if you are sending python text or bytecode)
> Then you need to send a `ProgramFlowRequest` to start the program."

> "Using the python experience in the Spike app should give you a pretty good idea of how to make the hardware do things, although the app module that can be used in the app to play sounds, plot graph data and stuff like that is obviously not present, you'd have to build things like that yourself using the `TunnelMessage` which allows you to send arbitrary data back and forth between the running program and whatever app you create."

> "Essential and the Lego Wireless Protocol works really differently, in that it cannot run programs, it can just run individual commands, so for that you can send a message to make a motor run."

## WHAT THIS MEANS FOR OUR EXTENSION

### The Two-Part Architecture Required:

**Part 1: Hub-Side Python Program**
A MicroPython program must be uploaded to the SPIKE Prime hub that:
- Listens for incoming TunnelMessages
- Parses commands (e.g., "motor A power 50", "LED red 255 green 0 blue 0")
- Executes the commands using the hub's Python API
- Sends sensor data back via TunnelMessages

**Part 2: App Inventor Extension (Android Side)**
Our extension must:
1. Connect via BLE using UUID `0000fd02-...`
2. Send InfoRequest to get hub capabilities
3. Upload a "controller" Python program to the hub (program.py)
4. Start the program via ProgramFlowRequest
5. Use TunnelMessage (0x32) for real-time bidirectional communication
6. Parse DeviceNotifications (0x3C) for sensor data

### The Controller Program Pattern:
```python
# program.py - uploaded to SPIKE Prime hub
import runloop
from hub import port, light_matrix, light, motion_sensor, button
import motor

# Listen for tunnel messages and execute commands
async def main():
    while True:
        # Read incoming tunnel message
        msg = await read_tunnel_message()  # API TBD
        
        # Parse command
        if msg.startswith("MOTOR"):
            port_name, power = parse_motor_command(msg)
            motor.run(port_name, power)
        elif msg.startswith("LED"):
            r, g, b = parse_led_command(msg)
            light.color(light.POWER, (r, g, b))
        elif msg.startswith("MATRIX"):
            pattern = parse_matrix_command(msg)
            light_matrix.show(pattern)
        
        # Send sensor data back
        # send_tunnel_message(sensor_data)  # API TBD

runloop.run(main())
```

## EXISTING IMPLEMENTATION: etomasfe/SpikeRemoteControl
Someone has already built a working HTML+JS implementation:
https://github.com/etomasfe/SpikeRemoteControl.git

This should be studied as a reference for how TunnelMessage communication works.

## COMPETITIVE IMPLICATIONS

### What Competitors Do:
1. **Korean FUNERS group** - Uses SPIKE 2.x JSON-RPC (OLD protocol, won't work with SPIKE 3.x firmware)
2. **RemoteBrick** - Uses LEGO Wireless Protocol 3.0 (for SPIKE Essential, NOT SPIKE Prime)
3. **etomasfe/SpikeRemoteControl** - HTML+JS, uses correct SPIKE 3.x protocol with program upload + TunnelMessage

### Our Advantage:
- We can be the **FIRST App Inventor extension** that implements the correct SPIKE 3.x protocol
- We have the OFFICIAL reference implementation from LEGO
- We understand the two-part architecture (hub program + BLE communication)
- Our extension can provide a SEAMLESS experience: auto-upload the controller program, then provide simple blocks for motor/LED/sensor control

### Our Current Code Gap:
Our existing LegoSpikePrime.java attempts to send direct motor commands over BLE, which is WRONG for SPIKE Prime 3.x.
The correct approach requires:
1. COBS encoding (we have COBSEncoder.java - needs verification against official)
2. Program upload capability (NOT yet implemented)
3. TunnelMessage communication (NOT yet implemented)
4. Hub-side Python controller program (NOT yet created)
