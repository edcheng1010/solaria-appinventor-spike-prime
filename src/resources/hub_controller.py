# LEGO SPIKE Prime 3.x hub controller — production build.
# Receives TunnelMessage payloads and drives motors on ports A-F.
# Command format — one or more 5-char chunks: {port A-F}{+ or -}{NNN}
# Example: "A+050B-030" → port A +500 deg/s, port B -300 deg/s (NNN × 10).
from hub import light_matrix, port
import hub
import motor

# Light centre pixel to confirm program started successfully.
light_matrix.set_pixel(2, 2, 100)

tunnel = hub.config['module_tunnel']

PORTS = {'A': port.A, 'B': port.B, 'C': port.C, 'D': port.D, 'E': port.E, 'F': port.F}


def on_message(data):
    if not isinstance(data, str):
        data = ''.join(chr(b) for b in data)
    i = 0
    while i + 5 <= len(data):
        p, s, n = data[i], data[i + 1], data[i + 2:i + 5]
        if p in PORTS and s in ('+', '-') and n.isdigit():
            motor.run(PORTS[p], int(s + n) * 10)
        i += 5
    tunnel.send(b'rdy')


tunnel.callback(on_message)
tunnel.send(b'rdy')   # signal readiness on program start

while True:
    pass
