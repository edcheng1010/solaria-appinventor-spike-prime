# LEGO SPIKE Prime hub controller — SSP v0.8 edition.
#
# Wire format: SSP v0.8 json-utf8-newline over TunnelMessage (opcode 0x32).
# Each incoming frame is one newline-terminated JSON string.
# Each outgoing event is one newline-terminated JSON string.
#
# This program is the TYPE 2 "bridge firmware" for the Solaria platform.
# It runs entirely on the hub via the Python TunnelMessage facility.
#
# See: https://github.com/edcheng1010/solaria-hub/blob/main/spec/SSP-v0.8.md

import hub, motor, motor_pair, time, math
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
    'black': color.BLACK if hasattr(color, 'BLACK') else 0,
    'magenta': color.MAGENTA if hasattr(color, 'MAGENTA') else 1,
    'violet': color.VIOLET if hasattr(color, 'VIOLET') else 2,
    'blue': color.BLUE if hasattr(color, 'BLUE') else 3,
    'azure': color.AZURE if hasattr(color, 'AZURE') else 4,
    'cyan': color.CYAN if hasattr(color, 'CYAN') else 5,
    'green': color.GREEN if hasattr(color, 'GREEN') else 6,
    'yellow': color.YELLOW if hasattr(color, 'YELLOW') else 7,
    'orange': color.ORANGE if hasattr(color, 'ORANGE') else 8,
    'red': color.RED if hasattr(color, 'RED') else 9,
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
# Orientation frame — body axes for each hub mounting face.
# Body frame (confirmed Top mode): +X=Left(A/C/E), +Y=Front(USB), +Z=Top(display)
# Per face: u=up, f=forward, l=left unit vectors in body frame.
# Pitch/roll derived from gravity vector; yaw from gyro integration about u-axis.
# Convention: pitch+ = ref face tilts forward; roll+ = left(A/C/E) side tilts up; yaw+ = CW.
_ORIENT_FRAMES = {
    # Vectors in SENSOR coordinates: acc[0]=USB, acc[1]=A/C/E, acc[2]=Top
    # (ux,uy,uz, fx,fy,fz, lx,ly,lz) where pitch=atan2(af,au), roll=atan2(al,au)
    # pitch+ = reference face tilts forward; roll+ = A/C/E side tilts up; yaw+ = CW
    'Top':        ( 0, 0, 1,   1, 0, 0,   0, 1, 0),
    'Bottom':     ( 0, 0,-1,   1, 0, 0,   0,-1, 0),
    'Front':      ( 1, 0, 0,   0, 0,-1,   0, 1, 0),
    'Back':       (-1, 0, 0,   0, 0, 1,   0, 1, 0),
    'Left side':  ( 0, 1, 0,   1, 0, 0,   0, 0,-1),
    'Right side': ( 0,-1, 0,   1, 0, 0,   0, 0, 1),
}
_orient_u = (0, 0, 1)   # current up unit vector (sensor frame)
_orient_f = (1, 0, 0)   # current forward unit vector (acc[0]=USB for pitch)
_orient_l = (0, 1, 0)   # current left unit vector (acc[1]=A/C/E for roll)
_yaw_acc  = 0.0         # current heading in degrees
_yaw_zero = 0.0         # yaw zero offset in degrees (ResetHubYaw/SetHubYaw)
# Complementary filter state for pitch/roll.
# Combines gyro (short-term, fast) with accelerometer (long-term, drift-free).
# FALLBACK to pure accelerometer: set _CF_ALPHA = 0.0 (pure accel EMA with _CF_ALPHA as weight).
_cf_pitch   = 0.0       # complementary-filter pitch (degrees)
_cf_roll    = 0.0       # complementary-filter roll (degrees)
_cf_last_ms = 0         # timestamp of last orientation update tick
_CF_ALPHA   = 0.98      # gyro weight (1-_CF_ALPHA=0.02 is accel correction weight)

# Map Python API orientation strings to LEGO face names for HubFaceOrientationRead/Changed.
_FACE_NAME_MAP = {
    'face_up':    'Top',
    'face_down':  'Bottom',
    'port_a_up':  'Left side',    # Port A on left face; pitch axis
    'port_a_down': 'Right side',
    'port_e_up':  'Front',        # Port E end; roll axis
    'port_e_down': 'Back',
}

# Gesture integer → SSP string name (SPIKE Prime 3.x constants)
_GESTURE_MAP = {0: 'tap', 1: 'double_tap', 2: 'shake', 3: 'fall'}
# Button constants for hub.button.pressed() API (SPIKE Prime 3.x)
_BTN_CONST = {
    'left':  hub.button.LEFT  if hasattr(hub.button, 'LEFT')  else 1,
    'right': hub.button.RIGHT if hasattr(hub.button, 'RIGHT') else 2,
}
# Gesture fast-poll: polls gesture() every 10 ms for this many ticks.
# 5 ticks = 50 ms (tap reliable); 20 ticks = 200 ms (shake/double_tap reliable).
_GESTURE_POLL_TICKS = 20

# Subscriptions: port_id -> {type, mode, interval_ms, min_change, last_ms, last_val}
_subscriptions = {}
_sys_subscriptions = {}  # metric -> {interval_ms, last_ms, last_val}

_last_ping_ms = None     # None = heartbeat not yet started
_heartbeat_active = False

# v0.8: cached volume (student-facing 0-100) for sound.read
_cached_volume = 50

def _hw_volume():
    # Hub speaker is inaudible below ~67. Remap student 1-100 -> hub 67-100
    # so the whole dial is usable. 0 stays truly off.
    if _cached_volume <= 0:
        return 0
    return 67 + (_cached_volume - 1) * 33 // 99

# v0.8: cached motor acceleration rates (port_id -> ms)
_motor_acceleration = {}
_movement_acceleration = None

# Light matrix display state — enables software brightness scaling and rotation.
# _matrix_pixels[row][col], values 0-100 natural brightness (before scale).
_matrix_pixels = [[0] * 5 for _ in range(5)]
_matrix_brightness = 100   # global scale 0-100
_matrix_orientation = 0    # degrees CW: 0, 90, 180, 270

# Known 5x5 pixel patterns for predefined images (row-major, 0=off, 100=on).
_IMAGE_PIXELS = {
    'HEART':      [[0,100,0,100,0],[100,100,100,100,100],[100,100,100,100,100],[0,100,100,100,0],[0,0,100,0,0]],
    'HEARTSMALL': [[0,0,0,0,0],[0,100,0,100,0],[0,100,100,100,0],[0,0,100,0,0],[0,0,0,0,0]],
    'HAPPY':      [[0,0,0,0,0],[0,100,0,100,0],[0,0,0,0,0],[100,0,0,0,100],[0,100,100,100,0]],
    'SMILE':      [[0,0,0,0,0],[0,0,0,0,0],[0,100,0,100,0],[100,0,0,0,100],[0,100,100,100,0]],
    'SAD':        [[0,0,0,0,0],[0,100,0,100,0],[0,0,0,0,0],[0,100,100,100,0],[100,0,0,0,100]],
    'CONFUSED':   [[0,0,0,0,0],[0,100,0,100,0],[0,0,0,0,0],[0,0,100,0,0],[0,100,0,0,0]],
    'ANGRY':      [[100,0,0,0,100],[0,100,0,100,0],[0,0,0,0,0],[0,100,100,100,0],[100,0,100,0,100]],
    'ASLEEP':     [[0,0,0,0,0],[100,100,0,100,100],[0,0,0,0,0],[0,100,100,100,0],[0,0,0,0,0]],
    'SURPRISED':  [[0,100,0,100,0],[0,0,0,0,0],[0,0,100,0,0],[0,100,0,100,0],[0,0,100,0,0]],
    'YES':        [[0,0,0,0,0],[0,0,0,0,100],[0,0,0,100,0],[100,0,100,0,0],[0,100,0,0,0]],
    'NO':         [[100,0,0,0,100],[0,100,0,100,0],[0,0,100,0,0],[0,100,0,100,0],[100,0,0,0,100]],
    'ARROWNORTH': [[0,0,100,0,0],[0,100,100,100,0],[100,0,100,0,100],[0,0,100,0,0],[0,0,100,0,0]],
    'ARROWEAST':  [[0,0,100,0,0],[0,0,0,100,0],[100,100,100,100,100],[0,0,0,100,0],[0,0,100,0,0]],
    'ARROWSOUTH': [[0,0,100,0,0],[0,0,100,0,0],[100,0,100,0,100],[0,100,100,100,0],[0,0,100,0,0]],
    'ARROWWEST':  [[0,0,100,0,0],[0,100,0,0,0],[100,100,100,100,100],[0,100,0,0,0],[0,0,100,0,0]],
}


def _render_matrix():
    """Redraw all 25 pixels applying brightness scale and orientation transform."""
    for row in range(5):
        for col in range(5):
            o = _matrix_orientation
            if o == 90:
                px, py = 4 - row, col
            elif o == 180:
                px, py = 4 - col, 4 - row
            elif o == 270:
                px, py = row, 4 - col
            else:
                px, py = col, row
            scaled = int(_matrix_pixels[row][col] * _matrix_brightness / 100)
            light_matrix.set_pixel(px, py, scaled)

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


def _dot(a, b):
    return a[0]*b[0] + a[1]*b[1] + a[2]*b[2]


def _update_orientation(now):
    """Update pitch/roll (complementary filter) and yaw (hardware or gyro).

    Complementary filter blends gyro short-term precision with accelerometer
    long-term correction: angle = CF_ALPHA*(angle + rate*dt) + (1-CF_ALPHA)*accel_angle.
    FALLBACK: set _CF_ALPHA=0.0 to revert to pure accelerometer (accel_angle only).

    Yaw strategy: Top/Bottom use drift-free hardware-fused yaw from tilt_angles()[0].
    Other faces use gyro integration with a 1.5 deg/s stillness guard to limit tilt bleed.
    """
    global _cf_pitch, _cf_roll, _cf_last_ms, _yaw_acc
    try:
        # --- Shared sensor reads ---
        a = hub.motion_sensor.acceleration()   # mg, sensor frame
        ax, ay, az = a[0], a[1], a[2]
        w = hub.motion_sensor.angular_velocity()  # decideg/s, sensor frame
        wx, wy, wz = w[0] / 10.0, w[1] / 10.0, w[2] / 10.0

        # --- Pitch / Roll: complementary filter ---
        au = _dot(_orient_u, (ax, ay, az))
        accel_pitch = math.degrees(math.atan2(_dot(_orient_f, (ax, ay, az)), au))
        accel_roll  = math.degrees(math.atan2(_dot(_orient_l, (ax, ay, az)), au))

        dt_ms = time.ticks_diff(now, _cf_last_ms) if _cf_last_ms else 0
        _cf_last_ms = now

        if 0 < dt_ms <= 500 and _CF_ALPHA > 0.0:
            dt_s = dt_ms / 1000.0
            # Pitch: negate because rotating l toward u (pitch+) is -rotation about l.
            # Roll: positive because rotating l toward u about f-axis is +rotation about f.
            pitch_rate = -_dot(_orient_l, (wx, wy, wz))
            roll_rate  =  _dot(_orient_f, (wx, wy, wz))
            _cf_pitch = _CF_ALPHA * (_cf_pitch + pitch_rate * dt_s) + (1.0 - _CF_ALPHA) * accel_pitch
            _cf_roll  = _CF_ALPHA * (_cf_roll  + roll_rate  * dt_s) + (1.0 - _CF_ALPHA) * accel_roll
        else:
            # First tick, long gap, or CF_ALPHA=0 fallback: use pure accel.
            _cf_pitch = accel_pitch
            _cf_roll  = accel_roll

        # --- Yaw ---
        if abs(_orient_u[2]) > 0.5:
            # Top / Bottom: drift-free hardware-fused yaw. CW from above = positive.
            raw = hub.motion_sensor.tilt_angles()
            _yaw_acc = -raw[0] / 10.0
        else:
            # Side / end mounting: gyro integration with stillness guard.
            if 0 < dt_ms <= 500:
                rate = -_dot(_orient_u, (wx, wy, wz))
                if abs(rate) > 1.5:
                    _yaw_acc += rate * (dt_ms / 1000.0)
    except Exception:
        pass


def _tilt_angles():
    """Returns (pitch, roll, yaw) in integer degrees for the current orientation."""
    return (int(round(_cf_pitch)),
            int(round(_cf_roll)),
            int(round(_yaw_acc - _yaw_zero)))


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
    global _matrix_pixels
    n = name.upper()
    if n in _IMAGE_PIXELS:
        # Use our pixel map — supports brightness scaling and rotation.
        _matrix_pixels = [row[:] for row in _IMAGE_PIXELS[n]]
        _render_matrix()
        return
    # Fallback: let firmware render (brightness/rotation won't apply).
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


def _face_orientation():
    """Return LEGO face name (Top/Bottom/Front/Back/Left side/Right side) from IMU."""
    try:
        try:
            raw = hub.motion_sensor.get_orientation()
            return _FACE_NAME_MAP.get(raw, raw)  # map Python API string to LEGO name
        except AttributeError:
            pass
        # Fallback: detect face from gravity vector (sensor frame: acc[0]=USB, acc[1]=ACE, acc[2]=Top).
        try:
            a = hub.motion_sensor.acceleration()
            ax, ay, az = abs(a[0]), abs(a[1]), abs(a[2])
            if az >= ax and az >= ay:
                return 'Top' if a[2] > 0 else 'Bottom'
            elif ax >= ay:
                return 'Front' if a[0] > 0 else 'Back'
            else:
                return 'Left side' if a[1] > 0 else 'Right side'
        except Exception:
            return 'Top'
    except Exception:
        return 'Top'


def _angular_velocity():
    """Return angular velocity {x, y, z} in deg/s. Hub returns decideg/s — divide by 10."""
    try:
        av = hub.motion_sensor.angular_velocity()
        return {'x': av[0] / 10.0, 'y': av[1] / 10.0, 'z': av[2] / 10.0}
    except Exception:
        return {'x': 0, 'y': 0, 'z': 0}


def _read_sensor_value(port_id, sensor_type, params=None):
    """Reads a sensor value. Returns None on error."""
    # IMU-specific types routed directly
    if port_id == 'imu' or sensor_type in ('pitch', 'roll', 'yaw',
                                             'face_orientation', 'angular_velocity',
                                             'acceleration', 'gesture'):
        try:
            if sensor_type == 'pitch':          return _tilt_angles()[0]
            if sensor_type == 'roll':           return _tilt_angles()[1]
            if sensor_type == 'yaw':            return _tilt_angles()[2]
            if sensor_type == 'face_orientation': return _face_orientation()
            if sensor_type == 'angular_velocity': return _angular_velocity()
            if sensor_type == 'gesture':
                try:
                    return _GESTURE_MAP.get(hub.motion_sensor.gesture(), None)
                except Exception:
                    return None
            if sensor_type == 'acceleration':
                try:
                    # Hub returns mg — convert to m/s² (1g = 9810 mg, divide by 100 ≈ m/s²)
                    acc = hub.motion_sensor.acceleration()
                    return {'x': round(acc[0] / 100.0, 2),
                            'y': round(acc[1] / 100.0, 2),
                            'z': round(acc[2] / 100.0, 2)}
                except Exception:
                    return {'x': 0, 'y': 0, 'z': 0}
        except Exception:
            return None

    p = PORTS.get(port_id.upper())
    if p is None:
        return None

    # Motor position / speed reading — try multiple FW 3.x function names
    if sensor_type == 'position':
        # Cumulative position since last reset (can exceed 360 or be negative)
        for fn_name in ('relative_position', 'get_position'):
            fn = getattr(motor, fn_name, None)
            if fn is not None:
                try:
                    return fn(p)
                except Exception:
                    continue
        return None
    if sensor_type == 'absolute_position':
        # Current orientation 0-359
        fn = getattr(motor, 'absolute_position', None)
        if fn is not None:
            try:
                return fn(p)
            except Exception:
                return None
        # Fallback: cumulative position mod 360
        for fn_name in ('relative_position', 'get_position'):
            fn = getattr(motor, fn_name, None)
            if fn is not None:
                try:
                    return fn(p) % 360
                except Exception:
                    continue
        return None
    if sensor_type == 'speed':
        # On observed SPIKE Prime 3.x firmware, motor.velocity(port) returns the
        # current speed already in PERCENT (-100..100), not deg/s as the docs claim.
        # At 100% speed setting it reads ~88 due to closed-loop tracking.
        fn = getattr(motor, 'velocity', None) or getattr(motor, 'get_velocity', None)
        if fn is not None:
            try:
                return int(fn(p))
            except Exception:
                pass
        return None

    if not _sensors_ok:
        return None
    try:
        if sensor_type == 'color':
            c = color_sensor.color(p)
            return _CLR_MAP.get(c, str(c))
        elif sensor_type == 'rgb':
            try:
                rgb = color_sensor.rgbi(p)  # (r, g, b, intensity) — raw 0-1023 range
                intensity = rgb[3] if rgb[3] > 0 else 1
                # Normalize to 0-255 using intensity as the scale reference.
                return [min(255, int(rgb[i] * 255 // intensity)) for i in range(3)]
            except Exception:
                return [0, 0, 0]
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
        elif sensor_type == 'is_color':
            p2 = params or {}
            c = color_sensor.color(p)
            name = _CLR_MAP.get(c, str(c)).lower()
            queried = p2.get('color', '').lower()
            return {'match': name == queried, 'color': queried}
        elif sensor_type == 'is_closer':
            p2 = params or {}
            d = distance_sensor.distance(p)
            mm = int(p2.get('mm', 0))
            return isinstance(d, (int, float)) and 0 <= d <= mm
        elif sensor_type == 'is_reflected_above':
            p2 = params or {}
            r = color_sensor.reflection(p)
            return isinstance(r, (int, float)) and r > int(p2.get('percent', 0))
    except Exception:
        return None


def _read_system_metric(metric):
    """Reads a system metric value. Returns None on error."""
    try:
        if metric == 'battery':
            # hub.battery_voltage may be a callable or direct attribute.
            # Normalise to 0-100 assuming ~6400-8400 mV range (2-cell Li-ion).
            try:
                v = hub.battery_voltage() if callable(hub.battery_voltage) else hub.battery_voltage
                return max(0, min(100, (v - 6400) * 100 // 2000))
            except Exception:
                return None
        elif metric == 'temperature':
            try:
                return hub.temperature() / 10.0  # hub returns decidegrees
            except Exception:
                try:
                    return hub.battery_temperature / 10.0
                except Exception:
                    return None
        elif metric == 'charging':
            # usb_charge_current: ~0-3 unplugged, ~190+ when charging (measured).
            try:
                uc = hub.usb_charge_current() if callable(hub.usb_charge_current) else hub.usb_charge_current
                return uc > 20
            except Exception:
                return False
        elif metric == 'connection_rssi':
            return None  # measured on Android side, not accessible from Python
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
                'features': ['speed', 'position', 'stall', 'power', 'acceleration'],
                'goto_modes': ['absolute', 'relative'],
                'constraints': {
                    'speed':        {'type': 'int', 'min': -100, 'max': 100},
                    'position':     {'type': 'int', 'min': 0, 'max': 359, 'wraps': True},
                    'acceleration': {'type': 'int', 'min': 0, 'max': 10000},
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
        # 'touch' feature omitted until FW support is verified
    })

    # Status LED (always present)
    ports_list.append({
        'id': 'status',
        'type': 'led',
        'features': ['set'],
        'constraints': {
            'color': {'type': 'enum', 'values': list(_LED_COLORS.keys())},
        },
    })

    # IMU (always present on SPIKE Prime 3.x) — v0.7/v0.8 features
    ports_list.append({
        'id': 'imu',
        'type': 'orientation',
        'features': ['pitch', 'roll', 'yaw', 'gesture', 'face_orientation', 'angular_velocity'],
        'constraints': {
            'gesture': {
                'type': 'enum',
                'values': ['shake', 'tap', 'double_tap', 'fall'],
            },
            'face_orientation': {
                'type': 'enum',
                'values': ['face_up', 'face_down', 'port_a_up', 'port_a_down',
                           'port_e_up', 'port_e_down'],
            },
        },
    })

    # Speaker — v0.8: volume + sound_wait_supported
    ports_list.append({
        'id': 'speaker',
        'type': 'speaker',
        'features': ['beep', 'volume'],
        'sound_wait_supported': True,
        # 'builtin' and 'midi' features added after FW API verification
    })

    return {
        'type': 'capability',
        'device': 'spike-prime',
        'firmware': '3.x',
        'ssp_version': '0.8',
        'encodings': ['json-utf8-newline'],
        'supports_batch': False,
        'tank_drive': True,
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
    action = cmd.split('.')[1]  # run, stop, goto, reset, set_acceleration
    port_id = obj.get('port', '').upper()

    # set_acceleration doesn't need a physical port
    if action == 'set_acceleration':
        rate = int(obj.get('rate', 500))
        _motor_acceleration[port_id] = rate
        # SPIKE FW may not expose hardware-level accel; cache client-side for now
        return

    p = PORTS.get(port_id)
    if p is None:
        _send_error(201, 'Unknown port: ' + port_id, req_id)
        return

    try:
        if action == 'run':
            raw_speed = int(obj.get('speed', 0))
            mode = obj.get('mode', 'speed')

            # Power mode: open-loop raw duty cycle, no velocity feedback.
            # SPIKE FW 3.x exposes this differently across versions — try known APIs.
            if mode == 'power':
                # raw_speed is -100..+100 percent duty cycle
                power_pct = max(-100, min(100, raw_speed))
                attempts = [
                    lambda: motor.start_at_power(p, power_pct),
                    lambda: motor.run(p, power_pct, mode=getattr(motor, 'POWER', 1)),
                    # Last resort: scale to velocity range (approximate)
                    lambda: motor.run(p, power_pct * 11),
                ]
                for attempt in attempts:
                    try:
                        attempt()
                        break
                    except (AttributeError, TypeError):
                        continue
                    except Exception as e:
                        _send_error(301, 'motor.run power: ' + str(e), req_id)
                        break
                return

            spd = raw_speed * 11
            dur = obj.get('duration')
            unit = obj.get('duration_unit', 'ms')
            # Apply cached acceleration for timed runs (SPIKE FW supports kwarg)
            accel = _motor_acceleration.get(port_id.upper(), None)
            if dur is not None:
                dur = int(dur)
                accel_kwargs = {'acceleration': accel, 'deceleration': accel} if accel else {}
                try:
                    if unit == 'ms':
                        motor.run_for_time(p, dur, spd, **accel_kwargs)
                    elif unit == 'degrees':
                        motor.run_for_degrees(p, dur, spd, **accel_kwargs)
                    elif unit == 'rotations':
                        motor.run_for_degrees(p, dur * 360, spd, **accel_kwargs)
                except TypeError:
                    # FW version doesn't support acceleration kwargs — run without
                    if unit == 'ms':
                        motor.run_for_time(p, dur, spd)
                    elif unit == 'degrees':
                        motor.run_for_degrees(p, dur, spd)
                    elif unit == 'rotations':
                        motor.run_for_degrees(p, dur * 360, spd)
            else:
                # Indefinite run — try acceleration kwarg first (newer FW), fall back
                if accel:
                    try:
                        motor.run(p, spd, acceleration=accel)
                    except TypeError:
                        motor.run(p, spd)
                else:
                    motor.run(p, spd)

        elif action == 'stop':
            stop_action = obj.get('stop_action', 'brake')
            # Map our string to the FW constant if available
            stop_const = None
            if stop_action == 'coast':
                stop_const = getattr(motor, 'COAST', None)
            elif stop_action == 'hold':
                stop_const = getattr(motor, 'HOLD', None)
            elif stop_action == 'brake':
                stop_const = getattr(motor, 'BRAKE', None) or getattr(motor, 'SMART_BRAKING', None)
            # Try with kwarg, then positional, then plain stop
            if stop_const is not None:
                try:
                    motor.stop(p, stop=stop_const)
                except TypeError:
                    try:
                        motor.stop(p, stop_const)
                    except TypeError:
                        motor.stop(p)
            else:
                motor.stop(p)

        elif action == 'goto':
            pos = int(obj.get('position', 0))
            spd = abs(int(obj.get('speed', 50))) * 11
            goto_mode = obj.get('mode', 'absolute')
            if goto_mode == 'relative':
                motor.run_for_degrees(p, pos, spd)
            else:
                # Absolute goto via delta-of-run_for_degrees workaround.
                # SPIKE FW 3.x absolute-position APIs vary too much across versions
                # (run_to_position vs run_to_absolute_position with different signatures),
                # but motor.run_for_degrees is reliably present. We compute the shortest
                # delta from current absolute position to the target and run that.
                target = pos % 360
                current = None
                # Try canonical FW 3.x function names for current position
                for fn_name in ('absolute_position', 'relative_position'):
                    fn = getattr(motor, fn_name, None)
                    if fn is not None:
                        try:
                            current = fn(p) % 360
                            break
                        except Exception:
                            pass
                if current is None:
                    _send_error(301, 'goto: cannot read current motor position', req_id)
                else:
                    delta = target - current
                    if delta > 180:   delta -= 360
                    elif delta < -180: delta += 360
                    try:
                        motor.run_for_degrees(p, delta, spd)
                    except Exception as e:
                        _send_error(301, 'goto failed: %s: %s' %
                                    (type(e).__name__, str(e)), req_id)

        elif action == 'reset':
            motor.reset_relative_position(p, 0)

    except Exception as e:
        _send_error(301, 'Motor error: ' + str(e), req_id)


def _handle_movement(cmd, obj, req_id):
    global _movement_acceleration
    action = cmd.split('.')[1]  # configure, drive, turn, stop, set_acceleration

    try:
        if action == 'set_acceleration':
            _movement_acceleration = int(obj.get('rate', 500))
            return

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

            # v0.7 tank drive: explicit left_speed / right_speed
            accel = _movement_acceleration
            accel_kw = {'acceleration': accel, 'deceleration': accel} if accel is not None else {}
            if 'left_speed' in obj or 'right_speed' in obj:
                l_vel = int(obj.get('left_speed', 0)) * 11
                r_vel = int(obj.get('right_speed', 0)) * 11
                try:
                    motor_pair.move_tank(motor_pair.PAIR_1, l_vel, r_vel, **accel_kw)
                except TypeError:
                    motor_pair.move_tank(motor_pair.PAIR_1, l_vel, r_vel)
            else:
                steering = int(obj.get('steering', 0))
                vel = int(obj.get('speed', 50)) * 11
                dur = obj.get('duration')
                unit = obj.get('duration_unit', 'ms')
                if dur is not None:
                    dur = int(dur)
                    try:
                        if unit == 'degrees':
                            motor_pair.move_for_degrees(motor_pair.PAIR_1, dur, steering, velocity=vel, **accel_kw)
                        elif unit == 'rotations':
                            motor_pair.move_for_degrees(motor_pair.PAIR_1, dur * 360, steering, velocity=vel, **accel_kw)
                        else:
                            motor_pair.move_for_time(motor_pair.PAIR_1, dur, steering, velocity=vel, **accel_kw)
                    except TypeError:
                        if unit == 'degrees':
                            motor_pair.move_for_degrees(motor_pair.PAIR_1, dur, steering, velocity=vel)
                        elif unit == 'rotations':
                            motor_pair.move_for_degrees(motor_pair.PAIR_1, dur * 360, steering, velocity=vel)
                        else:
                            motor_pair.move_for_time(motor_pair.PAIR_1, dur, steering, velocity=vel)
                else:
                    try:
                        motor_pair.move(motor_pair.PAIR_1, steering, velocity=vel, **accel_kw)
                    except TypeError:
                        motor_pair.move(motor_pair.PAIR_1, steering, velocity=vel)

        elif action == 'turn':
            angle = int(obj.get('angle', 90))
            vel = int(obj.get('speed', 50)) * 11
            accel = _movement_acceleration
            accel_kw = {'acceleration': accel, 'deceleration': accel} if accel is not None else {}
            try:
                motor_pair.move_for_degrees(motor_pair.PAIR_1, angle, 0, velocity=vel, **accel_kw)
            except TypeError:
                motor_pair.move_for_degrees(motor_pair.PAIR_1, angle, 0, velocity=vel)

        elif action == 'stop':
            stop_action = obj.get('stop_action', 'brake')
            stop_const = None
            if stop_action == 'coast':
                stop_const = getattr(motor_pair, 'COAST', None)
            elif stop_action == 'hold':
                stop_const = getattr(motor_pair, 'HOLD', None)
            elif stop_action == 'brake':
                stop_const = getattr(motor_pair, 'BRAKE', None) or getattr(motor_pair, 'SMART_BRAKING', None)
            if stop_const is not None:
                try:
                    motor_pair.stop(motor_pair.PAIR_1, stop=stop_const)
                except TypeError:
                    try:
                        motor_pair.stop(motor_pair.PAIR_1, stop_const)
                    except TypeError:
                        motor_pair.stop(motor_pair.PAIR_1)
            else:
                motor_pair.stop(motor_pair.PAIR_1)

    except Exception as e:
        _send_error(301, 'Movement error: ' + str(e), req_id)


def _handle_led(cmd, obj, req_id):
    global _matrix_pixels, _matrix_brightness, _matrix_orientation
    parts = cmd.split('.')  # ['led', 'set'], ['led', 'distance'], ['led', 'matrix', 'pixel']
    if len(parts) == 2 and parts[1] == 'distance':
        p = PORTS.get(obj.get('port', '').upper())
        if p is not None:
            try:
                distance_sensor.show(p, [
                    int(obj.get('tl', 0)),
                    int(obj.get('tr', 0)),
                    int(obj.get('bl', 0)),
                    int(obj.get('br', 0)),
                ])
            except Exception as e:
                _send_error(301, 'distance LED error: ' + str(e), req_id)
        return
    if len(parts) == 2:
        action = parts[1]  # set, off
        port_id = obj.get('port', '')
        if port_id == 'status':
            if action == 'set':
                color_name = str(obj.get('color', 'off')).lower()
                c = _LED_COLORS.get(color_name, 0)
                try:
                    # Two-arg form: target the POWER (center button) light.
                    # CONNECT (light 1) is owned by firmware for BLE status.
                    hub.light.color(getattr(hub.light, 'POWER', 0), c)
                except Exception:
                    pass
            elif action == 'off':
                try:
                    hub.light.color(getattr(hub.light, 'POWER', 0), 0)
                except Exception:
                    pass
    elif len(parts) >= 3 and parts[1] == 'matrix':
        action = parts[2]  # pixel, image, text, clear, brightness, orientation, rotate
        try:
            if action == 'pixel':
                x = int(obj.get('x', 0))
                y = int(obj.get('y', 0))
                brightness = int(obj.get('brightness', 100))
                _matrix_pixels[y][x] = max(0, min(100, brightness))
                scaled = int(_matrix_pixels[y][x] * _matrix_brightness / 100)
                light_matrix.set_pixel(x, y, scaled)

            elif action == 'image':
                _show_image(str(obj.get('image', 'HAPPY')))

            elif action == 'text':
                text = str(obj.get('text', ''))
                light_matrix.write(text)

            elif action == 'clear':
                _matrix_pixels = [[0] * 5 for _ in range(5)]
                _render_matrix()

            elif action == 'brightness':
                _matrix_brightness = max(0, min(100, int(obj.get('level', 100))))
                _render_matrix()

            elif action == 'orientation':
                _matrix_orientation = int(obj.get('rotation', 0)) % 360
                _render_matrix()

            elif action == 'rotate':
                degrees = int(obj.get('degrees', 90))
                _matrix_orientation = (_matrix_orientation + degrees) % 360
                _render_matrix()

        except Exception as e:
            _send_error(301, 'LED error: ' + str(e), req_id)


def _handle_sound(cmd, obj, req_id):
    global _cached_volume
    action = cmd.split('.')[1]  # beep, play, stop, set_volume, read
    try:
        if action == 'beep':
            freq = int(obj.get('freq', 440))
            dur = obj.get('duration')
            if dur is not None:
                hub.sound.beep(freq, int(dur), _hw_volume())
                # wait=true means music context (PlayNoteForBeats) — block until done
                # so notes play sequentially. Sound context (Beep) leaves wait=false.
                if obj.get('wait', False):
                    time.sleep_ms(int(dur))
            else:
                # Indefinite beep — no native API; just beep for a long time
                hub.sound.beep(freq, 30000, _hw_volume())

        elif action == 'rest':
            time.sleep_ms(int(obj.get('duration', 0)))

        elif action == 'stop':
            hub.sound.stop()

        elif action == 'play':
            wait = obj.get('wait', False)
            sound_name = obj.get('sound')
            notes = obj.get('notes')
            if notes:
                # v0.8 MIDI notes — parse and play via beep sequences (best effort)
                _play_notes_sequence(notes, int(obj.get('tempo', 120)), req_id)
                return
            if sound_name:
                if wait:
                    try:
                        hub.sound.play(str(sound_name), volume=_cached_volume)
                        _send({'event': 'sound_complete', 'request_id': req_id} if req_id else
                              {'event': 'sound_complete'})
                    except TypeError:
                        hub.sound.play(str(sound_name))
                        _send({'event': 'sound_complete'})
                else:
                    hub.sound.play(str(sound_name))

        elif action == 'set_volume':
            # Store only — volume is applied per-beep via the beep() volume arg.
            # Do NOT also call hub.sound.volume() or scaling is applied twice.
            level = int(obj.get('level', 50))
            _cached_volume = max(0, min(100, level))

        elif action == 'read':
            metric = obj.get('metric', 'volume')
            if metric == 'volume':
                _send({'event': 'sound', 'metric': 'volume', 'value': _cached_volume})

    except Exception as e:
        _send_error(301, 'Sound error: ' + str(e), req_id)


def _play_notes_sequence(notes_str, tempo, req_id):
    """Parse v0.8 §6.3.1 notes string and play as beeps (best-effort MIDI)."""
    # Note name -> frequency mapping (A4=440 Hz standard)
    NOTE_FREQ = {
        'C': 261, 'C#': 277, 'Db': 277, 'D': 293, 'D#': 311, 'Eb': 311,
        'E': 329, 'F': 349, 'F#': 369, 'Gb': 369, 'G': 392, 'G#': 415,
        'Ab': 415, 'A': 440, 'A#': 466, 'Bb': 466, 'B': 493,
    }
    ms_per_beat = int(60000 / max(1, tempo))
    try:
        tokens = notes_str.strip().split()
        for token in tokens:
            if ':' not in token:
                continue
            note_part, dur_part = token.rsplit(':', 1)
            duration_ms = int(float(dur_part) * ms_per_beat)
            if note_part == 'R':
                time.sleep_ms(duration_ms)
                continue
            # Strip octave digit to get note name, then compute freq
            import re as _re
            m = _re.match(r'([A-G][#b]?)(\d)', note_part)
            if m:
                name, octave = m.group(1), int(m.group(2))
                base_freq = NOTE_FREQ.get(name, 440)
                freq = int(base_freq * (2 ** (octave - 4)))
                hub.sound.beep(freq, duration_ms, _hw_volume())
            else:
                time.sleep_ms(duration_ms)
    except Exception:
        pass  # degrade silently


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
        val = _read_sensor_value(port_id, sensor_type, obj)
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
            'ssp_version': '0.8',
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


def _handle_timer(cmd, obj, req_id):
    global _timer_start
    action = cmd.split('.')[1]  # get, reset
    if action == 'get':
        elapsed_ms = time.ticks_diff(time.ticks_ms(), _timer_start)
        _sensor_event('timer', 'elapsed', elapsed_ms // 1000, req_id)
    elif action == 'reset':
        _timer_start = time.ticks_ms()


def _handle_orientation(cmd, obj, req_id):
    """v0.7 orientation.* command category."""
    global _orient_u, _orient_f, _orient_l, _yaw_acc, _yaw_zero
    global _cf_pitch, _cf_roll, _cf_last_ms
    action = cmd.split('.')[1]  # set_yaw, reset_yaw, set_reference
    try:
        if action == 'reset_yaw':
            _yaw_zero = _yaw_acc
        elif action == 'set_yaw':
            angle = int(obj.get('angle', 0))
            _yaw_zero = _yaw_acc - angle
        elif action == 'set_reference':
            face = obj.get('face', 'Top')
            f = _ORIENT_FRAMES.get(face, _ORIENT_FRAMES['Top'])
            _orient_u = (f[0], f[1], f[2])
            _orient_f = (f[3], f[4], f[5])
            _orient_l = (f[6], f[7], f[8])
            # Seed yaw from hardware if Top/Bottom, else start at 0.
            try:
                if abs(f[2]) > 0.5:  # Top or Bottom
                    raw = hub.motion_sensor.tilt_angles()
                    _yaw_acc = -raw[0] / 10.0
                else:
                    _yaw_acc = 0.0
            except Exception:
                _yaw_acc = 0.0
            _yaw_zero  = _yaw_acc  # reference point = current heading
            _cf_last_ms = 0
            # Seed filter from live accel so readings start at true value immediately.
            try:
                a = hub.motion_sensor.acceleration()
                ax, ay, az = a[0], a[1], a[2]
                au = _dot(_orient_u, (ax, ay, az))
                _cf_pitch = math.degrees(math.atan2(_dot(_orient_f, (ax, ay, az)), au))
                _cf_roll  = math.degrees(math.atan2(_dot(_orient_l, (ax, ay, az)), au))
            except Exception:
                _cf_pitch = 0.0
                _cf_roll  = 0.0
    except Exception as e:
        _send_error(301, 'Orientation error: ' + str(e), req_id)


def _read_button(name):
    """Read button state using hub.button.pressed(const) — SPIKE Prime 3.x API."""
    try:
        c = _BTN_CONST.get(name)
        if c is None:
            return 'released'
        return 'pressed' if hub.button.pressed(c) > 0 else 'released'
    except Exception:
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
        elif cmd.startswith('timer.'):
            _handle_timer(cmd, obj, req_id)
        elif cmd.startswith('orientation.'):
            _handle_orientation(cmd, obj, req_id)
        else:
            _send_error(400, 'Unknown command: ' + cmd, req_id)
    except Exception as e:
        _send_error(400, 'Handler error: ' + str(e), req_id)


# ---------------------------------------------------------------------------
# Startup
# ---------------------------------------------------------------------------

def start():
    """Entry point — called when running on the SPIKE Prime hub."""
    # Pin global speaker volume to max so per-beep volume controls the full range.
    try:
        hub.sound.volume(100)
    except Exception:
        pass
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

        # Update pitch/roll (complementary filter) and yaw every tick.
        _update_orientation(now)

        # Fast-poll gesture subscriptions: check every 10 ms for _GESTURE_POLL_TICKS ticks.
        # 200 ms window catches shake and double_tap which need sustained/repeated motion.
        for pid, sub in list(_subscriptions.items()):
            if sub['type'] != 'gesture':
                continue
            for _ in range(_GESTURE_POLL_TICKS):
                try:
                    g = _GESTURE_MAP.get(hub.motion_sensor.gesture(), None)
                except Exception:
                    g = None
                if g is not None:
                    _sensor_event(pid, 'gesture', g)
                    sub['last_val'] = None
                    sub['last_ms'] = now
                    break
                time.sleep_ms(10)

        # Sensor subscriptions
        for pid, sub in list(_subscriptions.items()):
            elapsed = time.ticks_diff(now, sub['last_ms'])
            if elapsed < sub['interval_ms']:
                continue

            stype = sub['type']
            if stype == 'gesture':
                continue  # handled by fast-poll above
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
                # Reset gestures to None after emit so the same gesture can fire again next time.
                sub['last_val'] = None if stype == 'gesture' else val
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

            if val is not None:
                # Only fire on actual change from a known baseline (not on first read).
                if sub['last_val'] is not None and val != sub['last_val']:
                    _system_event(metric, val)
                sub['last_val'] = val
            sub['last_ms'] = now

        time.sleep_ms(50)


# Run when executed on the hub (MicroPython treats this as __main__)
if __name__ == '__main__':
    start()
