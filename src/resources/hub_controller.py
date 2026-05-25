# LEGO SPIKE Prime hub controller — SSP v0.6 edition.
#
# Wire format: SSP v0.6 json-utf8-newline over TunnelMessage (opcode 0x32).
# Each incoming frame is one newline-terminated JSON string.
# Each outgoing event is one newline-terminated JSON string.
#
# This program is the TYPE 2 "bridge firmware" for the Solaria platform.
# It runs entirely on the hub via the Python TunnelMessage facility.
#
# See: https://github.com/edcheng1010/solaria-hub/blob/main/spec/SSP-v0.6.md

import hub, motor, motor_pair, time
from hub import light_matrix, port

try:
    import json
    _json_ok = True
except ImportError:
    _json_ok = False

try:
    import color_sensor, distance_sensor, force_sensor, color
    _CLR_MAP = {}
    for _n in ('BLACK', 'RED', 'GREEN', 'YELLOW', 'BLUE', 'WHITE',
               'CYAN', 'MAGENTA', 'ORANGE', 'VIOLET', 'AZURE', 'NONE'):
        try:
            _CLR_MAP[getattr(color, _n)] = _n.lower()
        except AttributeError:
            pass
    _sensors_ok = True
except Exception:
    _sensors_ok = False
    _CLR_MAP = {}

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

PORTS = {'A': port.A, 'B': port.B, 'C': port.C,
         'D': port.D, 'E': port.E, 'F': port.F}

# Status LED color name → color constant (SSP enum values)
_LED_COLORS = {
    'red': color.RED if hasattr(color, 'RED') else 9,
    'orange': color.ORANGE if hasattr(color, 'ORANGE') else 7,
    'yellow': color.YELLOW if hasattr(color, 'YELLOW') else 6,
    'green': color.GREEN if hasattr(color, 'GREEN') else 5,
    'cyan': color.CYAN if hasattr(color, 'CYAN') else 10,
    'blue': color.BLUE if hasattr(color, 'BLUE') else 3,
    'violet': color.VIOLET if hasattr(color, 'VIOLET') else 11,
    'magenta': color.MAGENTA if hasattr(color, 'MAGENTA') else 8,
    'white': color.WHITE if hasattr(color, 'WHITE') else 10,
    'off': 0,
}

# Image name → light_matrix constant
_IMG_CONST = {
    'HEART': 'IMAGE_HEART', 'HEARTSMALL': 'IMAGE_HEART_SMALL',
    'HAPPY': 'IMAGE_HAPPY', 'SMILE': 'IMAGE_SMILE', 'SAD': 'IMAGE_SAD',
    'CONFUSED': 'IMAGE_CONFUSED', 'ANGRY': 'IMAGE_ANGRY',
    'ASLEEP': 'IMAGE_ASLEEP', 'SURPRISED': 'IMAGE_SURPRISED',
    'YES': 'IMAGE_YES', 'NO': 'IMAGE_NO',
    'ARROWNORTH': 'IMAGE_ARROW_N', 'ARROWEAST': 'IMAGE_ARROW_E',
    'ARROWSOUTH': 'IMAGE_ARROW_S', 'ARROWWEST': 'IMAGE_ARROW_W',
}
_IMAGES_IDX = {
    'HEART': 0, 'HEARTSMALL': 1, 'HAPPY': 2, 'SMILE': 3, 'SAD': 4,
    'CONFUSED': 5, 'ANGRY': 6, 'ASLEEP': 7, 'SURPRISED': 8,
    'YES': 12, 'NO': 13, 'ARROWNORTH': 16, 'ARROWEAST': 18,
    'ARROWSOUTH': 20, 'ARROWWEST': 22,
}

# ---------------------------------------------------------------------------
# State
# ---------------------------------------------------------------------------

_timer_start = time.ticks_ms()
_mov_lp = None          # cached motor_pair left port (skip re-pair when same)
_mov_rp = None

# Subscriptions: port_id -> {type, mode, interval_ms, min_change, last_ms, last_val}
# Special port 'battery'/'temperature'/etc. are system metrics.
_subscriptions = {}
_sys_subscriptions = {}  # metric -> {interval_ms, last_ms, last_val}

_last_ping_ms = None     # None = heartbeat not yet started
_heartbeat_active = False

# ---------------------------------------------------------------------------
# Tunnel setup
# ---------------------------------------------------------------------------

tunnel = hub.config['module_tunnel']


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _send(obj):
    """Send a JSON event to the client."""
    try:
        tunnel.send((json.dumps(obj) + '\n').encode())
    except Exception:
        pass


def _send_error(code, message, request_id=None):
    err = {'event': 'error', 'code': code, 'message': message}
    if request_id is not None:
        err['request_id'] = request_id
    _send(err)


def _sensor_event(port_id, sensor_type, value, request_id=None):
    ev = {'event': 'sensor', 'port': port_id, 'type': sensor_type, 'value': value}
    if request_id is not None:
        ev['request_id'] = request_id
    _send(ev)


def _system_event(metric, value):
    _send({'event': 'system', 'metric': metric, 'value': value})


def _tilt_angles():
    """Returns (pitch, roll, yaw) in degrees (integer), or (0, 0, 0) on error."""
    try:
        try:
            raw = hub.motion_sensor.tilt_angles()
        except AttributeError:
            raw = hub.imu.tilt_angles()
        return (raw[0] // 10, raw[1] // 10, raw[2] // 10)
    except Exception:
        return (0, 0, 0)


def _ensure_pair(lp, rp):
    """Re-pair motor pair only when ports change."""
    global _mov_lp, _mov_rp
    if lp != _mov_lp or rp != _mov_rp:
        try:
            motor_pair.pair(motor_pair.PAIR_1, PORTS[lp], PORTS[rp])
        except Exception:
            pass
        _mov_lp, _mov_rp = lp, rp


def _show_image(name):
    n = name.upper()
    const = _IMG_CONST.get(n)
    if const is not None:
        try:
            img = getattr(light_matrix, const)
            light_matrix.show_image(img)
            return
        except AttributeError:
            pass
    idx = _IMAGES_IDX.get(n, 2)
    light_matrix.show_image(idx)


def _read_sensor_value(port_id, sensor_type):
    """Reads a sensor value. Returns None on error."""
    p = PORTS.get(port_id.upper())
    if p is None or not _sensors_ok:
        return None
    try:
        if sensor_type == 'color':
            c = color_sensor.color(p)
            return _CLR_MAP.get(c, str(c))
        elif sensor_type == 'reflected':
            return color_sensor.reflection(p)
        elif sensor_type == 'ambient':
            return color_sensor.ambient_light(p)
        elif sensor_type == 'distance':
            return distance_sensor.distance(p)
        elif sensor_type == 'force':
            return force_sensor.force(p)
        elif sensor_type == 'touched':
            return force_sensor.pressed(p)
        elif sensor_type == 'pitch':
            return _tilt_angles()[0]
        elif sensor_type == 'roll':
            return _tilt_angles()[1]
        elif sensor_type == 'yaw':
            return _tilt_angles()[2]
    except Exception:
        return None


def _read_system_metric(metric):
    """Reads a system metric value. Returns None on error."""
    try:
        if metric == 'battery':
            try:
                return hub.battery.level()
            except AttributeError:
                return hub.battery.voltage() // 40  # rough % from mV
        elif metric == 'temperature':
            try:
                return hub.temperature()
            except AttributeError:
                return None
        elif metric == 'charging':
            try:
                return hub.battery.charger_detect()
            except AttributeError:
                return False
        elif metric == 'connection_rssi':
            return None  # not accessible from Python
    except Exception:
        return None
    return None


# ---------------------------------------------------------------------------
# Capability declaration
# ---------------------------------------------------------------------------

def _build_capability():
    ports_list = []

    # Motor ports: try each port; include as motor if present
    for pid in ('A', 'B', 'C', 'D', 'E', 'F'):
        try:
            p = PORTS[pid]
            # probe: if port.device is None, nothing connected
            device = getattr(p, 'device', None)
            if device is None:
                continue
            ports_list.append({
                'id': pid,
                'type': 'motor',
                'features': ['speed', 'position', 'stall'],
                'constraints': {
                    'speed':    {'type': 'int', 'min': -100, 'max': 100},
                    'position': {'type': 'int', 'min': 0, 'max': 359, 'wraps': True},
                },
            })
        except Exception:
            pass

    # Display port (always present)
    ports_list.append({
        'id': 'display',
        'type': 'display',
        'width': 5, 'height': 5, 'depth': 'grayscale',
        'features': ['pixel', 'image', 'text', 'brightness', 'orientation'],
    })

    # Status LED (always present)
    ports_list.append({
        'id': 'status',
        'type': 'led',
        'features': ['set'],
        'constraints': {
            'color': {
                'type': 'enum',
                'values': list(_LED_COLORS.keys()),
            },
        },
    })

    # IMU (always present on SPIKE Prime 3.x)
    ports_list.append({
        'id': 'imu',
        'type': 'orientation',
        'features': ['pitch', 'roll', 'yaw', 'gesture'],
        'constraints': {
            'gesture': {
                'type': 'enum',
                'values': ['shake', 'tap', 'double_tap', 'fall', 'face_up', 'face_down'],
            },
        },
    })

    # Speaker
    ports_list.append({
        'id': 'speaker',
        'type': 'speaker',
        'features': ['beep'],
    })

    return {
        'type': 'capability',
        'device': 'spike-prime',
        'firmware': '3.x',
        'ssp_version': '0.6',
        'encodings': ['json-utf8-newline'],
        'supports_batch': False,
        'system_metrics': [
            'battery', 'charging', 'temperature',
            'button.left', 'button.right', 'button.center',
        ],
        'ports': ports_list,
    }


# ---------------------------------------------------------------------------
# Command handlers
# ---------------------------------------------------------------------------

def _handle_motor(cmd, obj, req_id):
    action = cmd.split('.')[1]  # run, stop, goto, reset
    port_id = obj.get('port', '').upper()
    p = PORTS.get(port_id)
    if p is None:
        _send_error(201, 'Unknown port: ' + port_id, req_id)
        return

    try:
        if action == 'run':
            spd = int(obj.get('speed', 0)) * 11
            dur = obj.get('duration')
            unit = obj.get('duration_unit', 'ms')
            if dur is not None:
                dur = int(dur)
                if unit == 'ms':
                    motor.run_for_time(p, dur, spd)
                elif unit == 'degrees':
                    motor.run_for_degrees(p, dur, spd)
                elif unit == 'rotations':
                    motor.run_for_degrees(p, dur * 360, spd)
            else:
                motor.run(p, spd)

        elif action == 'stop':
            stop_action = obj.get('stop_action', 'brake')
            if stop_action == 'coast':
                motor.stop(p, stop=motor.COAST)
            elif stop_action == 'hold':
                motor.stop(p, stop=motor.HOLD)
            else:
                motor.stop(p)

        elif action == 'goto':
            pos = int(obj.get('position', 0))
            spd = int(obj.get('speed', 50)) * 11
            motor.run_to_position(p, pos, spd)

        elif action == 'reset':
            motor.reset_relative_position(p, 0)

    except Exception as e:
        _send_error(301, 'Motor error: ' + str(e), req_id)


def _handle_movement(cmd, obj, req_id):
    action = cmd.split('.')[1]  # configure, drive, turn, stop

    try:
        if action == 'configure':
            lp = obj.get('left', '').upper()
            rp = obj.get('right', '').upper()
            if lp in PORTS and rp in PORTS:
                _ensure_pair(lp, rp)

        elif action == 'drive':
            lp = obj.get('left', _mov_lp or 'A').upper()
            rp = obj.get('right', _mov_rp or 'B').upper()
            if lp in PORTS and rp in PORTS:
                _ensure_pair(lp, rp)
            steering = int(obj.get('steering', 0))
            vel = int(obj.get('speed', 50)) * 11
            dur = obj.get('duration')
            unit = obj.get('duration_unit', 'ms')
            if dur is not None:
                dur = int(dur)
                if unit == 'degrees':
                    motor_pair.move_for_degrees(motor_pair.PAIR_1, dur, steering, velocity=vel)
                elif unit == 'rotations':
                    motor_pair.move_for_degrees(motor_pair.PAIR_1, dur * 360, steering, velocity=vel)
                else:
                    motor_pair.move_for_time(motor_pair.PAIR_1, dur, steering, velocity=vel)
            else:
                motor_pair.move(motor_pair.PAIR_1, steering, velocity=vel)

        elif action == 'turn':
            angle = int(obj.get('angle', 90))
            vel = int(obj.get('speed', 50)) * 11
            motor_pair.move_for_degrees(motor_pair.PAIR_1, angle, 0, velocity=vel)

        elif action == 'stop':
            stop_action = obj.get('stop_action', 'brake')
            if stop_action == 'coast':
                motor_pair.stop(motor_pair.PAIR_1, stop=motor_pair.COAST)
            else:
                motor_pair.stop(motor_pair.PAIR_1)

    except Exception as e:
        _send_error(301, 'Movement error: ' + str(e), req_id)


def _handle_led(cmd, obj, req_id):
    parts = cmd.split('.')  # ['led', ...] or ['led', 'matrix', 'pixel']
    if len(parts) == 2:
        action = parts[1]  # set, off
        port_id = obj.get('port', '')
        if port_id == 'status':
            if action == 'set':
                color_name = str(obj.get('color', 'off')).lower()
                c = _LED_COLORS.get(color_name, 0)
                try:
                    hub.light.color(c)
                except Exception:
                    pass
            elif action == 'off':
                try:
                    hub.light.color(0)
                except Exception:
                    pass
    elif len(parts) >= 3 and parts[1] == 'matrix':
        action = parts[2]  # pixel, image, text, clear, brightness, orientation
        try:
            if action == 'pixel':
                x = int(obj.get('x', 0))
                y = int(obj.get('y', 0))
                brightness = int(obj.get('brightness', 100))
                light_matrix.set_pixel(x, y, brightness)

            elif action == 'image':
                _show_image(str(obj.get('image', 'HAPPY')))

            elif action == 'text':
                text = str(obj.get('text', ''))
                light_matrix.write(text)

            elif action == 'clear':
                for _x in range(5):
                    for _y in range(5):
                        light_matrix.set_pixel(_x, _y, 0)

            elif action == 'brightness':
                # No global brightness API; scale future pixel writes instead
                pass

            elif action == 'orientation':
                rotation = int(obj.get('rotation', 0))
                try:
                    hub.light_matrix.set_orientation(rotation)
                except Exception:
                    pass

        except Exception as e:
            _send_error(301, 'LED error: ' + str(e), req_id)


def _handle_sound(cmd, obj, req_id):
    action = cmd.split('.')[1]  # beep, play, stop, set_volume
    try:
        if action == 'beep':
            freq = int(obj.get('freq', 440))
            dur = int(obj.get('duration', 200))
            hub.sound.beep(freq, dur)
        elif action == 'stop':
            hub.sound.stop()
        elif action == 'play':
            sound_name = obj.get('sound')
            if sound_name:
                hub.sound.play(str(sound_name))
        elif action == 'set_volume':
            level = int(obj.get('level', 50))
            try:
                hub.sound.set_volume(level)
            except AttributeError:
                pass
    except Exception as e:
        _send_error(301, 'Sound error: ' + str(e), req_id)


def _handle_sensor(cmd, obj, req_id):
    action = cmd.split('.')[1]  # subscribe, unsubscribe, read
    port_id = obj.get('port', '')

    if action == 'subscribe':
        mode = obj.get('mode', 'interval')
        interval_ms = int(obj.get('interval', 100))
        min_change = obj.get('min_change', None)
        # Determine sensor type from port capabilities (simplistic)
        if port_id == 'imu':
            sensor_type = obj.get('type', 'pitch')
        else:
            sensor_type = obj.get('type', 'color')
        _subscriptions[port_id] = {
            'type': sensor_type,
            'mode': mode,
            'interval_ms': interval_ms,
            'min_change': float(min_change) if min_change is not None else None,
            'last_ms': 0,
            'last_val': None,
        }

    elif action == 'unsubscribe':
        _subscriptions.pop(port_id, None)

    elif action == 'read':
        sensor_type = obj.get('type', 'color')
        if port_id == 'imu':
            angles = _tilt_angles()
            if sensor_type == 'pitch':
                _sensor_event(port_id, 'pitch', angles[0], req_id)
            elif sensor_type == 'roll':
                _sensor_event(port_id, 'roll', angles[1], req_id)
            elif sensor_type == 'yaw':
                _sensor_event(port_id, 'yaw', angles[2], req_id)
        else:
            val = _read_sensor_value(port_id, sensor_type)
            if val is not None:
                _sensor_event(port_id, sensor_type, val, req_id)


def _handle_system(cmd, obj, req_id):
    global _last_ping_ms, _heartbeat_active
    action = cmd.split('.')[1]  # ping, info, subscribe, unsubscribe, read, reset

    if action == 'ping':
        _last_ping_ms = time.ticks_ms()
        _heartbeat_active = True
        _send({'event': 'pong'})

    elif action == 'info':
        _send({
            'event': 'system_info',
            'device': 'spike-prime',
            'ssp_version': '0.6',
        })

    elif action == 'subscribe':
        metric = obj.get('metric', '')
        interval_ms = int(obj.get('interval', 5000))
        _sys_subscriptions[metric] = {
            'interval_ms': interval_ms,
            'last_ms': 0,
            'last_val': None,
        }

    elif action == 'unsubscribe':
        _sys_subscriptions.pop(obj.get('metric', ''), None)

    elif action == 'read':
        metric = obj.get('metric', '')
        if metric.startswith('button.'):
            btn_name = metric.split('.')[1]
            try:
                state = _read_button(btn_name)
                _system_event(metric, state)
            except Exception:
                pass
        else:
            val = _read_system_metric(metric)
            if val is not None:
                _system_event(metric, val)

    elif action == 'reset':
        pass  # no-op on this platform


def _read_button(name):
    """Read button state. Returns 'pressed' or 'released'."""
    try:
        if name == 'left':
            return 'pressed' if hub.button.left.is_pressed() else 'released'
        elif name == 'right':
            return 'pressed' if hub.button.right.is_pressed() else 'released'
        elif name == 'center':
            return 'pressed' if hub.button.center.is_pressed() else 'released'
    except AttributeError:
        # Fallback for firmwares that use hub.port button API
        pass
    return 'released'


# ---------------------------------------------------------------------------
# Message callback
# ---------------------------------------------------------------------------

def on_message(data):
    if not _json_ok:
        tunnel.send(b'{"event":"error","code":400,"message":"json not available"}\n')
        return

    if not isinstance(data, str):
        try:
            data = data.decode('utf-8')
        except Exception:
            data = ''.join(chr(b) for b in data)

    try:
        obj = json.loads(data.strip())
    except Exception:
        _send_error(400, 'Malformed JSON')
        return

    cmd = obj.get('cmd', '')
    req_id = obj.get('request_id', None)

    try:
        if cmd.startswith('motor.'):
            _handle_motor(cmd, obj, req_id)
        elif cmd.startswith('movement.'):
            _handle_movement(cmd, obj, req_id)
        elif cmd.startswith('led.'):
            _handle_led(cmd, obj, req_id)
        elif cmd.startswith('sound.'):
            _handle_sound(cmd, obj, req_id)
        elif cmd.startswith('sensor.'):
            _handle_sensor(cmd, obj, req_id)
        elif cmd.startswith('system.'):
            _handle_system(cmd, obj, req_id)
        else:
            _send_error(400, 'Unknown command: ' + cmd, req_id)
    except Exception as e:
        _send_error(400, 'Handler error: ' + str(e), req_id)


# ---------------------------------------------------------------------------
# Startup
# ---------------------------------------------------------------------------

def start():
    """Entry point — called when running on the SPIKE Prime hub."""
    tunnel.callback(on_message)
    _send(_build_capability())
    _run_loop()


def _run_loop():
    """Main polling loop — subscriptions and heartbeat."""
    global _heartbeat_active
    while True:
        now = time.ticks_ms()

        # Heartbeat: stop emitting if client stops pinging for 10 s
        if _heartbeat_active and _last_ping_ms is not None:
            if time.ticks_diff(now, _last_ping_ms) > 10000:
                _heartbeat_active = False
                _subscriptions.clear()
                _sys_subscriptions.clear()

        # Sensor subscriptions
        for pid, sub in list(_subscriptions.items()):
            elapsed = time.ticks_diff(now, sub['last_ms'])
            if elapsed < sub['interval_ms']:
                continue

            stype = sub['type']
            if pid == 'imu':
                angles = _tilt_angles()
                val = {'pitch': angles[0], 'roll': angles[1], 'yaw': angles[2]}.get(stype, 0)
            else:
                val = _read_sensor_value(pid, stype)

            if val is None:
                continue

            mode = sub['mode']
            last_val = sub['last_val']
            min_change = sub['min_change']

            should_emit = False
            if mode == 'interval':
                should_emit = True
            elif mode in ('on_change', 'hybrid'):
                if last_val is None:
                    should_emit = True
                elif min_change is not None:
                    try:
                        should_emit = abs(float(val) - float(last_val)) >= min_change
                    except (TypeError, ValueError):
                        should_emit = (val != last_val)
                else:
                    should_emit = (val != last_val)

            if should_emit:
                _sensor_event(pid, stype, val)
                sub['last_val'] = val
            sub['last_ms'] = now

        # System metric subscriptions
        for metric, sub in list(_sys_subscriptions.items()):
            elapsed = time.ticks_diff(now, sub['last_ms'])
            if elapsed < sub['interval_ms']:
                continue

            if metric.startswith('button.'):
                val = _read_button(metric.split('.')[1])
            else:
                val = _read_system_metric(metric)

            if val is not None and val != sub['last_val']:
                _system_event(metric, val)
                sub['last_val'] = val
            sub['last_ms'] = now

        time.sleep_ms(50)


# Run when executed on the hub (MicroPython treats this as __main__)
if __name__ == '__main__':
    start()
