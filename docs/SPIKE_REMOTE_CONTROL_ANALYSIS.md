# Analysis: etomasfe/SpikeRemoteControl - WORKING SPIKE Prime 3.x BLE Implementation
## Date: 2026-04-25

## PROVEN WORKING ARCHITECTURE

This is a **working** HTML+JS implementation that controls SPIKE Prime motors via BLE.
It confirms the two-part architecture described by SteffenLEGO (LEGO official developer).

## Complete Communication Flow

### Step 1: BLE Connection
```
Service UUID: 0000fd02-0000-1000-8000-00805f9b34fb
RX (write to hub): 0000fd02-0001-1000-8000-00805f9b34fb
TX (read from hub): 0000fd02-0002-1000-8000-00805f9b34fb
```

### Step 2: Clear Slot 0
```
Send: 0x46 0x00 (ClearSlotRequest for slot 0)
```

### Step 3: Upload Python Controller Program
```
Send: 0x0C + "program.py\0" + slot(0x00) + CRC32 (StartFileUploadRequest)
Send: 0x10 + running_CRC32 + chunk_size + chunk_data (TransferChunkRequest) × N chunks
```

### Step 4: Start Program
```
Send: 0x1E 0x00 0x00 (ProgramFlowRequest for slot 0)
```

### Step 5: Real-Time Control via TunnelMessage
```
Send: 0x32 + payload_size(2 bytes) + command_string (TunnelMessage)
Receive: 0x32 + "rdy" (acknowledgment from hub program)
```

## COBS Encoding Constants (CONFIRMED)
```
DELIMITER = 0x02
NO_DELIMITER = 255
MAX_BLOCK_SIZE = 84
COBS_CODE_OFFSET = 2
XOR = 0x03
```

## Pack/Unpack Flow
```
Pack:   data → COBS encode → XOR each byte with 0x03 → append delimiter 0x02
Unpack: strip delimiter → XOR each byte with 0x03 → COBS decode
```

## Hub-Side Python Program (The Controller)
```python
from hub import light_matrix
import motor
from hub import port
light_matrix.set_pixel(1,1,100)  # Visual indicator that program is running
import hub
tunnel = hub.config['module_tunnel']  # KEY: Access the tunnel module

def receive_tunnel_message(data):
    if data == b"bye.bye.AB":
        quit()    
    # Parse command format: "A+050B+050" (motor port + signed power)
    n = ''
    for i in range(1, 5):
        n = n + chr(data[i])
    mA = int(n) * 10  # Power multiplied by 10
    n = ''
    for i in range(6, 10):
        n = n + chr(data[i])
    mB = int(n) * 10
    
    # Motor 1 control
    if chr(data[0]) == 'A': motor.run(port.A, mA)
    elif chr(data[0]) == 'B': motor.run(port.B, mA)
    # ... ports C-F
    
    # Motor 2 control
    if chr(data[5]) == 'A': motor.run(port.A, mB)
    elif chr(data[5]) == 'B': motor.run(port.B, mB)
    # ... ports C-F
    
    tunnel.send(b'rdy')  # Acknowledge command received

tunnel.callback(receive_tunnel_message)  # Register callback
tunnel.send(b'rdy')  # Signal ready
while True:
    pass  # Keep program running
```

## KEY API DISCOVERY: `hub.config['module_tunnel']`
This is the CRITICAL Python API for bidirectional communication:
- `tunnel = hub.config['module_tunnel']` - Access the tunnel module
- `tunnel.callback(handler_function)` - Register callback for incoming messages
- `tunnel.send(bytes)` - Send data back to the connected device
- Callback receives `data` as bytes

## Command Format Used
```
Format: "P1±NNNP2±NNN" (10 bytes)
Example: "A+050B+050" = Motor A at +50, Motor B at +50
- P1/P2: Port letter (A-F)
- ±NNN: Signed 3-digit power value (+050, -100, etc.)
- Power is multiplied by 10 on hub side (so +050 becomes 500 deg/sec)
```

## TunnelMessage Format
```
Byte 0: 0x32 (TunnelMessage type)
Byte 1-2: payload size (uint16 little-endian) - e.g., 0x0a 0x00 = 10 bytes
Byte 3+: payload data (the command string)
```

## CRITICAL IMPLICATIONS FOR OUR EXTENSION

### What We Must Implement:
1. **COBS encoding/decoding** - Matches our COBSEncoder.java (NEEDS VERIFICATION)
2. **Program upload** - NEW capability needed (clear slot → upload file → transfer chunks → start)
3. **TunnelMessage** - NEW capability needed for real-time motor/LED control
4. **Hub-side Python program** - Must be embedded in our extension as a string constant
5. **CRC32** - Standard CRC32 with polynomial 0xEDB88320 (we have SpikeCRC32.java)

### What This Means:
- Our current approach of sending direct motor commands is WRONG
- We need to upload a Python "controller" program to the hub first
- Then use TunnelMessage for real-time bidirectional communication
- The hub-side program interprets commands and controls hardware
- This is fundamentally different from LEGO Wireless Protocol 3.0 (used by SPIKE Essential)

### Our Competitive Advantage:
- etomasfe's implementation is HTML+JS (WebBluetooth) - NOT an App Inventor extension
- It's a rough proof-of-concept ("my first attempt", "code is quite large and a bit confused")
- We can create a POLISHED, user-friendly App Inventor extension
- We can support more than just motor control (LED, sensors, matrix display)
- We can provide proper error handling, reconnection, and user-friendly blocks
