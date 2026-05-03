# LEGO SPIKE Prime hub controller — MVP release.
#
# Command protocol (all via TunnelMessage):
#
#   MTR:A:CW:050       start motor A clockwise at 50%
#   MTR:A:STOP         stop motor A
#
#   MOV:PAIR:A:B       set movement pair A=left B=right
#   MOV:FWD:050        move forward at 50%
#   MOV:BWD:050        move backward at 50%
#   MOV:STEER:+50:075  steer +50 (right) at speed 75%
#   MOV:STOP           stop movement
#
#   LGT:ON:HAPPY       show image on 5x5 matrix
#   LGT:OFF            turn off matrix
#   LGT:TXT:Hello      scroll text on matrix
#   LGT:PIX:2:2:100    set pixel (x,y) to brightness
#   LGT:BTN:RED        set center button LED color
#
#   SEN:CLR:A          read color on port A  -> SEN:CLR:A:RED
#   SEN:DST:A          read distance on port A -> SEN:DST:A:150
#   SEN:PRS:A          read pressure on port A -> SEN:PRS:A:50
#   SEN:ISP:A          is force sensor pressed? -> SEN:ISP:A:1
#   SEN:TLT:PITCH      read hub tilt -> SEN:TLT:PITCH:15
#   SEN:TMR            read timer -> SEN:TMR:3
#   SEN:TMRR           reset timer

from hub import light_matrix, port
import hub, motor, motor_pair, time

# Try every known path to the center-button LED function.
# SPIKE Prime 3.x may expose it as a direct import or as hub.led attribute.
_hub_led = None
try:
    from hub import led as _hub_led
except Exception:
    _hub_led = getattr(hub, 'led', None)

try:
    import color_sensor, distance_sensor, force_sensor, color
    _clr_map = {}
    for _n in ('BLACK', 'RED', 'GREEN', 'YELLOW', 'BLUE', 'WHITE',
               'CYAN', 'MAGENTA', 'ORANGE', 'VIOLET', 'AZURE', 'NONE'):
        try:
            _clr_map[getattr(color, _n)] = _n
        except AttributeError:
            pass
    _sensors_ok = True
except Exception:
    _sensors_ok = False
    _clr_map = {}

light_matrix.set_pixel(2, 2, 100)   # centre LED = program running
tunnel = hub.config['module_tunnel']

PORTS = {'A': port.A, 'B': port.B, 'C': port.C,
         'D': port.D, 'E': port.E, 'F': port.F}

# Map from our image name (upper) to the exact light_matrix.IMAGE_* attribute name.
# The IMAGE_* constants are the most reliable way to call show_image().
# Fallback integer indices are kept for firmware that lacks these constants.
_IMG_CONST = {
    'HEART':       'IMAGE_HEART',
    'HEARTSMALL':  'IMAGE_HEART_SMALL',   # underscore in constant name
    'HAPPY':       'IMAGE_HAPPY',
    'SMILE':       'IMAGE_SMILE',
    'SAD':         'IMAGE_SAD',
    'CONFUSED':    'IMAGE_CONFUSED',
    'ANGRY':       'IMAGE_ANGRY',
    'ASLEEP':      'IMAGE_ASLEEP',
    'SURPRISED':   'IMAGE_SURPRISED',
    'YES':         'IMAGE_YES',
    'NO':          'IMAGE_NO',
    'ARROWNORTH':  'IMAGE_ARROW_N',        # abbreviated in constant name
    'ARROWEAST':   'IMAGE_ARROW_E',
    'ARROWSOUTH':  'IMAGE_ARROW_S',
    'ARROWWEST':   'IMAGE_ARROW_W',
}

# Integer index fallbacks (used if IMAGE_* constant is not present).
IMAGES = {
    'HEART': 0,
    'HEART_SMALL': 1, 'HEARTSMALL': 1,
    'HAPPY': 2, 'SMILE': 3, 'SAD': 4,
    'CONFUSED': 5, 'ANGRY': 6, 'ASLEEP': 7,
    'SURPRISED': 8, 'YES': 12, 'NO': 13,
    'ARROW_N': 16, 'ARROWNORTH': 16,
    'ARROW_E': 18, 'ARROWEAST': 18,
    'ARROW_S': 20, 'ARROWSOUTH': 20,
    'ARROW_W': 22, 'ARROWWEST': 22,
}

# hub.led() color index map (SPIKE Prime 3.x color constants, ~0-10 range).
# Do NOT use 0-255 RGB — hub.led() takes a single color index, not RGB.
_HUB_LED = {
    'BLACK': 0, 'MAGENTA': 1, 'VIOLET': 2, 'BLUE': 3,
    'AZURE': 4, 'CYAN': 5, 'GREEN': 6, 'YELLOW': 7,
    'ORANGE': 8, 'RED': 9, 'WHITE': 10,
}

_timer_start = time.ticks_ms()


def on_message(data):
    global _timer_start
    if not isinstance(data, str):
        data = ''.join(chr(b) for b in data)

    parts = data.split(':')
    resp = b'rdy'

    try:
        cmd = parts[0]

        # --- Motors ---
        if cmd == 'MTR' and len(parts) >= 3:
            p = parts[1].upper()
            act = parts[2].upper()
            if p in PORTS:
                if act == 'STOP':
                    motor.stop(PORTS[p])
                elif act in ('CW', 'CCW') and len(parts) >= 4:
                    spd = int(parts[3]) * 11
                    if act == 'CCW':
                        spd = -spd
                    motor.run(PORTS[p], spd)

        # --- Movement ---
        elif cmd == 'MOV' and len(parts) >= 2:
            sub = parts[1].upper()
            if sub == 'PAIR' and len(parts) >= 4:
                lp, rp = parts[2].upper(), parts[3].upper()
                if lp in PORTS and rp in PORTS:
                    motor_pair.pair(motor_pair.PAIR_1, PORTS[lp], PORTS[rp])
            elif sub == 'FWD' and len(parts) >= 3:
                motor_pair.move(motor_pair.PAIR_1, 0, velocity=int(parts[2]) * 11)
            elif sub == 'BWD' and len(parts) >= 3:
                motor_pair.move(motor_pair.PAIR_1, 0, velocity=-(int(parts[2]) * 11))
            elif sub == 'STEER' and len(parts) >= 4:
                # parts[2] is signed steering e.g. "+50" or "-50"
                steering = int(parts[2])
                spd = int(parts[3]) * 11
                motor_pair.move(motor_pair.PAIR_1, steering, velocity=spd)
            elif sub == 'STOP':
                motor_pair.stop(motor_pair.PAIR_1)

        # --- Light ---
        elif cmd == 'LGT' and len(parts) >= 2:
            sub = parts[1].upper()
            if sub == 'ON' and len(parts) >= 3:
                _n = parts[2].upper()
                # Look up the exact IMAGE_* constant name (handles underscores
                # like IMAGE_HEART_SMALL and abbreviations like IMAGE_ARROW_N)
                _const = _IMG_CONST.get(_n)
                if _const is not None:
                    try:
                        _img = getattr(light_matrix, _const)
                    except AttributeError:
                        _img = IMAGES.get(_n, 2)
                else:
                    _img = IMAGES.get(_n, 2)
                light_matrix.show_image(_img)
            elif sub == 'OFF':
                # Use set_pixel loop — reliable on all firmware versions
                for _x in range(5):
                    for _y in range(5):
                        light_matrix.set_pixel(_x, _y, 0)
            elif sub == 'TXT' and len(parts) >= 3:
                light_matrix.write(':'.join(parts[2:]))
            elif sub == 'PIX' and len(parts) >= 5:
                light_matrix.set_pixel(int(parts[2]), int(parts[3]), int(parts[4]))
            elif sub == 'BTN' and len(parts) >= 3:
                # Debug: track exactly what happens and return a diagnostic string.
                # Java logs it via logDebug("TunnelMessage: DBG:BTN:...").
                _btn_name = parts[2].upper()
                if _hub_led is None:
                    _dbg = 'no_func'
                else:
                    try:
                        import color as _clr
                        _c = getattr(_clr, _btn_name, None)
                        if _c is None:
                            _dbg = 'no_color_attr'
                        else:
                            _hub_led(_c)
                            _dbg = 'called_ok'
                    except Exception as _e:
                        _dbg = 'exc:' + str(_e)[:20]
                resp = ('DBG:BTN:' + _dbg).encode()

        # --- Sensors ---
        elif cmd == 'SEN' and len(parts) >= 2 and _sensors_ok:
            sub = parts[1].upper()

            if sub == 'CLR' and len(parts) >= 3:
                p = parts[2].upper()
                if p in PORTS:
                    try:
                        c = color_sensor.color(PORTS[p])
                        resp = ('SEN:CLR:' + p + ':' + _clr_map.get(c, str(c))).encode()
                    except Exception:
                        resp = ('SEN:CLR:' + p + ':NONE').encode()

            elif sub == 'DST' and len(parts) >= 3:
                p = parts[2].upper()
                if p in PORTS:
                    try:
                        resp = ('SEN:DST:' + p + ':' +
                                str(distance_sensor.distance(PORTS[p]))).encode()
                    except Exception:
                        resp = ('SEN:DST:' + p + ':-1').encode()

            elif sub == 'PRS' and len(parts) >= 3:
                p = parts[2].upper()
                if p in PORTS:
                    try:
                        resp = ('SEN:PRS:' + p + ':' +
                                str(force_sensor.force(PORTS[p]))).encode()
                    except Exception:
                        resp = ('SEN:PRS:' + p + ':0').encode()

            elif sub == 'ISP' and len(parts) >= 3:
                p = parts[2].upper()
                if p in PORTS:
                    try:
                        pressed = force_sensor.pressed(PORTS[p])
                        resp = ('SEN:ISP:' + p + ':' + ('1' if pressed else '0')).encode()
                    except Exception:
                        resp = ('SEN:ISP:' + p + ':0').encode()

            elif sub == 'TLT' and len(parts) >= 3:
                axis = parts[2].upper()
                try:
                    # tilt_angles() returns (pitch, roll, yaw) in decidegrees.
                    # Try hub.motion_sensor first, fall back to hub.imu for
                    # firmware variants that renamed the module.
                    try:
                        angles = hub.motion_sensor.tilt_angles()
                    except AttributeError:
                        angles = hub.imu.tilt_angles()
                    val = {'PITCH': angles[0], 'ROLL': angles[1], 'YAW': angles[2]}.get(axis.upper(), 0)
                    resp = ('SEN:TLT:' + axis + ':' + str(val // 10)).encode()
                except Exception:
                    resp = ('SEN:TLT:' + axis + ':0').encode()

            elif sub == 'TMR':
                elapsed = time.ticks_diff(time.ticks_ms(), _timer_start) // 1000
                resp = ('SEN:TMR:' + str(elapsed)).encode()

            elif sub == 'TMRR':
                _timer_start = time.ticks_ms()

    except Exception:
        resp = b'err'

    tunnel.send(resp)


tunnel.callback(on_message)
tunnel.send(b'rdy')

while True:
    pass
