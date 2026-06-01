package io.github.appinventor.legospikeprime;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;
import com.google.appinventor.components.runtime.util.YailList;

import org.json.JSONObject;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

@SimpleObject(external = true)
@DesignerComponent(version = 2,
    description = "Manages BLE connection to a LEGO SPIKE Prime hub. "
        + "Set BluetoothDevice, call StartScanning, then ConnectToHub. "
        + "HubConnected fires when the hub is ready — its parameters include "
        + "deviceType, sspVersion, availablePorts, and supportedEncodings.",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = "aiwebres/legospike.png")
public class LegoSpikeConnectivity extends AndroidNonvisibleComponent {

    private static final String LOG_TAG = "LegoSpikeConnectivity";

    // SPIKE Prime 3.x BLE UUIDs — DO NOT CHANGE (CLAUDE.md Rule 1)
    public static final String SPIKE_SERVICE_UUID = "0000fd02-0000-1000-8000-00805f9b34fb";
    public static final String RX_CHAR_UUID       = "0000fd02-0001-1000-8000-00805f9b34fb";
    public static final String TX_CHAR_UUID       = "0000fd02-0002-1000-8000-00805f9b34fb";

    private static final int    DEFAULT_MAX_PACKET_SIZE = 20;
    private static final int    CHUNK_DELAY_MS          = 100;
    private static final int    CONTROLLER_SLOT         = 0;
    private static final String PREFS_NAME              = "LegoSpikePrefs";

    // =========================================================================
    // Hub controller program embedded as a String.
    // Command protocol:
    //   MTR:A:CW:050     start motor A clockwise at 50%
    //   MTR:A:STOP       stop motor A
    //   MOV:PAIR:A:B     set movement motors A=left B=right
    //   MOV:FWD:050      move forward at 50%
    //   MOV:BWD:050      move backward at 50%
    //   MOV:STEER:+50:075 steer +50 at speed 75%
    //   MOV:STOP         stop movement
    //   LGT:ON:HAPPY     show image on 5x5 matrix
    //   LGT:OFF          turn off matrix
    //   LGT:TXT:Hello    write text on matrix
    //   LGT:PIX:2:2:100  set pixel brightness
    //   LGT:BTN:RED      set center button color
    //   SEN:CLR:A        read color on port A  -> SEN:CLR:A:RED
    //   SEN:DST:A        read distance on port A -> SEN:DST:A:150
    //   SEN:PRS:A        read pressure on port A -> SEN:PRS:A:50
    //   SEN:ISP:A        is pressed on port A -> SEN:ISP:A:1
    //   SEN:TLT:PITCH    read tilt pitch -> SEN:TLT:PITCH:15
    //   SEN:TMR          read timer -> SEN:TMR:3
    //   SEN:TMRR         reset timer
    // =========================================================================
    static final String HUB_CONTROLLER_PROGRAM =
        "# LEGO SPIKE Prime hub controller — SSP v0.8 edition.\n" +
        "#\n" +
        "# Wire format: SSP v0.8 json-utf8-newline over TunnelMessage (opcode 0x32).\n" +
        "# Each incoming frame is one newline-terminated JSON string.\n" +
        "# Each outgoing event is one newline-terminated JSON string.\n" +
        "#\n" +
        "# This program is the TYPE 2 \"bridge firmware\" for the Solaria platform.\n" +
        "# It runs entirely on the hub via the Python TunnelMessage facility.\n" +
        "#\n" +
        "# See: https://github.com/edcheng1010/solaria-hub/blob/main/spec/SSP-v0.8.md\n" +
        "\n" +
        "import hub, motor, motor_pair, time, math\n" +
        "from hub import light_matrix, port\n" +
        "\n" +
        "try:\n" +
        "    import json\n" +
        "    _json_ok = True\n" +
        "except ImportError:\n" +
        "    _json_ok = False\n" +
        "\n" +
        "try:\n" +
        "    import color_sensor, distance_sensor, force_sensor, color\n" +
        "    _CLR_MAP = {}\n" +
        "    for _n in ('BLACK', 'RED', 'GREEN', 'YELLOW', 'BLUE', 'WHITE',\n" +
        "               'CYAN', 'MAGENTA', 'ORANGE', 'VIOLET', 'AZURE', 'NONE'):\n" +
        "        try:\n" +
        "            _CLR_MAP[getattr(color, _n)] = _n.lower()\n" +
        "        except AttributeError:\n" +
        "            pass\n" +
        "    _sensors_ok = True\n" +
        "except Exception:\n" +
        "    _sensors_ok = False\n" +
        "    _CLR_MAP = {}\n" +
        "\n" +
        "# ---------------------------------------------------------------------------\n" +
        "# Constants\n" +
        "# ---------------------------------------------------------------------------\n" +
        "\n" +
        "PORTS = {'A': port.A, 'B': port.B, 'C': port.C,\n" +
        "         'D': port.D, 'E': port.E, 'F': port.F}\n" +
        "\n" +
        "# Status LED color name → color constant (SSP enum values)\n" +
        "_LED_COLORS = {\n" +
        "    'black': color.BLACK if hasattr(color, 'BLACK') else 0,\n" +
        "    'magenta': color.MAGENTA if hasattr(color, 'MAGENTA') else 1,\n" +
        "    'violet': color.VIOLET if hasattr(color, 'VIOLET') else 2,\n" +
        "    'blue': color.BLUE if hasattr(color, 'BLUE') else 3,\n" +
        "    'azure': color.AZURE if hasattr(color, 'AZURE') else 4,\n" +
        "    'cyan': color.CYAN if hasattr(color, 'CYAN') else 5,\n" +
        "    'green': color.GREEN if hasattr(color, 'GREEN') else 6,\n" +
        "    'yellow': color.YELLOW if hasattr(color, 'YELLOW') else 7,\n" +
        "    'orange': color.ORANGE if hasattr(color, 'ORANGE') else 8,\n" +
        "    'red': color.RED if hasattr(color, 'RED') else 9,\n" +
        "    'white': color.WHITE if hasattr(color, 'WHITE') else 10,\n" +
        "    'off': 0,\n" +
        "}\n" +
        "\n" +
        "# Image name → light_matrix constant\n" +
        "_IMG_CONST = {\n" +
        "    'HEART': 'IMAGE_HEART', 'HEARTSMALL': 'IMAGE_HEART_SMALL',\n" +
        "    'HAPPY': 'IMAGE_HAPPY', 'SMILE': 'IMAGE_SMILE', 'SAD': 'IMAGE_SAD',\n" +
        "    'CONFUSED': 'IMAGE_CONFUSED', 'ANGRY': 'IMAGE_ANGRY',\n" +
        "    'ASLEEP': 'IMAGE_ASLEEP', 'SURPRISED': 'IMAGE_SURPRISED',\n" +
        "    'YES': 'IMAGE_YES', 'NO': 'IMAGE_NO',\n" +
        "    'ARROWNORTH': 'IMAGE_ARROW_N', 'ARROWEAST': 'IMAGE_ARROW_E',\n" +
        "    'ARROWSOUTH': 'IMAGE_ARROW_S', 'ARROWWEST': 'IMAGE_ARROW_W',\n" +
        "}\n" +
        "_IMAGES_IDX = {\n" +
        "    'HEART': 0, 'HEARTSMALL': 1, 'HAPPY': 2, 'SMILE': 3, 'SAD': 4,\n" +
        "    'CONFUSED': 5, 'ANGRY': 6, 'ASLEEP': 7, 'SURPRISED': 8,\n" +
        "    'YES': 12, 'NO': 13, 'ARROWNORTH': 16, 'ARROWEAST': 18,\n" +
        "    'ARROWSOUTH': 20, 'ARROWWEST': 22,\n" +
        "}\n" +
        "\n" +
        "# ---------------------------------------------------------------------------\n" +
        "# State\n" +
        "# ---------------------------------------------------------------------------\n" +
        "\n" +
        "_timer_start = time.ticks_ms()\n" +
        "_mov_lp = None          # cached motor_pair left port (skip re-pair when same)\n" +
        "_mov_rp = None\n" +
        "# Orientation frame — body axes for each hub mounting face.\n" +
        "# Body frame: +X=Left(A/C/E), +Y=Front(USB), +Z=Top(display)\n" +
        "# Convention: pitch+ = ref face tilts forward; roll+ = left(A/C/E) up; yaw+ = CW\n" +
        "_ORIENT_FRAMES = {\n" +
        "    # Sensor coordinates: acc[0]=USB, acc[1]=A/C/E, acc[2]=Top\n" +
        "    # pitch=atan2(af,au), roll=atan2(al,au), yaw integrated negated\n" +
        "    'Top':        ( 0, 0, 1,   1, 0, 0,   0, 1, 0),\n" +
        "    'Bottom':     ( 0, 0,-1,   1, 0, 0,   0,-1, 0),\n" +
        "    'Front':      ( 1, 0, 0,   0, 0,-1,   0, 1, 0),\n" +
        "    'Back':       (-1, 0, 0,   0, 0, 1,   0, 1, 0),\n" +
        "    'Left side':  ( 0, 1, 0,   1, 0, 0,   0, 0,-1),\n" +
        "    'Right side': ( 0,-1, 0,   1, 0, 0,   0, 0, 1),\n" +
        "}\n" +
        "_orient_u = (0, 0, 1)\n" +
        "_orient_f = (1, 0, 0)\n" +
        "_orient_l = (0, 1, 0)\n" +
        "_yaw_acc  = 0.0\n" +
        "_yaw_zero = 0.0\n" +
        "# Complementary filter for pitch/roll. FALLBACK: set _CF_ALPHA=0.0 for pure accel.\n" +
        "_cf_pitch   = 0.0\n" +
        "_cf_roll    = 0.0\n" +
        "_cf_last_ms = 0\n" +
        "_CF_ALPHA   = 0.98\n" +
        "\n" +
        "# Map Python API orientation strings to LEGO face names.\n" +
        "_FACE_NAME_MAP = {\n" +
        "    'face_up':    'Top',\n" +
        "    'face_down':  'Bottom',\n" +
        "    'port_a_up':  'Left side',\n" +
        "    'port_a_down': 'Right side',\n" +
        "    'port_e_up':  'Front',\n" +
        "    'port_e_down': 'Back',\n" +
        "}\n" +
        "\n" +
        "# Gesture integer → SSP string name (SPIKE Prime 3.x constants)\n" +
        "_GESTURE_MAP = {0: 'tap', 1: 'double_tap', 2: 'shake', 3: 'fall'}\n" +
        "_BTN_CONST = {\n" +
        "    'left':  hub.button.LEFT  if hasattr(hub.button, 'LEFT')  else 1,\n" +
        "    'right': hub.button.RIGHT if hasattr(hub.button, 'RIGHT') else 2,\n" +
        "}\n" +
        "_GESTURE_POLL_TICKS = 20  # 20x10ms=200ms window; reduce to 5 for faster loop\n" +
        "\n" +
        "# Subscriptions: port_id -> {type, mode, interval_ms, min_change, last_ms, last_val}\n" +
        "_subscriptions = {}\n" +
        "_sys_subscriptions = {}  # metric -> {interval_ms, last_ms, last_val}\n" +
        "\n" +
        "_last_ping_ms = None     # None = heartbeat not yet started\n" +
        "_heartbeat_active = False\n" +
        "\n" +
        "# v0.8: cached volume for sound.read\n" +
        "_cached_volume = 50\n" +
        "\n" +
        "# v0.8: cached motor acceleration rates (port_id -> ms)\n" +
        "_motor_acceleration = {}\n" +
        "_movement_acceleration = None\n" +
        "\n" +
        "# Light matrix display state — enables software brightness scaling and rotation.\n" +
        "# _matrix_pixels[row][col], values 0-100 natural brightness (before scale).\n" +
        "_matrix_pixels = [[0] * 5 for _ in range(5)]\n" +
        "_matrix_brightness = 100   # global scale 0-100\n" +
        "_matrix_orientation = 0    # degrees CW: 0, 90, 180, 270\n" +
        "\n" +
        "# Known 5x5 pixel patterns for predefined images (row-major, 0=off, 100=on).\n" +
        "_IMAGE_PIXELS = {\n" +
        "    'HEART':      [[0,100,0,100,0],[100,100,100,100,100],[100,100,100,100,100],[0,100,100,100,0],[0,0,100,0,0]],\n" +
        "    'HEARTSMALL': [[0,0,0,0,0],[0,100,0,100,0],[0,100,100,100,0],[0,0,100,0,0],[0,0,0,0,0]],\n" +
        "    'HAPPY':      [[0,0,0,0,0],[0,100,0,100,0],[0,0,0,0,0],[100,0,0,0,100],[0,100,100,100,0]],\n" +
        "    'SMILE':      [[0,0,0,0,0],[0,0,0,0,0],[0,100,0,100,0],[100,0,0,0,100],[0,100,100,100,0]],\n" +
        "    'SAD':        [[0,0,0,0,0],[0,100,0,100,0],[0,0,0,0,0],[0,100,100,100,0],[100,0,0,0,100]],\n" +
        "    'CONFUSED':   [[0,0,0,0,0],[0,100,0,100,0],[0,0,0,0,0],[0,0,100,0,0],[0,100,0,0,0]],\n" +
        "    'ANGRY':      [[100,0,0,0,100],[0,100,0,100,0],[0,0,0,0,0],[0,100,100,100,0],[100,0,100,0,100]],\n" +
        "    'ASLEEP':     [[0,0,0,0,0],[100,100,0,100,100],[0,0,0,0,0],[0,100,100,100,0],[0,0,0,0,0]],\n" +
        "    'SURPRISED':  [[0,100,0,100,0],[0,0,0,0,0],[0,0,100,0,0],[0,100,0,100,0],[0,0,100,0,0]],\n" +
        "    'YES':        [[0,0,0,0,0],[0,0,0,0,100],[0,0,0,100,0],[100,0,100,0,0],[0,100,0,0,0]],\n" +
        "    'NO':         [[100,0,0,0,100],[0,100,0,100,0],[0,0,100,0,0],[0,100,0,100,0],[100,0,0,0,100]],\n" +
        "    'ARROWNORTH': [[0,0,100,0,0],[0,100,100,100,0],[100,0,100,0,100],[0,0,100,0,0],[0,0,100,0,0]],\n" +
        "    'ARROWEAST':  [[0,0,100,0,0],[0,0,0,100,0],[100,100,100,100,100],[0,0,0,100,0],[0,0,100,0,0]],\n" +
        "    'ARROWSOUTH': [[0,0,100,0,0],[0,0,100,0,0],[100,0,100,0,100],[0,100,100,100,0],[0,0,100,0,0]],\n" +
        "    'ARROWWEST':  [[0,0,100,0,0],[0,100,0,0,0],[100,100,100,100,100],[0,100,0,0,0],[0,0,100,0,0]],\n" +
        "}\n" +
        "\n" +
        "\n" +
        "def _render_matrix():\n" +
        "    \"\"\"Redraw all 25 pixels applying brightness scale and orientation transform.\"\"\"\n" +
        "    for row in range(5):\n" +
        "        for col in range(5):\n" +
        "            o = _matrix_orientation\n" +
        "            if o == 90:\n" +
        "                px, py = 4 - row, col\n" +
        "            elif o == 180:\n" +
        "                px, py = 4 - col, 4 - row\n" +
        "            elif o == 270:\n" +
        "                px, py = row, 4 - col\n" +
        "            else:\n" +
        "                px, py = col, row\n" +
        "            scaled = int(_matrix_pixels[row][col] * _matrix_brightness / 100)\n" +
        "            light_matrix.set_pixel(px, py, scaled)\n" +
        "\n" +
        "# ---------------------------------------------------------------------------\n" +
        "# Tunnel setup\n" +
        "# ---------------------------------------------------------------------------\n" +
        "\n" +
        "tunnel = hub.config['module_tunnel']\n" +
        "\n" +
        "\n" +
        "# ---------------------------------------------------------------------------\n" +
        "# Helpers\n" +
        "# ---------------------------------------------------------------------------\n" +
        "\n" +
        "def _send(obj):\n" +
        "    \"\"\"Send a JSON event to the client.\"\"\"\n" +
        "    try:\n" +
        "        tunnel.send((json.dumps(obj) + '\\n').encode())\n" +
        "    except Exception:\n" +
        "        pass\n" +
        "\n" +
        "\n" +
        "def _send_error(code, message, request_id=None):\n" +
        "    err = {'event': 'error', 'code': code, 'message': message}\n" +
        "    if request_id is not None:\n" +
        "        err['request_id'] = request_id\n" +
        "    _send(err)\n" +
        "\n" +
        "\n" +
        "def _sensor_event(port_id, sensor_type, value, request_id=None):\n" +
        "    ev = {'event': 'sensor', 'port': port_id, 'type': sensor_type, 'value': value}\n" +
        "    if request_id is not None:\n" +
        "        ev['request_id'] = request_id\n" +
        "    _send(ev)\n" +
        "\n" +
        "\n" +
        "def _system_event(metric, value):\n" +
        "    _send({'event': 'system', 'metric': metric, 'value': value})\n" +
        "\n" +
        "\n" +
        "def _dot(a, b):\n" +
        "    return a[0]*b[0] + a[1]*b[1] + a[2]*b[2]\n" +
        "\n" +
        "\n" +
        "def _update_orientation(now):\n" +
        "    # FALLBACK to pure accel: set _CF_ALPHA=0.0 (gyro disabled, pure accel each tick).\n" +
        "    global _cf_pitch, _cf_roll, _cf_last_ms, _yaw_acc\n" +
        "    try:\n" +
        "        a = hub.motion_sensor.acceleration()\n" +
        "        ax, ay, az = a[0], a[1], a[2]\n" +
        "        w = hub.motion_sensor.angular_velocity()\n" +
        "        wx, wy, wz = w[0] / 10.0, w[1] / 10.0, w[2] / 10.0\n" +
        "        au = _dot(_orient_u, (ax, ay, az))\n" +
        "        accel_pitch = math.degrees(math.atan2(_dot(_orient_f, (ax, ay, az)), au))\n" +
        "        accel_roll  = math.degrees(math.atan2(_dot(_orient_l, (ax, ay, az)), au))\n" +
        "        dt_ms = time.ticks_diff(now, _cf_last_ms) if _cf_last_ms else 0\n" +
        "        _cf_last_ms = now\n" +
        "        if 0 < dt_ms <= 500 and _CF_ALPHA > 0.0:\n" +
        "            dt_s = dt_ms / 1000.0\n" +
        "            _cf_pitch = _CF_ALPHA * (_cf_pitch - _dot(_orient_l, (wx,wy,wz)) * dt_s) + (1.0 - _CF_ALPHA) * accel_pitch\n" +
        "            _cf_roll  = _CF_ALPHA * (_cf_roll  + _dot(_orient_f, (wx,wy,wz)) * dt_s) + (1.0 - _CF_ALPHA) * accel_roll\n" +
        "        else:\n" +
        "            _cf_pitch = accel_pitch\n" +
        "            _cf_roll  = accel_roll\n" +
        "        if abs(_orient_u[2]) > 0.5:\n" +
        "            raw = hub.motion_sensor.tilt_angles()\n" +
        "            _yaw_acc = -raw[0] / 10.0\n" +
        "        elif 0 < dt_ms <= 500:\n" +
        "            rate = -_dot(_orient_u, (wx, wy, wz))\n" +
        "            if abs(rate) > 1.5:\n" +
        "                _yaw_acc += rate * (dt_ms / 1000.0)\n" +
        "    except Exception:\n" +
        "        pass\n" +
        "\n" +
        "\n" +
        "def _tilt_angles():\n" +
        "    return (int(round(_cf_pitch)),\n" +
        "            int(round(_cf_roll)),\n" +
        "            int(round(_yaw_acc - _yaw_zero)))\n" +
        "\n" +
        "\n" +
        "def _ensure_pair(lp, rp):\n" +
        "    \"\"\"Re-pair motor pair only when ports change.\"\"\"\n" +
        "    global _mov_lp, _mov_rp\n" +
        "    if lp != _mov_lp or rp != _mov_rp:\n" +
        "        try:\n" +
        "            motor_pair.pair(motor_pair.PAIR_1, PORTS[lp], PORTS[rp])\n" +
        "        except Exception:\n" +
        "            pass\n" +
        "        _mov_lp, _mov_rp = lp, rp\n" +
        "\n" +
        "\n" +
        "def _show_image(name):\n" +
        "    global _matrix_pixels\n" +
        "    n = name.upper()\n" +
        "    if n in _IMAGE_PIXELS:\n" +
        "        # Use our pixel map — supports brightness scaling and rotation.\n" +
        "        _matrix_pixels = [row[:] for row in _IMAGE_PIXELS[n]]\n" +
        "        _render_matrix()\n" +
        "        return\n" +
        "    # Fallback: let firmware render (brightness/rotation won't apply).\n" +
        "    const = _IMG_CONST.get(n)\n" +
        "    if const is not None:\n" +
        "        try:\n" +
        "            img = getattr(light_matrix, const)\n" +
        "            light_matrix.show_image(img)\n" +
        "            return\n" +
        "        except AttributeError:\n" +
        "            pass\n" +
        "    idx = _IMAGES_IDX.get(n, 2)\n" +
        "    light_matrix.show_image(idx)\n" +
        "\n" +
        "\n" +
        "def _face_orientation():\n" +
        "    \"\"\"Return LEGO face name (Top/Bottom/Front/Back/Left side/Right side) from IMU.\"\"\"\n" +
        "    try:\n" +
        "        try:\n" +
        "            raw = hub.motion_sensor.get_orientation()\n" +
        "            return _FACE_NAME_MAP.get(raw, raw)\n" +
        "        except AttributeError:\n" +
        "            pass\n" +
        "        try:\n" +
        "            a = hub.motion_sensor.acceleration()\n" +
        "            ax, ay, az = abs(a[0]), abs(a[1]), abs(a[2])\n" +
        "            if az >= ax and az >= ay:\n" +
        "                return 'Top' if a[2] > 0 else 'Bottom'\n" +
        "            elif ax >= ay:\n" +
        "                return 'Front' if a[0] > 0 else 'Back'\n" +
        "            else:\n" +
        "                return 'Left side' if a[1] > 0 else 'Right side'\n" +
        "        except Exception:\n" +
        "            return 'Top'\n" +
        "    except Exception:\n" +
        "        return 'Top'\n" +
        "\n" +
        "\n" +
        "def _angular_velocity():\n" +
        "    \"\"\"Return angular velocity {x, y, z} in deg/s. Hub returns decideg/s — divide by 10.\"\"\"\n" +
        "    try:\n" +
        "        av = hub.motion_sensor.angular_velocity()\n" +
        "        return {'x': av[0] / 10.0, 'y': av[1] / 10.0, 'z': av[2] / 10.0}\n" +
        "    except Exception:\n" +
        "        return {'x': 0, 'y': 0, 'z': 0}\n" +
        "\n" +
        "\n" +
        "def _read_sensor_value(port_id, sensor_type):\n" +
        "    \"\"\"Reads a sensor value. Returns None on error.\"\"\"\n" +
        "    # IMU-specific types routed directly\n" +
        "    if port_id == 'imu' or sensor_type in ('pitch', 'roll', 'yaw',\n" +
        "                                             'face_orientation', 'angular_velocity',\n" +
        "                                             'acceleration', 'gesture'):\n" +
        "        try:\n" +
        "            if sensor_type == 'pitch':          return _tilt_angles()[0]\n" +
        "            if sensor_type == 'roll':           return _tilt_angles()[1]\n" +
        "            if sensor_type == 'yaw':            return _tilt_angles()[2]\n" +
        "            if sensor_type == 'face_orientation': return _face_orientation()\n" +
        "            if sensor_type == 'angular_velocity': return _angular_velocity()\n" +
        "            if sensor_type == 'gesture':\n" +
        "                try:\n" +
        "                    return _GESTURE_MAP.get(hub.motion_sensor.gesture(), None)\n" +
        "                except Exception:\n" +
        "                    return None\n" +
        "            if sensor_type == 'acceleration':\n" +
        "                try:\n" +
        "                    # Hub returns mg — convert to m/s² (divide by 100)\n" +
        "                    acc = hub.motion_sensor.acceleration()\n" +
        "                    return {'x': round(acc[0] / 100.0, 2),\n" +
        "                            'y': round(acc[1] / 100.0, 2),\n" +
        "                            'z': round(acc[2] / 100.0, 2)}\n" +
        "                except Exception:\n" +
        "                    return {'x': 0, 'y': 0, 'z': 0}\n" +
        "        except Exception:\n" +
        "            return None\n" +
        "\n" +
        "    p = PORTS.get(port_id.upper())\n" +
        "    if p is None:\n" +
        "        return None\n" +
        "\n" +
        "    # Motor position / speed reading — try multiple FW 3.x function names\n" +
        "    if sensor_type == 'position':\n" +
        "        # Cumulative position since last reset (can exceed 360 or be negative)\n" +
        "        for fn_name in ('relative_position', 'get_position'):\n" +
        "            fn = getattr(motor, fn_name, None)\n" +
        "            if fn is not None:\n" +
        "                try:\n" +
        "                    return fn(p)\n" +
        "                except Exception:\n" +
        "                    continue\n" +
        "        return None\n" +
        "    if sensor_type == 'absolute_position':\n" +
        "        # Current orientation 0-359\n" +
        "        fn = getattr(motor, 'absolute_position', None)\n" +
        "        if fn is not None:\n" +
        "            try:\n" +
        "                return fn(p)\n" +
        "            except Exception:\n" +
        "                return None\n" +
        "        # Fallback: cumulative position mod 360\n" +
        "        for fn_name in ('relative_position', 'get_position'):\n" +
        "            fn = getattr(motor, fn_name, None)\n" +
        "            if fn is not None:\n" +
        "                try:\n" +
        "                    return fn(p) % 360\n" +
        "                except Exception:\n" +
        "                    continue\n" +
        "        return None\n" +
        "    if sensor_type == 'speed':\n" +
        "        # On observed SPIKE Prime 3.x firmware, motor.velocity(port) returns the\n" +
        "        # current speed already in PERCENT (-100..100), not deg/s as the docs claim.\n" +
        "        # At 100% speed setting it reads ~88 due to closed-loop tracking.\n" +
        "        fn = getattr(motor, 'velocity', None) or getattr(motor, 'get_velocity', None)\n" +
        "        if fn is not None:\n" +
        "            try:\n" +
        "                return int(fn(p))\n" +
        "            except Exception:\n" +
        "                pass\n" +
        "        return None\n" +
        "\n" +
        "    if not _sensors_ok:\n" +
        "        return None\n" +
        "    try:\n" +
        "        if sensor_type == 'color':\n" +
        "            c = color_sensor.color(p)\n" +
        "            return _CLR_MAP.get(c, str(c))\n" +
        "        elif sensor_type == 'rgb':\n" +
        "            try:\n" +
        "                rgb = color_sensor.rgbi(p)\n" +
        "                intensity = rgb[3] if rgb[3] > 0 else 1\n" +
        "                return [min(255, int(rgb[i] * 255 // intensity)) for i in range(3)]\n" +
        "            except Exception:\n" +
        "                return [0, 0, 0]\n" +
        "        elif sensor_type == 'reflected':\n" +
        "            return color_sensor.reflection(p)\n" +
        "        elif sensor_type == 'ambient':\n" +
        "            return color_sensor.ambient_light(p)\n" +
        "        elif sensor_type == 'distance':\n" +
        "            return distance_sensor.distance(p)\n" +
        "        elif sensor_type == 'force':\n" +
        "            return force_sensor.force(p)\n" +
        "        elif sensor_type == 'touched':\n" +
        "            return force_sensor.pressed(p)\n" +
        "    except Exception:\n" +
        "        return None\n" +
        "\n" +
        "\n" +
        "def _read_system_metric(metric):\n" +
        "    \"\"\"Reads a system metric value. Returns None on error.\"\"\"\n" +
        "    try:\n" +
        "        if metric == 'battery':\n" +
        "            try:\n" +
        "                return hub.battery.level()\n" +
        "            except AttributeError:\n" +
        "                return hub.battery.voltage() // 40  # rough % from mV\n" +
        "        elif metric == 'temperature':\n" +
        "            try:\n" +
        "                return hub.temperature()\n" +
        "            except AttributeError:\n" +
        "                return None\n" +
        "        elif metric == 'charging':\n" +
        "            try:\n" +
        "                return hub.battery.charger_detect()\n" +
        "            except AttributeError:\n" +
        "                return False\n" +
        "        elif metric == 'connection_rssi':\n" +
        "            return None  # not accessible from Python\n" +
        "    except Exception:\n" +
        "        return None\n" +
        "    return None\n" +
        "\n" +
        "\n" +
        "# ---------------------------------------------------------------------------\n" +
        "# Capability declaration\n" +
        "# ---------------------------------------------------------------------------\n" +
        "\n" +
        "def _build_capability():\n" +
        "    ports_list = []\n" +
        "\n" +
        "    # Motor ports: try each port; include as motor if present\n" +
        "    for pid in ('A', 'B', 'C', 'D', 'E', 'F'):\n" +
        "        try:\n" +
        "            p = PORTS[pid]\n" +
        "            # probe: if port.device is None, nothing connected\n" +
        "            device = getattr(p, 'device', None)\n" +
        "            if device is None:\n" +
        "                continue\n" +
        "            ports_list.append({\n" +
        "                'id': pid,\n" +
        "                'type': 'motor',\n" +
        "                'features': ['speed', 'position', 'stall', 'power', 'acceleration'],\n" +
        "                'goto_modes': ['absolute', 'relative'],\n" +
        "                'constraints': {\n" +
        "                    'speed':        {'type': 'int', 'min': -100, 'max': 100},\n" +
        "                    'position':     {'type': 'int', 'min': 0, 'max': 359, 'wraps': True},\n" +
        "                    'acceleration': {'type': 'int', 'min': 0, 'max': 10000},\n" +
        "                },\n" +
        "            })\n" +
        "        except Exception:\n" +
        "            pass\n" +
        "\n" +
        "    # Display port (always present)\n" +
        "    ports_list.append({\n" +
        "        'id': 'display',\n" +
        "        'type': 'display',\n" +
        "        'width': 5, 'height': 5, 'depth': 'grayscale',\n" +
        "        'features': ['pixel', 'image', 'text', 'brightness', 'orientation'],\n" +
        "        # 'touch' feature omitted until FW support is verified\n" +
        "    })\n" +
        "\n" +
        "    # Status LED (always present)\n" +
        "    ports_list.append({\n" +
        "        'id': 'status',\n" +
        "        'type': 'led',\n" +
        "        'features': ['set'],\n" +
        "        'constraints': {\n" +
        "            'color': {'type': 'enum', 'values': list(_LED_COLORS.keys())},\n" +
        "        },\n" +
        "    })\n" +
        "\n" +
        "    # IMU (always present on SPIKE Prime 3.x) — v0.7/v0.8 features\n" +
        "    ports_list.append({\n" +
        "        'id': 'imu',\n" +
        "        'type': 'orientation',\n" +
        "        'features': ['pitch', 'roll', 'yaw', 'gesture', 'face_orientation', 'angular_velocity'],\n" +
        "        'constraints': {\n" +
        "            'gesture': {\n" +
        "                'type': 'enum',\n" +
        "                'values': ['shake', 'tap', 'double_tap', 'fall'],\n" +
        "            },\n" +
        "            'face_orientation': {\n" +
        "                'type': 'enum',\n" +
        "                'values': ['face_up', 'face_down', 'port_a_up', 'port_a_down',\n" +
        "                           'port_e_up', 'port_e_down'],\n" +
        "            },\n" +
        "        },\n" +
        "    })\n" +
        "\n" +
        "    # Speaker — v0.8: volume + sound_wait_supported\n" +
        "    ports_list.append({\n" +
        "        'id': 'speaker',\n" +
        "        'type': 'speaker',\n" +
        "        'features': ['beep', 'volume'],\n" +
        "        'sound_wait_supported': True,\n" +
        "        # 'builtin' and 'midi' features added after FW API verification\n" +
        "    })\n" +
        "\n" +
        "    return {\n" +
        "        'type': 'capability',\n" +
        "        'device': 'spike-prime',\n" +
        "        'firmware': '3.x',\n" +
        "        'ssp_version': '0.8',\n" +
        "        'encodings': ['json-utf8-newline'],\n" +
        "        'supports_batch': False,\n" +
        "        'tank_drive': True,\n" +
        "        'system_metrics': [\n" +
        "            'battery', 'charging', 'temperature',\n" +
        "            'button.left', 'button.right', 'button.center',\n" +
        "        ],\n" +
        "        'ports': ports_list,\n" +
        "    }\n" +
        "\n" +
        "\n" +
        "# ---------------------------------------------------------------------------\n" +
        "# Command handlers\n" +
        "# ---------------------------------------------------------------------------\n" +
        "\n" +
        "def _handle_motor(cmd, obj, req_id):\n" +
        "    action = cmd.split('.')[1]  # run, stop, goto, reset, set_acceleration\n" +
        "    port_id = obj.get('port', '').upper()\n" +
        "\n" +
        "    # set_acceleration doesn't need a physical port\n" +
        "    if action == 'set_acceleration':\n" +
        "        rate = int(obj.get('rate', 500))\n" +
        "        _motor_acceleration[port_id] = rate\n" +
        "        # SPIKE FW may not expose hardware-level accel; cache client-side for now\n" +
        "        return\n" +
        "\n" +
        "    p = PORTS.get(port_id)\n" +
        "    if p is None:\n" +
        "        _send_error(201, 'Unknown port: ' + port_id, req_id)\n" +
        "        return\n" +
        "\n" +
        "    try:\n" +
        "        if action == 'run':\n" +
        "            raw_speed = int(obj.get('speed', 0))\n" +
        "            mode = obj.get('mode', 'speed')\n" +
        "\n" +
        "            # Power mode: open-loop raw duty cycle, no velocity feedback.\n" +
        "            # SPIKE FW 3.x exposes this differently across versions — try known APIs.\n" +
        "            if mode == 'power':\n" +
        "                # raw_speed is -100..+100 percent duty cycle\n" +
        "                power_pct = max(-100, min(100, raw_speed))\n" +
        "                attempts = [\n" +
        "                    lambda: motor.start_at_power(p, power_pct),\n" +
        "                    lambda: motor.run(p, power_pct, mode=getattr(motor, 'POWER', 1)),\n" +
        "                    # Last resort: scale to velocity range (approximate)\n" +
        "                    lambda: motor.run(p, power_pct * 11),\n" +
        "                ]\n" +
        "                for attempt in attempts:\n" +
        "                    try:\n" +
        "                        attempt()\n" +
        "                        break\n" +
        "                    except (AttributeError, TypeError):\n" +
        "                        continue\n" +
        "                    except Exception as e:\n" +
        "                        _send_error(301, 'motor.run power: ' + str(e), req_id)\n" +
        "                        break\n" +
        "                return\n" +
        "\n" +
        "            spd = raw_speed * 11\n" +
        "            dur = obj.get('duration')\n" +
        "            unit = obj.get('duration_unit', 'ms')\n" +
        "            # Apply cached acceleration for timed runs (SPIKE FW supports kwarg)\n" +
        "            accel = _motor_acceleration.get(port_id.upper(), None)\n" +
        "            if dur is not None:\n" +
        "                dur = int(dur)\n" +
        "                accel_kwargs = {'acceleration': accel, 'deceleration': accel} if accel else {}\n" +
        "                try:\n" +
        "                    if unit == 'ms':\n" +
        "                        motor.run_for_time(p, dur, spd, **accel_kwargs)\n" +
        "                    elif unit == 'degrees':\n" +
        "                        motor.run_for_degrees(p, dur, spd, **accel_kwargs)\n" +
        "                    elif unit == 'rotations':\n" +
        "                        motor.run_for_degrees(p, dur * 360, spd, **accel_kwargs)\n" +
        "                except TypeError:\n" +
        "                    # FW version doesn't support acceleration kwargs — run without\n" +
        "                    if unit == 'ms':\n" +
        "                        motor.run_for_time(p, dur, spd)\n" +
        "                    elif unit == 'degrees':\n" +
        "                        motor.run_for_degrees(p, dur, spd)\n" +
        "                    elif unit == 'rotations':\n" +
        "                        motor.run_for_degrees(p, dur * 360, spd)\n" +
        "            else:\n" +
        "                # Indefinite run — try acceleration kwarg first (newer FW), fall back\n" +
        "                if accel:\n" +
        "                    try:\n" +
        "                        motor.run(p, spd, acceleration=accel)\n" +
        "                    except TypeError:\n" +
        "                        motor.run(p, spd)\n" +
        "                else:\n" +
        "                    motor.run(p, spd)\n" +
        "\n" +
        "        elif action == 'stop':\n" +
        "            stop_action = obj.get('stop_action', 'brake')\n" +
        "            # Map our string to the FW constant if available\n" +
        "            stop_const = None\n" +
        "            if stop_action == 'coast':\n" +
        "                stop_const = getattr(motor, 'COAST', None)\n" +
        "            elif stop_action == 'hold':\n" +
        "                stop_const = getattr(motor, 'HOLD', None)\n" +
        "            elif stop_action == 'brake':\n" +
        "                stop_const = getattr(motor, 'BRAKE', None) or getattr(motor, 'SMART_BRAKING', None)\n" +
        "            # Try with kwarg, then positional, then plain stop\n" +
        "            if stop_const is not None:\n" +
        "                try:\n" +
        "                    motor.stop(p, stop=stop_const)\n" +
        "                except TypeError:\n" +
        "                    try:\n" +
        "                        motor.stop(p, stop_const)\n" +
        "                    except TypeError:\n" +
        "                        motor.stop(p)\n" +
        "            else:\n" +
        "                motor.stop(p)\n" +
        "\n" +
        "        elif action == 'goto':\n" +
        "            pos = int(obj.get('position', 0))\n" +
        "            spd = abs(int(obj.get('speed', 50))) * 11\n" +
        "            goto_mode = obj.get('mode', 'absolute')\n" +
        "            if goto_mode == 'relative':\n" +
        "                motor.run_for_degrees(p, pos, spd)\n" +
        "            else:\n" +
        "                # Absolute goto via delta-of-run_for_degrees workaround.\n" +
        "                # SPIKE FW 3.x absolute-position APIs vary too much across versions\n" +
        "                # (run_to_position vs run_to_absolute_position with different signatures),\n" +
        "                # but motor.run_for_degrees is reliably present. We compute the shortest\n" +
        "                # delta from current absolute position to the target and run that.\n" +
        "                target = pos % 360\n" +
        "                current = None\n" +
        "                # Try canonical FW 3.x function names for current position\n" +
        "                for fn_name in ('absolute_position', 'relative_position'):\n" +
        "                    fn = getattr(motor, fn_name, None)\n" +
        "                    if fn is not None:\n" +
        "                        try:\n" +
        "                            current = fn(p) % 360\n" +
        "                            break\n" +
        "                        except Exception:\n" +
        "                            pass\n" +
        "                if current is None:\n" +
        "                    _send_error(301, 'goto: cannot read current motor position', req_id)\n" +
        "                else:\n" +
        "                    delta = target - current\n" +
        "                    if delta > 180:   delta -= 360\n" +
        "                    elif delta < -180: delta += 360\n" +
        "                    try:\n" +
        "                        motor.run_for_degrees(p, delta, spd)\n" +
        "                    except Exception as e:\n" +
        "                        _send_error(301, 'goto failed: %s: %s' %\n" +
        "                                    (type(e).__name__, str(e)), req_id)\n" +
        "\n" +
        "        elif action == 'reset':\n" +
        "            motor.reset_relative_position(p, 0)\n" +
        "\n" +
        "    except Exception as e:\n" +
        "        _send_error(301, 'Motor error: ' + str(e), req_id)\n" +
        "\n" +
        "\n" +
        "def _handle_movement(cmd, obj, req_id):\n" +
        "    global _movement_acceleration\n" +
        "    action = cmd.split('.')[1]  # configure, drive, turn, stop, set_acceleration\n" +
        "\n" +
        "    try:\n" +
        "        if action == 'set_acceleration':\n" +
        "            _movement_acceleration = int(obj.get('rate', 500))\n" +
        "            return\n" +
        "\n" +
        "        if action == 'configure':\n" +
        "            lp = obj.get('left', '').upper()\n" +
        "            rp = obj.get('right', '').upper()\n" +
        "            if lp in PORTS and rp in PORTS:\n" +
        "                _ensure_pair(lp, rp)\n" +
        "\n" +
        "        elif action == 'drive':\n" +
        "            lp = obj.get('left', _mov_lp or 'A').upper()\n" +
        "            rp = obj.get('right', _mov_rp or 'B').upper()\n" +
        "            if lp in PORTS and rp in PORTS:\n" +
        "                _ensure_pair(lp, rp)\n" +
        "\n" +
        "            # v0.7 tank drive: explicit left_speed / right_speed\n" +
        "            accel = _movement_acceleration\n" +
        "            accel_kw = {'acceleration': accel, 'deceleration': accel} if accel is not None else {}\n" +
        "            if 'left_speed' in obj or 'right_speed' in obj:\n" +
        "                l_vel = int(obj.get('left_speed', 0)) * 11\n" +
        "                r_vel = int(obj.get('right_speed', 0)) * 11\n" +
        "                try:\n" +
        "                    motor_pair.move_tank(motor_pair.PAIR_1, l_vel, r_vel, **accel_kw)\n" +
        "                except TypeError:\n" +
        "                    motor_pair.move_tank(motor_pair.PAIR_1, l_vel, r_vel)\n" +
        "            else:\n" +
        "                steering = int(obj.get('steering', 0))\n" +
        "                vel = int(obj.get('speed', 50)) * 11\n" +
        "                dur = obj.get('duration')\n" +
        "                unit = obj.get('duration_unit', 'ms')\n" +
        "                if dur is not None:\n" +
        "                    dur = int(dur)\n" +
        "                    try:\n" +
        "                        if unit == 'degrees':\n" +
        "                            motor_pair.move_for_degrees(motor_pair.PAIR_1, dur, steering, velocity=vel, **accel_kw)\n" +
        "                        elif unit == 'rotations':\n" +
        "                            motor_pair.move_for_degrees(motor_pair.PAIR_1, dur * 360, steering, velocity=vel, **accel_kw)\n" +
        "                        else:\n" +
        "                            motor_pair.move_for_time(motor_pair.PAIR_1, dur, steering, velocity=vel, **accel_kw)\n" +
        "                    except TypeError:\n" +
        "                        if unit == 'degrees':\n" +
        "                            motor_pair.move_for_degrees(motor_pair.PAIR_1, dur, steering, velocity=vel)\n" +
        "                        elif unit == 'rotations':\n" +
        "                            motor_pair.move_for_degrees(motor_pair.PAIR_1, dur * 360, steering, velocity=vel)\n" +
        "                        else:\n" +
        "                            motor_pair.move_for_time(motor_pair.PAIR_1, dur, steering, velocity=vel)\n" +
        "                else:\n" +
        "                    try:\n" +
        "                        motor_pair.move(motor_pair.PAIR_1, steering, velocity=vel, **accel_kw)\n" +
        "                    except TypeError:\n" +
        "                        motor_pair.move(motor_pair.PAIR_1, steering, velocity=vel)\n" +
        "\n" +
        "        elif action == 'turn':\n" +
        "            angle = int(obj.get('angle', 90))\n" +
        "            vel = int(obj.get('speed', 50)) * 11\n" +
        "            accel = _movement_acceleration\n" +
        "            accel_kw = {'acceleration': accel, 'deceleration': accel} if accel is not None else {}\n" +
        "            try:\n" +
        "                motor_pair.move_for_degrees(motor_pair.PAIR_1, angle, 0, velocity=vel, **accel_kw)\n" +
        "            except TypeError:\n" +
        "                motor_pair.move_for_degrees(motor_pair.PAIR_1, angle, 0, velocity=vel)\n" +
        "\n" +
        "        elif action == 'stop':\n" +
        "            stop_action = obj.get('stop_action', 'brake')\n" +
        "            stop_const = None\n" +
        "            if stop_action == 'coast':\n" +
        "                stop_const = getattr(motor_pair, 'COAST', None)\n" +
        "            elif stop_action == 'hold':\n" +
        "                stop_const = getattr(motor_pair, 'HOLD', None)\n" +
        "            elif stop_action == 'brake':\n" +
        "                stop_const = getattr(motor_pair, 'BRAKE', None) or getattr(motor_pair, 'SMART_BRAKING', None)\n" +
        "            if stop_const is not None:\n" +
        "                try:\n" +
        "                    motor_pair.stop(motor_pair.PAIR_1, stop=stop_const)\n" +
        "                except TypeError:\n" +
        "                    try:\n" +
        "                        motor_pair.stop(motor_pair.PAIR_1, stop_const)\n" +
        "                    except TypeError:\n" +
        "                        motor_pair.stop(motor_pair.PAIR_1)\n" +
        "            else:\n" +
        "                motor_pair.stop(motor_pair.PAIR_1)\n" +
        "\n" +
        "    except Exception as e:\n" +
        "        _send_error(301, 'Movement error: ' + str(e), req_id)\n" +
        "\n" +
        "\n" +
        "def _handle_led(cmd, obj, req_id):\n" +
        "    global _matrix_pixels, _matrix_brightness, _matrix_orientation\n" +
        "    parts = cmd.split('.')\n" +
        "    if len(parts) == 2 and parts[1] == 'distance':\n" +
        "        p = PORTS.get(obj.get('port', '').upper())\n" +
        "        if p is not None:\n" +
        "            try:\n" +
        "                distance_sensor.show(p, [int(obj.get('tl',0)), int(obj.get('tr',0)),\n" +
        "                                         int(obj.get('bl',0)), int(obj.get('br',0))])\n" +
        "            except Exception as e:\n" +
        "                _send_error(301, 'distance LED error: ' + str(e), req_id)\n" +
        "        return\n" +
        "    if len(parts) == 2:\n" +
        "        action = parts[1]  # set, off\n" +
        "        port_id = obj.get('port', '')\n" +
        "        if port_id == 'status':\n" +
        "            if action == 'set':\n" +
        "                color_name = str(obj.get('color', 'off')).lower()\n" +
        "                c = _LED_COLORS.get(color_name, 0)\n" +
        "                try:\n" +
        "                    hub.light.color(getattr(hub.light, 'POWER', 0), c)\n" +
        "                except Exception:\n" +
        "                    pass\n" +
        "            elif action == 'off':\n" +
        "                try:\n" +
        "                    hub.light.color(getattr(hub.light, 'POWER', 0), 0)\n" +
        "                except Exception:\n" +
        "                    pass\n" +
        "    elif len(parts) >= 3 and parts[1] == 'matrix':\n" +
        "        action = parts[2]  # pixel, image, text, clear, brightness, orientation, rotate\n" +
        "        try:\n" +
        "            if action == 'pixel':\n" +
        "                x = int(obj.get('x', 0))\n" +
        "                y = int(obj.get('y', 0))\n" +
        "                brightness = int(obj.get('brightness', 100))\n" +
        "                _matrix_pixels[y][x] = max(0, min(100, brightness))\n" +
        "                scaled = int(_matrix_pixels[y][x] * _matrix_brightness / 100)\n" +
        "                light_matrix.set_pixel(x, y, scaled)\n" +
        "\n" +
        "            elif action == 'image':\n" +
        "                _show_image(str(obj.get('image', 'HAPPY')))\n" +
        "\n" +
        "            elif action == 'text':\n" +
        "                text = str(obj.get('text', ''))\n" +
        "                light_matrix.write(text)\n" +
        "\n" +
        "            elif action == 'clear':\n" +
        "                _matrix_pixels = [[0] * 5 for _ in range(5)]\n" +
        "                _render_matrix()\n" +
        "\n" +
        "            elif action == 'brightness':\n" +
        "                _matrix_brightness = max(0, min(100, int(obj.get('level', 100))))\n" +
        "                _render_matrix()\n" +
        "\n" +
        "            elif action == 'orientation':\n" +
        "                _matrix_orientation = int(obj.get('rotation', 0)) % 360\n" +
        "                _render_matrix()\n" +
        "\n" +
        "            elif action == 'rotate':\n" +
        "                degrees = int(obj.get('degrees', 90))\n" +
        "                _matrix_orientation = (_matrix_orientation + degrees) % 360\n" +
        "                _render_matrix()\n" +
        "\n" +
        "        except Exception as e:\n" +
        "            _send_error(301, 'LED error: ' + str(e), req_id)\n" +
        "\n" +
        "\n" +
        "def _handle_sound(cmd, obj, req_id):\n" +
        "    global _cached_volume\n" +
        "    action = cmd.split('.')[1]  # beep, play, stop, set_volume, read\n" +
        "    try:\n" +
        "        if action == 'beep':\n" +
        "            freq = int(obj.get('freq', 440))\n" +
        "            dur = obj.get('duration')\n" +
        "            if dur is not None:\n" +
        "                hub.sound.beep(int(dur), freq)\n" +
        "            else:\n" +
        "                # Indefinite beep — no native API; just beep for a long time\n" +
        "                hub.sound.beep(30000, freq)\n" +
        "\n" +
        "        elif action == 'stop':\n" +
        "            hub.sound.stop()\n" +
        "\n" +
        "        elif action == 'play':\n" +
        "            wait = obj.get('wait', False)\n" +
        "            sound_name = obj.get('sound')\n" +
        "            notes = obj.get('notes')\n" +
        "            if notes:\n" +
        "                # v0.8 MIDI notes — parse and play via beep sequences (best effort)\n" +
        "                _play_notes_sequence(notes, int(obj.get('tempo', 120)), req_id)\n" +
        "                return\n" +
        "            if sound_name:\n" +
        "                if wait:\n" +
        "                    try:\n" +
        "                        hub.sound.play(str(sound_name), volume=_cached_volume)\n" +
        "                        _send({'event': 'sound_complete', 'request_id': req_id} if req_id else\n" +
        "                              {'event': 'sound_complete'})\n" +
        "                    except TypeError:\n" +
        "                        hub.sound.play(str(sound_name))\n" +
        "                        _send({'event': 'sound_complete'})\n" +
        "                else:\n" +
        "                    hub.sound.play(str(sound_name))\n" +
        "\n" +
        "        elif action == 'set_volume':\n" +
        "            level = int(obj.get('level', 50))\n" +
        "            _cached_volume = max(0, min(100, level))\n" +
        "            try:\n" +
        "                hub.sound.set_volume(_cached_volume)\n" +
        "            except AttributeError:\n" +
        "                pass\n" +
        "\n" +
        "        elif action == 'read':\n" +
        "            metric = obj.get('metric', 'volume')\n" +
        "            if metric == 'volume':\n" +
        "                _send({'event': 'sound', 'metric': 'volume', 'value': _cached_volume})\n" +
        "\n" +
        "    except Exception as e:\n" +
        "        _send_error(301, 'Sound error: ' + str(e), req_id)\n" +
        "\n" +
        "\n" +
        "def _play_notes_sequence(notes_str, tempo, req_id):\n" +
        "    \"\"\"Parse v0.8 §6.3.1 notes string and play as beeps (best-effort MIDI).\"\"\"\n" +
        "    # Note name -> frequency mapping (A4=440 Hz standard)\n" +
        "    NOTE_FREQ = {\n" +
        "        'C': 261, 'C#': 277, 'Db': 277, 'D': 293, 'D#': 311, 'Eb': 311,\n" +
        "        'E': 329, 'F': 349, 'F#': 369, 'Gb': 369, 'G': 392, 'G#': 415,\n" +
        "        'Ab': 415, 'A': 440, 'A#': 466, 'Bb': 466, 'B': 493,\n" +
        "    }\n" +
        "    ms_per_beat = int(60000 / max(1, tempo))\n" +
        "    try:\n" +
        "        tokens = notes_str.strip().split()\n" +
        "        for token in tokens:\n" +
        "            if ':' not in token:\n" +
        "                continue\n" +
        "            note_part, dur_part = token.rsplit(':', 1)\n" +
        "            duration_ms = int(float(dur_part) * ms_per_beat)\n" +
        "            if note_part == 'R':\n" +
        "                time.sleep_ms(duration_ms)\n" +
        "                continue\n" +
        "            # Strip octave digit to get note name, then compute freq\n" +
        "            import re as _re\n" +
        "            m = _re.match(r'([A-G][#b]?)(\\d)', note_part)\n" +
        "            if m:\n" +
        "                name, octave = m.group(1), int(m.group(2))\n" +
        "                base_freq = NOTE_FREQ.get(name, 440)\n" +
        "                freq = int(base_freq * (2 ** (octave - 4)))\n" +
        "                hub.sound.beep(duration_ms, freq)\n" +
        "            else:\n" +
        "                time.sleep_ms(duration_ms)\n" +
        "    except Exception:\n" +
        "        pass  # degrade silently\n" +
        "\n" +
        "\n" +
        "def _handle_sensor(cmd, obj, req_id):\n" +
        "    action = cmd.split('.')[1]  # subscribe, unsubscribe, read\n" +
        "    port_id = obj.get('port', '')\n" +
        "\n" +
        "    if action == 'subscribe':\n" +
        "        mode = obj.get('mode', 'interval')\n" +
        "        interval_ms = int(obj.get('interval', 100))\n" +
        "        min_change = obj.get('min_change', None)\n" +
        "        # Determine sensor type from port capabilities (simplistic)\n" +
        "        if port_id == 'imu':\n" +
        "            sensor_type = obj.get('type', 'pitch')\n" +
        "        else:\n" +
        "            sensor_type = obj.get('type', 'color')\n" +
        "        _subscriptions[port_id] = {\n" +
        "            'type': sensor_type,\n" +
        "            'mode': mode,\n" +
        "            'interval_ms': interval_ms,\n" +
        "            'min_change': float(min_change) if min_change is not None else None,\n" +
        "            'last_ms': 0,\n" +
        "            'last_val': None,\n" +
        "        }\n" +
        "\n" +
        "    elif action == 'unsubscribe':\n" +
        "        _subscriptions.pop(port_id, None)\n" +
        "\n" +
        "    elif action == 'read':\n" +
        "        sensor_type = obj.get('type', 'color')\n" +
        "        val = _read_sensor_value(port_id, sensor_type)\n" +
        "        if val is not None:\n" +
        "            _sensor_event(port_id, sensor_type, val, req_id)\n" +
        "\n" +
        "\n" +
        "def _handle_system(cmd, obj, req_id):\n" +
        "    global _last_ping_ms, _heartbeat_active\n" +
        "    action = cmd.split('.')[1]  # ping, info, subscribe, unsubscribe, read, reset\n" +
        "\n" +
        "    if action == 'ping':\n" +
        "        _last_ping_ms = time.ticks_ms()\n" +
        "        _heartbeat_active = True\n" +
        "        _send({'event': 'pong'})\n" +
        "\n" +
        "    elif action == 'info':\n" +
        "        _send({\n" +
        "            'event': 'system_info',\n" +
        "            'device': 'spike-prime',\n" +
        "            'ssp_version': '0.8',\n" +
        "        })\n" +
        "\n" +
        "    elif action == 'subscribe':\n" +
        "        metric = obj.get('metric', '')\n" +
        "        interval_ms = int(obj.get('interval', 5000))\n" +
        "        _sys_subscriptions[metric] = {\n" +
        "            'interval_ms': interval_ms,\n" +
        "            'last_ms': 0,\n" +
        "            'last_val': None,\n" +
        "        }\n" +
        "\n" +
        "    elif action == 'unsubscribe':\n" +
        "        _sys_subscriptions.pop(obj.get('metric', ''), None)\n" +
        "\n" +
        "    elif action == 'read':\n" +
        "        metric = obj.get('metric', '')\n" +
        "        if metric.startswith('button.'):\n" +
        "            btn_name = metric.split('.')[1]\n" +
        "            try:\n" +
        "                state = _read_button(btn_name)\n" +
        "                _system_event(metric, state)\n" +
        "            except Exception:\n" +
        "                pass\n" +
        "        else:\n" +
        "            val = _read_system_metric(metric)\n" +
        "            if val is not None:\n" +
        "                _system_event(metric, val)\n" +
        "\n" +
        "    elif action == 'reset':\n" +
        "        pass  # no-op on this platform\n" +
        "\n" +
        "\n" +
        "def _handle_timer(cmd, obj, req_id):\n" +
        "    global _timer_start\n" +
        "    action = cmd.split('.')[1]  # get, reset\n" +
        "    if action == 'get':\n" +
        "        elapsed_ms = time.ticks_diff(time.ticks_ms(), _timer_start)\n" +
        "        _sensor_event('timer', 'elapsed', elapsed_ms // 1000, req_id)\n" +
        "    elif action == 'reset':\n" +
        "        _timer_start = time.ticks_ms()\n" +
        "\n" +
        "\n" +
        "def _handle_orientation(cmd, obj, req_id):\n" +
        "    global _orient_u, _orient_f, _orient_l, _yaw_acc, _yaw_zero\n" +
        "    global _cf_pitch, _cf_roll, _cf_last_ms\n" +
        "    action = cmd.split('.')[1]\n" +
        "    try:\n" +
        "        if action == 'reset_yaw':\n" +
        "            _yaw_zero = _yaw_acc\n" +
        "        elif action == 'set_yaw':\n" +
        "            angle = int(obj.get('angle', 0))\n" +
        "            _yaw_zero = _yaw_acc - angle\n" +
        "        elif action == 'set_reference':\n" +
        "            face = obj.get('face', 'Top')\n" +
        "            f = _ORIENT_FRAMES.get(face, _ORIENT_FRAMES['Top'])\n" +
        "            _orient_u = (f[0], f[1], f[2])\n" +
        "            _orient_f = (f[3], f[4], f[5])\n" +
        "            _orient_l = (f[6], f[7], f[8])\n" +
        "            try:\n" +
        "                if abs(f[2]) > 0.5:\n" +
        "                    raw = hub.motion_sensor.tilt_angles()\n" +
        "                    _yaw_acc = -raw[0] / 10.0\n" +
        "                else:\n" +
        "                    _yaw_acc = 0.0\n" +
        "            except Exception:\n" +
        "                _yaw_acc = 0.0\n" +
        "            _yaw_zero  = _yaw_acc\n" +
        "            _cf_last_ms = 0\n" +
        "            try:\n" +
        "                a = hub.motion_sensor.acceleration()\n" +
        "                ax, ay, az = a[0], a[1], a[2]\n" +
        "                au = _dot(_orient_u, (ax, ay, az))\n" +
        "                _cf_pitch = math.degrees(math.atan2(_dot(_orient_f, (ax, ay, az)), au))\n" +
        "                _cf_roll  = math.degrees(math.atan2(_dot(_orient_l, (ax, ay, az)), au))\n" +
        "            except Exception:\n" +
        "                _cf_pitch = 0.0\n" +
        "                _cf_roll  = 0.0\n" +
        "    except Exception as e:\n" +
        "        _send_error(301, 'Orientation error: ' + str(e), req_id)\n" +
        "\n" +
        "\n" +
        "def _read_button(name):\n" +
        "    try:\n" +
        "        c = _BTN_CONST.get(name)\n" +
        "        if c is None:\n" +
        "            return 'released'\n" +
        "        return 'pressed' if hub.button.pressed(c) > 0 else 'released'\n" +
        "    except Exception:\n" +
        "        return 'released'\n" +
        "\n" +
        "\n" +
        "# ---------------------------------------------------------------------------\n" +
        "# Message callback\n" +
        "# ---------------------------------------------------------------------------\n" +
        "\n" +
        "def on_message(data):\n" +
        "    if not _json_ok:\n" +
        "        tunnel.send(b'{\"event\":\"error\",\"code\":400,\"message\":\"json not available\"}\\n')\n" +
        "        return\n" +
        "\n" +
        "    if not isinstance(data, str):\n" +
        "        try:\n" +
        "            data = data.decode('utf-8')\n" +
        "        except Exception:\n" +
        "            data = ''.join(chr(b) for b in data)\n" +
        "\n" +
        "    try:\n" +
        "        obj = json.loads(data.strip())\n" +
        "    except Exception:\n" +
        "        _send_error(400, 'Malformed JSON')\n" +
        "        return\n" +
        "\n" +
        "    cmd = obj.get('cmd', '')\n" +
        "    req_id = obj.get('request_id', None)\n" +
        "\n" +
        "    try:\n" +
        "        if cmd.startswith('motor.'):\n" +
        "            _handle_motor(cmd, obj, req_id)\n" +
        "        elif cmd.startswith('movement.'):\n" +
        "            _handle_movement(cmd, obj, req_id)\n" +
        "        elif cmd.startswith('led.'):\n" +
        "            _handle_led(cmd, obj, req_id)\n" +
        "        elif cmd.startswith('sound.'):\n" +
        "            _handle_sound(cmd, obj, req_id)\n" +
        "        elif cmd.startswith('sensor.'):\n" +
        "            _handle_sensor(cmd, obj, req_id)\n" +
        "        elif cmd.startswith('system.'):\n" +
        "            _handle_system(cmd, obj, req_id)\n" +
        "        elif cmd.startswith('timer.'):\n" +
        "            _handle_timer(cmd, obj, req_id)\n" +
        "        elif cmd.startswith('orientation.'):\n" +
        "            _handle_orientation(cmd, obj, req_id)\n" +
        "        else:\n" +
        "            _send_error(400, 'Unknown command: ' + cmd, req_id)\n" +
        "    except Exception as e:\n" +
        "        _send_error(400, 'Handler error: ' + str(e), req_id)\n" +
        "\n" +
        "\n" +
        "# ---------------------------------------------------------------------------\n" +
        "# Startup\n" +
        "# ---------------------------------------------------------------------------\n" +
        "\n" +
        "def start():\n" +
        "    \"\"\"Entry point — called when running on the SPIKE Prime hub.\"\"\"\n" +
        "    tunnel.callback(on_message)\n" +
        "    _send(_build_capability())\n" +
        "    _run_loop()\n" +
        "\n" +
        "\n" +
        "def _run_loop():\n" +
        "    global _heartbeat_active\n" +
        "    while True:\n" +
        "        now = time.ticks_ms()\n" +
        "\n" +
        "        # Update pitch/roll (complementary filter) and yaw every tick.\n" +
        "        _update_orientation(now)\n" +
        "\n" +
        "        # Heartbeat: stop emitting if client stops pinging for 10 s\n" +
        "        if _heartbeat_active and _last_ping_ms is not None:\n" +
        "            if time.ticks_diff(now, _last_ping_ms) > 10000:\n" +
        "                _heartbeat_active = False\n" +
        "                _subscriptions.clear()\n" +
        "                _sys_subscriptions.clear()\n" +
        "\n" +
        "        # Fast-poll gesture subscriptions: check every 10 ms for _GESTURE_POLL_TICKS ticks.\n" +
        "        # 200 ms window catches shake and double_tap which need sustained/repeated motion.\n" +
        "        for pid, sub in list(_subscriptions.items()):\n" +
        "            if sub['type'] != 'gesture':\n" +
        "                continue\n" +
        "            for _ in range(_GESTURE_POLL_TICKS):\n" +
        "                try:\n" +
        "                    g = _GESTURE_MAP.get(hub.motion_sensor.gesture(), None)\n" +
        "                except Exception:\n" +
        "                    g = None\n" +
        "                if g is not None:\n" +
        "                    _sensor_event(pid, 'gesture', g)\n" +
        "                    sub['last_val'] = None\n" +
        "                    sub['last_ms'] = now\n" +
        "                    break\n" +
        "                time.sleep_ms(10)\n" +
        "\n" +
        "        # Sensor subscriptions\n" +
        "        for pid, sub in list(_subscriptions.items()):\n" +
        "            elapsed = time.ticks_diff(now, sub['last_ms'])\n" +
        "            if elapsed < sub['interval_ms']:\n" +
        "                continue\n" +
        "\n" +
        "            stype = sub['type']\n" +
        "            if stype == 'gesture':\n" +
        "                continue  # handled by fast-poll above\n" +
        "            val = _read_sensor_value(pid, stype)\n" +
        "\n" +
        "            if val is None:\n" +
        "                continue\n" +
        "\n" +
        "            mode = sub['mode']\n" +
        "            last_val = sub['last_val']\n" +
        "            min_change = sub['min_change']\n" +
        "\n" +
        "            should_emit = False\n" +
        "            if mode == 'interval':\n" +
        "                should_emit = True\n" +
        "            elif mode in ('on_change', 'hybrid'):\n" +
        "                if last_val is None:\n" +
        "                    should_emit = True\n" +
        "                elif min_change is not None:\n" +
        "                    try:\n" +
        "                        should_emit = abs(float(val) - float(last_val)) >= min_change\n" +
        "                    except (TypeError, ValueError):\n" +
        "                        should_emit = (val != last_val)\n" +
        "                else:\n" +
        "                    should_emit = (val != last_val)\n" +
        "\n" +
        "            if should_emit:\n" +
        "                _sensor_event(pid, stype, val)\n" +
        "                # Reset gestures to None after emit so the same gesture can fire again next time.\n" +
        "                sub['last_val'] = None if stype == 'gesture' else val\n" +
        "            sub['last_ms'] = now\n" +
        "\n" +
        "        # System metric subscriptions\n" +
        "        for metric, sub in list(_sys_subscriptions.items()):\n" +
        "            elapsed = time.ticks_diff(now, sub['last_ms'])\n" +
        "            if elapsed < sub['interval_ms']:\n" +
        "                continue\n" +
        "\n" +
        "            if metric.startswith('button.'):\n" +
        "                val = _read_button(metric.split('.')[1])\n" +
        "            else:\n" +
        "                val = _read_system_metric(metric)\n" +
        "\n" +
        "            if val is not None:\n" +
        "                if sub['last_val'] is not None and val != sub['last_val']:\n" +
        "                    _system_event(metric, val)\n" +
        "                sub['last_val'] = val\n" +
        "            sub['last_ms'] = now\n" +
        "\n" +
        "        time.sleep_ms(50)\n" +
        "\n" +
        "\n" +
        "# Run when executed on the hub (MicroPython treats this as __main__)\n" +
        "if __name__ == '__main__':\n" +
        "    start()\n" +
        "";


    // Stable hash of HUB_CONTROLLER_PROGRAM — declared after the constant to avoid
    // a Java illegal-forward-reference compile error.
    private static final String PROGRAM_HASH =
        String.valueOf(HUB_CONTROLLER_PROGRAM.hashCode());

    // In-memory cache: hub address → program hash (session-scoped fallback for
    // SharedPreferences failures; also makes the first reconnect within a session fast).
    private static final java.util.concurrent.ConcurrentHashMap<String, String> memCache =
        new java.util.concurrent.ConcurrentHashMap<>();

    // HubDataListener — implemented by sub-components that need hub responses
    // =========================================================================
    interface HubDataListener {
        void onHubData(String data);
    }

    private final List<HubDataListener> dataListeners = new ArrayList<>();

    void addDataListener(HubDataListener l) {
        if (!dataListeners.contains(l)) dataListeners.add(l);
    }

    void removeDataListener(HubDataListener l) {
        dataListeners.remove(l);
    }

    // =========================================================================
    // State
    // =========================================================================
    private Component bluetoothLE;
    private BluetoothInterfaceImpl bluetoothInterface;

    private volatile boolean isConnected   = false;
    private volatile boolean isScanning    = false;
    private volatile boolean wasScanningBeforeConnection = false;

    private String connectedDeviceAddress = "";
    private String connectedDeviceName    = "";

    private int maxPacketSize = DEFAULT_MAX_PACKET_SIZE;
    private int maxChunkSize  = 960;

    // =========================================================================
    // SSP v0.6 infrastructure
    // =========================================================================
    final CapabilityStore capabilityStore = new CapabilityStore();

    private final SSPParser sspParser = new SSPParser(new SSPParser.Listener() {
        @Override public void onCapability(JSONObject cap) {
            capabilityStore.load(cap);
            final String device  = cap.optString("device",      "");
            final String version = cap.optString("ssp_version", "");
            startHeartbeat();
        }
        @Override public void onSensor(String port, String type, Object value) {
            // Sensor events are also dispatched to HubDataListeners below
        }
        @Override public void onSystem(String metric, Object value) {
            // future: surface battery/button events to App Inventor
        }
        @Override public void onError(int code, String message, String requestId) {
            final String rid = requestId != null ? requestId : "";
            mainHandler.post(() -> OnError(code, message, rid));
        }
        @Override public void onPong() {
            lastPongMs = System.currentTimeMillis();
        }
        @Override public void onUnknown(JSONObject raw) {}
    });

    private Timer heartbeatTimer = null;
    private volatile long lastPongMs = 0;
    private volatile long lastTickMs = 0;
    private static final long HEARTBEAT_INTERVAL_MS = 5000;
    private static final long PONG_TIMEOUT_MS       = 10000;

    private void startHeartbeat() {
        stopHeartbeat();
        long now = System.currentTimeMillis();
        lastPongMs = now;
        lastTickMs = now;
        heartbeatTimer = new Timer("SSPHeartbeat", true);
        heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                if (!isConnected) { cancel(); return; }
                long n = System.currentTimeMillis();
                long tickGap = n - lastTickMs;
                lastTickMs = n;

                // Detect timer suspension (Android backgrounding throttles Java Timers).
                // If the previous tick was way later than expected, the app was likely
                // backgrounded — assume hub is still alive, reset state, send fresh ping
                // and re-evaluate next cycle. Avoids false heartbeat_lost on foreground.
                if (tickGap > HEARTBEAT_INTERVAL_MS * 2) {
                    logDebug("Heartbeat: timer gap " + tickGap + "ms — backgrounded?, resetting");
                    lastPongMs = n;
                    sendSSP(new SSPMessage("system.ping"));
                    return;
                }

                long since = n - lastPongMs;
                if (since > PONG_TIMEOUT_MS) {
                    cancel();           // stop this TimerTask from firing again
                    heartbeatTimer = null;
                    mainHandler.post(() -> HubDisconnected("heartbeat_lost"));
                    return;
                }
                sendSSP(new SSPMessage("system.ping"));
            }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS);
    }

    private void stopHeartbeat() {
        if (heartbeatTimer != null) {
            heartbeatTimer.cancel();
            heartbeatTimer = null;
        }
    }

    private volatile boolean uploadInProgress = false;
    private final java.util.concurrent.LinkedBlockingQueue<byte[]> uploadResponseQueue =
        new java.util.concurrent.LinkedBlockingQueue<>(8);

    private Object connectionListenerProxy = null;
    private volatile String pendingConnectAddress = "";

    private Timer connectionPollTimer                         = null;
    private static final int CONNECTION_POLL_INTERVAL_MS     = 500;
    private static final int CONNECTION_POLL_TIMEOUT_MS      = 10000;

    private Timer disconnectWatchdog                          = null;
    private static final int DISCONNECT_WATCHDOG_INTERVAL_MS = 2000;

    private final List<Byte> receiveBuffer = new ArrayList<>();

    private boolean debugMode    = true;
    private int     scanInterval = 1000;
    private Timer   scanTimer;
    private Handler mainHandler;

    private String customDeviceName = "LEGO Hub";
    private static final Set<String> LEGO_HUB_NAMES = new HashSet<>(Arrays.asList(
        "MITNodeHub", "LEGO Technic Hub", "LEGO Hub", "SPIKE Prime Hub", "SPIKE Hub"
    ));

    private final List<LegoHub> legoHubs = new ArrayList<>();

    // =========================================================================
    // LegoHub inner class — RSSI staleness detection (CLAUDE.md Rule 2)
    // =========================================================================
    private class LegoHub {
        private final String name;
        private final String address;
        private int    bleIndex;
        private long   lastSeenTimestamp;
        private int    lastRssi       = Integer.MIN_VALUE;
        private int    rssiStaleCount = 0;
        private Boolean frozenVisibility = null;

        LegoHub(String name, String address, int bleIndex) {
            this.name    = name;
            this.address = address;
            this.bleIndex = bleIndex;
            this.lastSeenTimestamp = System.currentTimeMillis();
        }

        LegoHub(LegoHub src, boolean frozen) {
            this.name              = src.name;
            this.address           = src.address;
            this.bleIndex          = src.bleIndex;
            this.lastSeenTimestamp = src.lastSeenTimestamp;
            this.lastRssi          = src.lastRssi;
            this.rssiStaleCount    = src.rssiStaleCount;
            this.frozenVisibility  = frozen;
        }

        String getName()     { return name; }
        String getAddress()  { return address; }
        int    getBleIndex() { return bleIndex; }

        void updateLastSeen() { lastSeenTimestamp = System.currentTimeMillis(); }

        boolean updateRssi(int newRssi) {
            if (lastRssi == newRssi) { rssiStaleCount++; return false; }
            lastRssi = newRssi; rssiStaleCount = 0; return true;
        }

        boolean isVisible() {
            if (frozenVisibility != null) return frozenVisibility;
            boolean rssiStale = rssiStaleCount >= 3;
            boolean timeStale = (System.currentTimeMillis() - lastSeenTimestamp)
                                 > (2L * LegoSpikeConnectivity.this.scanInterval);
            return !(rssiStale && timeStale);
        }

        @Override public boolean equals(Object o) {
            return o instanceof LegoHub && address.equals(((LegoHub)o).address);
        }
        @Override public int hashCode() { return address.hashCode(); }
        @Override public String toString() { return name + " (" + address + ")"; }
    }

    // =========================================================================
    // Constructor
    // =========================================================================
    public LegoSpikeConnectivity(ComponentContainer container) {
        super(container.$form());
        mainHandler        = new Handler(Looper.getMainLooper());
        bluetoothInterface = new BluetoothInterfaceImpl();
        bluetoothInterface.setExtension(this);
        logDebug("LegoSpikeConnectivity initialised");
    }

    // =========================================================================
    // BluetoothDevice property
    // =========================================================================
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "The BluetoothLE component used for BLE communication")
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COMPONENT
        + ":edu.mit.appinventor.ble.BluetoothLE")
    public void BluetoothDevice(Component ble) {
        if (this.bluetoothLE != null && connectionListenerProxy != null) {
            try {
                Class<?> iface = this.bluetoothLE.getClass().getClassLoader()
                    .loadClass("edu.mit.appinventor.ble.BluetoothLE$BluetoothConnectionListener");
                this.bluetoothLE.getClass()
                    .getMethod("removeConnectionListener", iface)
                    .invoke(this.bluetoothLE, connectionListenerProxy);
            } catch (Exception e) {
                logDebug("removeConnectionListener: " + e.getMessage());
            }
            connectionListenerProxy = null;
        }

        this.bluetoothLE = ble;
        bluetoothInterface.setBluetoothLE(ble);
        logDebug("BluetoothLE component set");

        if (ble == null) return;

        try {
            ClassLoader cl = ble.getClass().getClassLoader();
            if (cl == null) cl = Thread.currentThread().getContextClassLoader();
            Class<?> iface = cl
                .loadClass("edu.mit.appinventor.ble.BluetoothLE$BluetoothConnectionListener");
            connectionListenerProxy = Proxy.newProxyInstance(
                ble.getClass().getClassLoader(),
                new Class<?>[]{ iface },
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy,
                                         java.lang.reflect.Method method,
                                         Object[] args) {
                        String mn = method.getName();
                        if ("onConnected".equals(mn)) {
                            handleBleConnected(args != null && args.length > 0 ? args[0] : null);
                        } else if ("onDisconnected".equals(mn)) {
                            handleBleDisconnected();
                        }
                        return null;
                    }
                });
            ble.getClass()
                .getMethod("addConnectionListener", iface)
                .invoke(ble, connectionListenerProxy);
            logDebug("BluetoothConnectionListener proxy registered");
        } catch (Exception e) {
            logDebug("Could not register connection listener: "
                + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "The BluetoothLE component used for BLE communication")
    public Component BluetoothDevice() { return bluetoothLE; }

    // =========================================================================
    // Simple properties
    // =========================================================================
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Enable verbose logcat output for debugging")
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
        defaultValue = "True")
    public void DebugMode(boolean v) { debugMode = v; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Enable verbose logcat output for debugging")
    public boolean DebugMode() { return debugMode; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Custom BLE device name to match during scanning")
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING,
        defaultValue = "LEGO Hub")
    public void CustomDeviceName(String v) { customDeviceName = v; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Custom BLE device name to match during scanning")
    public String CustomDeviceName() { return customDeviceName; }

    public void ScanInterval(int ms) { scanInterval = Math.max(100, ms); }
    public int  ScanInterval()       { return scanInterval; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "True while a BLE scan is running")
    public boolean IsScanning() { return isScanning; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "True when connected to a hub")
    public boolean IsConnected() { return isConnected; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Name of the currently connected hub, or empty string")
    public String ConnectedDeviceName() { return connectedDeviceName; }

    public String ConnectedDeviceAddress() { return connectedDeviceAddress; }

    // =========================================================================
    private String[] buildCapabilityArgs() {
        String deviceType = capabilityStore.getDeviceType();
        String sspVersion = capabilityStore.getSspVersion();
        java.util.List<String> portIds = capabilityStore.getPortIds();
        java.util.List<String> encodings = capabilityStore.getEncodings();
        StringBuilder ports = new StringBuilder();
        for (int i = 0; i < portIds.size(); i++) { if (i > 0) ports.append(','); ports.append(portIds.get(i)); }
        StringBuilder encs = new StringBuilder();
        for (int i = 0; i < encodings.size(); i++) { if (i > 0) encs.append(','); encs.append(encodings.get(i)); }
        return new String[]{
            deviceType != null ? deviceType : "",
            sspVersion != null ? sspVersion : "",
            ports.toString(),
            encs.toString()
        };
    }

    // =========================================================================
    // Scanning
    // =========================================================================
    @SimpleFunction(description = "Start scanning for LEGO SPIKE Prime hubs")
    public void StartScanning() {
        if (bluetoothLE == null) { ErrorOccurred("BluetoothLE component not set"); return; }
        if (isScanning) StopScanning();

        legoHubs.clear();
        logDebug("StartScanning");

        boolean started = false;
        for (String method : new String[]{"StartScanningWithUUIDs", "ScanForService",
                                          "StartScanningFiltered"}) {
            try {
                bluetoothLE.getClass()
                    .getMethod(method, String.class)
                    .invoke(bluetoothLE, SPIKE_SERVICE_UUID);
                started = true;
                break;
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                logDebug(method + " failed: " + e.getMessage());
            }
        }
        if (!started) {
            try {
                bluetoothLE.getClass().getMethod("StartScanning").invoke(bluetoothLE);
                started = true;
            } catch (Exception e) {
                ErrorOccurred("Cannot start scan: " + e.getMessage()); return;
            }
        }

        isScanning = true;
        startScanTimer();
        ScanningStarted();
    }

    @SimpleFunction(description = "Stop scanning for LEGO SPIKE Prime hubs")
    public void StopScanning() {
        if (bluetoothLE == null || !isScanning) return;
        try {
            bluetoothLE.getClass().getMethod("StopScanning").invoke(bluetoothLE);
        } catch (Exception e) {
            logDebug("StopScanning error: " + e.getMessage());
        }
        stopScanTimer();
        isScanning = false;
        ScanningStopped();
    }

    private void startScanTimer() {
        stopScanTimer();
        scanTimer = new Timer("LegoScanTimer", true);
        scanTimer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() { if (isScanning) CheckAllDevices(); }
        }, scanInterval, scanInterval);
    }

    private void stopScanTimer() {
        if (scanTimer != null) { scanTimer.cancel(); scanTimer = null; }
    }

    public void CheckAllDevices() {
        if (bluetoothLE == null) return;
        try {
            List<LegoHub> oldSnap = new ArrayList<>();
            for (LegoHub h : legoHubs) oldSnap.add(new LegoHub(h, h.isVisible()));

            String deviceListStr;
            try {
                deviceListStr = (String) bluetoothLE.getClass()
                    .getMethod("DeviceList").invoke(bluetoothLE);
            } catch (Exception e) { return; }

            if (deviceListStr == null || deviceListStr.isEmpty()) {
                dispatchHubListChangedIfNeeded(oldSnap); return;
            }

            int count = deviceListStr.split(",").length;
            Set<String> seen = new HashSet<>();
            for (int i = 1; i <= count; i++) checkBLEDeviceAtIndex(i, seen);

            dispatchHubListChangedIfNeeded(oldSnap);
        } catch (Exception e) {
            logDebug("CheckAllDevices error: " + e);
        }
    }

    private String checkBLEDeviceAtIndex(int bleIndex, Set<String> seen) {
        try {
            String name = (String) bluetoothLE.getClass()
                .getMethod("FoundDeviceName", int.class).invoke(bluetoothLE, bleIndex);
            String addr = (String) bluetoothLE.getClass()
                .getMethod("FoundDeviceAddress", int.class).invoke(bluetoothLE, bleIndex);
            Integer rssi = (Integer) bluetoothLE.getClass()
                .getMethod("FoundDeviceRssi", int.class).invoke(bluetoothLE, bleIndex);

            if (addr == null) return null;
            if (!isLegoSpikeHub(name)) return null;

            seen.add(addr);

            LegoHub existing = null;
            for (LegoHub h : legoHubs) {
                if (addr.equals(h.getAddress())) { existing = h; break; }
            }
            if (existing == null) {
                LegoHub hub = new LegoHub(name != null ? name : "SPIKE Hub", addr, bleIndex);
                if (rssi != null) hub.updateRssi(rssi);
                legoHubs.add(hub);
                final String finalName = hub.getName();
                mainHandler.post(() -> HubFound(finalName));
            } else {
                existing.bleIndex = bleIndex;
                if (rssi != null && existing.updateRssi(rssi)) existing.updateLastSeen();
            }
            return addr;
        } catch (Exception e) {
            logDebug("checkBLEDeviceAtIndex(" + bleIndex + ") error: " + e);
            return null;
        }
    }

    private void dispatchHubListChangedIfNeeded(List<LegoHub> oldSnap) {
        List<LegoHub> oldVisible = visibleFrom(oldSnap);
        List<LegoHub> nowVisible = getVisibleHubs();

        List<LegoHub> gained = new ArrayList<>(), lost = new ArrayList<>(),
                      kept   = new ArrayList<>();
        for (LegoHub h : nowVisible) {
            if (containsAddress(oldVisible, h.getAddress())) kept.add(h);
            else gained.add(h);
        }
        for (LegoHub h : oldVisible) {
            if (!containsAddress(nowVisible, h.getAddress())) lost.add(h);
        }
        if (!gained.isEmpty() || !lost.isEmpty()) {
            HubListChanged(hubNames(gained), hubNames(kept), hubNames(lost), HubList());
        }
    }

    private static boolean containsAddress(List<LegoHub> list, String addr) {
        if (addr == null) return false;
        for (LegoHub h : list) { if (addr.equals(h.getAddress())) return true; }
        return false;
    }

    // =========================================================================
    // Hub list accessors
    // =========================================================================
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Comma-separated names of currently visible hubs")
    public String HubList() { return hubNames(getVisibleHubs()); }

    @SimpleFunction(description = "Number of visible LEGO SPIKE Prime hubs")
    public int HubCount() { return getVisibleHubs().size(); }

    @SimpleFunction(description = "Name of hub at 1-based index in visible hub list")
    public String HubName(int index) {
        List<LegoHub> v = getVisibleHubs();
        return (index >= 1 && index <= v.size()) ? v.get(index - 1).getName() : "";
    }

    public String GetLegoHubAddress(int index) {
        List<LegoHub> v = getVisibleHubs();
        return (index >= 1 && index <= v.size()) ? v.get(index - 1).getAddress() : "";
    }

    // =========================================================================
    // Connection
    // =========================================================================
    @SimpleFunction(description =
        "Connect to the LEGO SPIKE Prime hub at the given 1-based index")
    public void ConnectToHub(int index) {
        if (bluetoothLE == null) { ErrorOccurred("BluetoothLE component not set"); return; }

        List<LegoHub> visible = getVisibleHubs();
        if (index < 1 || index > visible.size()) {
            ErrorOccurred("Invalid hub index: " + index); return;
        }
        stopConnectionPolling();
        pendingConnectAddress = "";
        if (isConnected) Disconnect();

        if (isScanning) {
            wasScanningBeforeConnection = true;
            try {
                bluetoothLE.getClass().getMethod("StopScanning").invoke(bluetoothLE);
            } catch (Exception e) {
                logDebug("BLE StopScanning: " + e.getMessage());
            }
            stopScanTimer();
            isScanning = false;
            ScanningStopped();
        } else {
            wasScanningBeforeConnection = false;
        }

        LegoHub hub = visible.get(index - 1);
        pendingConnectAddress = hub.getAddress();
        logDebug("ConnectToHub → " + hub);
        try {
            bluetoothLE.getClass()
                .getMethod("ConnectWithAddress", String.class)
                .invoke(bluetoothLE, hub.getAddress());
            startConnectionPolling(hub.getName(), hub.getAddress());
        } catch (Exception e) {
            ErrorOccurred("Connect failed: " + e.getMessage());
            pendingConnectAddress = "";
            if (wasScanningBeforeConnection) {
                isScanning = true; startScanTimer(); ScanningStarted();
            }
        }
    }

    @SimpleFunction(description = "Disconnect from the currently connected hub")
    public void Disconnect() {
        stopConnectionPolling();
        pendingConnectAddress = "";
        if (bluetoothLE == null) return;
        if (!isConnected) {
            logDebug("Disconnect called but not connected — ignored");
            return;
        }
        isConnected = false;
        logDebug("Disconnect → " + connectedDeviceName);
        try {
            bluetoothLE.getClass().getMethod("Disconnect").invoke(bluetoothLE);
        } catch (Exception e) {
            logDebug("BLE Disconnect: " + e.getMessage());
        }
        onDisconnected();
    }

    private void OnConnectionFailed(String reason) {
        logDebug("OnConnectionFailed: " + reason);
        stopConnectionPolling();
        pendingConnectAddress = "";
        if (wasScanningBeforeConnection) {
            wasScanningBeforeConnection = false;
            isScanning = true;
            startScanTimer();
            ScanningStarted();
        }
        ErrorOccurred("Connection failed: " + (reason != null ? reason : "unknown"));
    }

    // =========================================================================
    // BLE notification subscription
    // =========================================================================
    private void registerForTXNotifications() {
        if (bluetoothLE == null) return;
        logDebug("Subscribing to TX notifications: " + TX_CHAR_UUID);
        if (registerForBytesViaInner()) return;
        try {
            bluetoothLE.getClass()
                .getMethod("RegisterForBytes", String.class, String.class, boolean.class)
                .invoke(bluetoothLE, SPIKE_SERVICE_UUID, TX_CHAR_UUID, false);
            logDebug("TX notification subscription OK (fallback)");
        } catch (Exception e) {
            logDebug("RegisterForBytes failed: " + e.getMessage());
            ErrorOccurred("TX subscription failed: " + e.getMessage());
        }
    }

    private boolean registerForBytesViaInner() {
        try {
            java.lang.reflect.Field f = bluetoothLE.getClass().getDeclaredField("inner");
            f.setAccessible(true);
            final Object inner = f.get(bluetoothLE);
            if (inner == null) return false;

            ClassLoader cl = bluetoothLE.getClass().getClassLoader();
            if (cl == null) cl = Thread.currentThread().getContextClassLoader();
            Class<?> handlerClass = cl.loadClass(
                "edu.mit.appinventor.ble.BluetoothLE$BLEResponseHandler");

            Object proxy = Proxy.newProxyInstance(cl, new Class<?>[]{ handlerClass },
                new InvocationHandler() {
                    @Override public Object invoke(Object p,
                            java.lang.reflect.Method m, Object[] args) {
                        if ("onReceive".equals(m.getName())
                                && args != null && args.length >= 3) {
                            String charUuid = (String) args[1];
                            if (TX_CHAR_UUID.equalsIgnoreCase(charUuid)) {
                                java.util.List<?> vals = (java.util.List<?>) args[2];
                                for (Object v : vals) {
                                    try {
                                        receiveBuffer.add(
                                            (byte)(((Number) v).intValue() & 0xFF));
                                    } catch (Exception ignored) {}
                                }
                                processReceiveBuffer();
                            }
                        }
                        return null;
                    }
                });

            inner.getClass()
                .getMethod("RegisterForByteValues",
                    String.class, String.class, boolean.class, handlerClass)
                .invoke(inner, SPIKE_SERVICE_UUID, TX_CHAR_UUID, false, proxy);
            return true;
        } catch (Exception e) {
            logDebug("Inner BLEResponseHandler unavailable (" + e.getClass().getSimpleName()
                + ") — wire BluetoothLE.BytesReceived → OnBytesReceived in your blocks");
            return false;
        }
    }

    // =========================================================================
    // Outbound — sendFramedMessage / sendCommand
    // =========================================================================
    private void sendFramedMessage(byte[] framedMessage) {
        if (bluetoothLE == null || !isConnected) {
            logDebug("sendFramedMessage: not connected");
            return;
        }
        try {
            java.lang.reflect.Method writeBytes =
                bluetoothLE.getClass().getMethod(
                    "WriteBytes", String.class, String.class, boolean.class, Object.class);

            for (int offset = 0; offset < framedMessage.length; offset += maxPacketSize) {
                int end = Math.min(offset + maxPacketSize, framedMessage.length);
                Object[] values = new Object[end - offset];
                for (int i = offset; i < end; i++) {
                    values[i - offset] = framedMessage[i] & 0xFF;
                }
                YailList packet = YailList.makeList(values);
                writeBytes.invoke(bluetoothLE, SPIKE_SERVICE_UUID, RX_CHAR_UUID,
                    false, packet);
            }
        } catch (Exception e) {
            logDebug("sendFramedMessage error: " + e);
            ErrorOccurred("Send error: " + e.getMessage());
        }
    }

    /** Send a command string to the running hub controller via TunnelMessage. */
    void sendCommand(String command) {
        if (!isConnected) { ErrorOccurred("Not connected"); return; }
        if (command == null || command.isEmpty()) return;
        logDebug("sendCommand: " + command);
        sendFramedMessage(MessageFramer.pack(MessageBuilder.buildTunnelMessage(command)));
    }

    /** Send an SSP v0.6 JSON command via TunnelMessage. */
    void sendSSP(SSPMessage msg) {
        if (!isConnected) { ErrorOccurred("Not connected"); return; }
        String json = new String(msg.serialise(), StandardCharsets.UTF_8).trim();
        logDebug("sendSSP: " + json);
        sendFramedMessage(MessageFramer.pack(MessageBuilder.buildTunnelMessage(json)));
    }

    // =========================================================================
    // Inbound — frame buffer and detection
    // =========================================================================
    @SimpleFunction(description =
        "Wire BluetoothLE.BytesReceived to this as a fallback if auto-wiring is unavailable. "
        + "Wiring: BluetoothLE1.BytesReceived → LegoSpikeConnectivity1.OnBytesReceived")
    public void OnBytesReceived(String serviceUuid,
                                String characteristicUuid,
                                YailList byteValues) {
        if (!TX_CHAR_UUID.equalsIgnoreCase(characteristicUuid)) return;
        for (Object item : byteValues.toArray()) {
            try {
                receiveBuffer.add((byte)(Integer.parseInt(item.toString()) & 0xFF));
            } catch (NumberFormatException ignored) {}
        }
        processReceiveBuffer();
    }

    private void processReceiveBuffer() {
        while (!receiveBuffer.isEmpty()) {
            int endIdx = -1;
            for (int i = 0; i < receiveBuffer.size(); i++) {
                if ((receiveBuffer.get(i) & 0xFF) == 0x02) { endIdx = i; break; }
            }
            if (endIdx == -1) break;

            byte[] frame = new byte[endIdx + 1];
            for (int i = 0; i <= endIdx; i++) frame[i] = receiveBuffer.get(i);
            for (int i = 0; i <= endIdx; i++) receiveBuffer.remove(0);

            handleCompleteFrame(frame);
        }
    }

    private void handleCompleteFrame(byte[] frame) {
        try {
            byte[] raw = MessageFramer.unpack(frame);
            if (raw == null || raw.length == 0) return;
            int msgId = raw[0] & 0xFF;
            logDebug("Frame msgId=0x" + String.format("%02X", msgId));

            if (msgId == ResponseParser.MSG_INFO_RESPONSE) {
                handleInfoResponse(raw);
            } else if (msgId == 0x0D || msgId == 0x11 || msgId == 0x1F || msgId == 0x47) {
                handleStatusResponse(raw);
            } else if (msgId == 0x20) {
                if (raw.length >= 2)
                    logDebug("ProgramFlow: " + (raw[1] == 0 ? "started" : "stopped"));
            } else if (msgId == 0x21) {
                if (raw.length > 1) {
                    int len = raw.length - 1;
                    while (len > 0 && raw[len] == 0) len--;
                    String console = new String(raw, 1, len,
                        java.nio.charset.StandardCharsets.UTF_8).trim();
                    logDebug("HUB PRINT: " + console);
                }
            } else if (msgId == 0x32) {
                if (raw.length >= 3) {
                    int payloadSize = (raw[1] & 0xFF) | ((raw[2] & 0xFF) << 8);
                    if (raw.length >= 3 + payloadSize) {
                        String text = new String(raw, 3, payloadSize,
                            StandardCharsets.UTF_8).trim();
                        logDebug("TunnelMessage: " + text);
                        // Route SSP JSON events through SSPParser (capability, pong, error)
                        if (text.startsWith("{")) {
                            sspParser.parse((text + "\n").getBytes(StandardCharsets.UTF_8));
                        }
                        // Dispatch to component listeners (sensors parse SSP JSON in onHubData)
                        if (!text.isEmpty() && !"rdy".equals(text) && !"err".equals(text)) {
                            for (HubDataListener l : new ArrayList<>(dataListeners)) {
                                l.onHubData(text);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logDebug("handleCompleteFrame error: " + e);
        }
    }

    private void handleInfoResponse(byte[] raw) {
        ResponseParser.InfoResponse info = ResponseParser.parseInfoResponse(raw);
        if (info == null) return;
        maxPacketSize = info.maxPacketSize;
        maxChunkSize  = info.maxChunkSize;
        logDebug("SPIKE FW: " + info.fwMajor + "." + info.fwMinor + "." + info.fwBuild
            + "  maxPacket=" + maxPacketSize + "  maxChunk=" + maxChunkSize);
    }

    private void handleStatusResponse(byte[] raw) {
        int     msgId   = raw[0] & 0xFF;
        boolean success = ResponseParser.parseStatusResponse(raw);
        String  name    = statusResponseName(msgId);
        logDebug("Status " + (success ? "OK" : "NACK") + ": " + name);
        if (!success && msgId != 0x47) {
            ErrorOccurred(name + " not acknowledged by hub");
        }
        if (uploadInProgress) uploadResponseQueue.offer(raw);
    }

    private static String statusResponseName(int msgId) {
        switch (msgId) {
            case 0x0D: return "StartFileUploadResponse";
            case 0x11: return "TransferChunkResponse";
            case 0x1F: return "ProgramFlowResponse";
            case 0x47: return "ClearSlotResponse";
            default:   return "StatusResponse(0x" + String.format("%02X", msgId) + ")";
        }
    }

    // =========================================================================
    // Connection callbacks
    // =========================================================================
    private void handleBleConnected(Object bleInstance) {
        stopConnectionPolling();
        if (isConnected) return;

        String name = "SPIKE Hub";
        if (bleInstance != null) {
            try {
                String n = (String) bleInstance.getClass()
                    .getMethod("ConnectedDeviceName").invoke(bleInstance);
                if (n != null && !n.isEmpty() && !"NEEDS_PERMISSION".equals(n)) name = n;
            } catch (Exception e) {
                logDebug("ConnectedDeviceName: " + e.getMessage());
            }
        }
        if ("SPIKE Hub".equals(name) && !pendingConnectAddress.isEmpty()) {
            for (LegoHub h : legoHubs) {
                if (pendingConnectAddress.equals(h.getAddress())) { name = h.getName(); break; }
            }
        }

        final String finalName = name;
        final String finalAddr = pendingConnectAddress;
        mainHandler.post(() -> onConnected(finalName, finalAddr));
    }

    private void handleBleDisconnected() {
        stopConnectionPolling();
        mainHandler.post(() -> onDisconnected());
    }

    private void startConnectionPolling(final String targetName, final String targetAddress) {
        stopConnectionPolling();
        final long startTime = System.currentTimeMillis();
        connectionPollTimer = new Timer("LegoConnPoll", true);
        connectionPollTimer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                if (isConnected) { stopConnectionPolling(); return; }
                if (System.currentTimeMillis() - startTime > CONNECTION_POLL_TIMEOUT_MS) {
                    stopConnectionPolling();
                    mainHandler.post(() -> ErrorOccurred("Connection timeout"));
                    if (wasScanningBeforeConnection) {
                        wasScanningBeforeConnection = false;
                        mainHandler.post(() -> { isScanning = true; startScanTimer(); ScanningStarted(); });
                    }
                    return;
                }
                try {
                    boolean bleConn = (Boolean) bluetoothLE.getClass()
                        .getMethod("IsDeviceConnected").invoke(bluetoothLE);
                    if (bleConn && !isConnected) {
                        stopConnectionPolling();
                        mainHandler.post(() -> onConnected(targetName, targetAddress));
                    }
                } catch (Exception e) {
                    logDebug("Connection poll: " + e.getMessage());
                }
            }
        }, CONNECTION_POLL_INTERVAL_MS, CONNECTION_POLL_INTERVAL_MS);
    }

    private void stopConnectionPolling() {
        if (connectionPollTimer != null) { connectionPollTimer.cancel(); connectionPollTimer = null; }
    }

    private void startDisconnectWatchdog() {
        stopDisconnectWatchdog();
        if (bluetoothLE == null) return;
        disconnectWatchdog = new Timer("LegoDisconnectWatch", true);
        disconnectWatchdog.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                if (!isConnected) { cancel(); return; }
                try {
                    boolean bleConn = (Boolean) bluetoothLE.getClass()
                        .getMethod("IsDeviceConnected").invoke(bluetoothLE);
                    if (!bleConn) {
                        disconnectWatchdog = null;
                        mainHandler.post(() -> onDisconnected());
                    }
                } catch (Exception e) {
                    logDebug("Watchdog poll: " + e.getMessage());
                }
            }
        }, DISCONNECT_WATCHDOG_INTERVAL_MS, DISCONNECT_WATCHDOG_INTERVAL_MS);
    }

    private void stopDisconnectWatchdog() {
        if (disconnectWatchdog != null) { disconnectWatchdog.cancel(); disconnectWatchdog = null; }
    }

    // =========================================================================
    // Connection state transitions
    // =========================================================================
    public void onConnected(String deviceName, String deviceAddress) {
        if (isConnected) return;
        stopConnectionPolling();

        isConnected            = true;
        connectedDeviceName    = deviceName;
        connectedDeviceAddress = deviceAddress;
        pendingConnectAddress  = "";
        receiveBuffer.clear();

        logDebug("onConnected: " + deviceName);

        capabilityStore.clear();   // reset so waitForCapability works correctly
        registerForTXNotifications();
        logDebug("Sending InfoRequest");
        sendFramedMessage(MessageFramer.pack(MessageBuilder.buildInfoRequest()));
        startDisconnectWatchdog();
        UploadController();
    }

    public void onDisconnected() {
        stopConnectionPolling();
        stopDisconnectWatchdog();
        if (!isConnected && connectedDeviceName.isEmpty()) return;
        isConnected = false;
        final String n = connectedDeviceName;
        connectedDeviceName    = "";
        connectedDeviceAddress = "";
        pendingConnectAddress  = "";
        receiveBuffer.clear();
        logDebug("onDisconnected: " + n);

        if (wasScanningBeforeConnection) {
            wasScanningBeforeConnection = false;
            isScanning = true;
            startScanTimer();
            ScanningStarted();
        }

        mainHandler.post(() -> HubDisconnected("disconnected"));
    }

    // =========================================================================
    // Events
    // =========================================================================
    @SimpleEvent(description = "Fired when BLE scanning starts")
    public void ScanningStarted() {
        mainHandler.post(() ->
            EventDispatcher.dispatchEvent(LegoSpikeConnectivity.this, "ScanningStarted"));
    }

    @SimpleEvent(description = "Fired when BLE scanning stops")
    public void ScanningStopped() {
        mainHandler.post(() ->
            EventDispatcher.dispatchEvent(LegoSpikeConnectivity.this, "ScanningStopped"));
    }

    @SimpleEvent(description = "Fired when a LEGO SPIKE Prime hub is discovered during scanning")
    public void HubFound(String deviceName) {
        logDebug("HubFound: " + deviceName);
        EventDispatcher.dispatchEvent(this, "HubFound", deviceName);
    }

    @SimpleEvent(description = "Fired when the visible hub list changes")
    public void HubListChanged(String newHubs, String retainedHubs,
                               String lostHubs, String allCurrentHubs) {
        mainHandler.post(() ->
            EventDispatcher.dispatchEvent(LegoSpikeConnectivity.this, "HubListChanged",
                newHubs, retainedHubs, lostHubs, allCurrentHubs));
    }

    @SimpleEvent(description =
        "Fired when the hub is connected and ready to use. "
        + "deviceName: hub BLE name. deviceType: hardware type (e.g. 'spike-prime'). "
        + "sspVersion: protocol version (e.g. '0.8'). "
        + "availablePorts: comma-separated port IDs (e.g. 'A,B,C,D,E,F,display,status,imu'). "
        + "supportedEncodings: comma-separated encodings (e.g. 'json-utf8-newline').")
    public void HubConnected(String deviceName, String deviceType, String sspVersion,
                             String availablePorts, String supportedEncodings) {
        logDebug("HubConnected: " + deviceName + " (" + deviceType + " SSP " + sspVersion + ")");
        EventDispatcher.dispatchEvent(this, "HubConnected",
            deviceName, deviceType, sspVersion, availablePorts, supportedEncodings);
    }

    @SimpleEvent(description =
        "Fired when the connection to the hub is lost. "
        + "reason: 'disconnected' (clean) or 'heartbeat_lost' (silent drop — hub program crashed or BLE dropped).")
    public void HubDisconnected(String reason) {
        logDebug("HubDisconnected: " + reason);
        stopHeartbeat();
        EventDispatcher.dispatchEvent(this, "HubDisconnected", reason);
    }

    @SimpleEvent(description = "Fired when an error occurs")
    public void ErrorOccurred(String errorMessage) {
        logDebug("ErrorOccurred: " + errorMessage);
        EventDispatcher.dispatchEvent(this, "ErrorOccurred", errorMessage);
    }

    @SimpleEvent(description =
        "Fired when the hub bridge reports an error. "
        + "code: SSP error code (200-499). message: description. requestId: echoed request id if set.")
    public void OnError(int code, String message, String requestId) {
        logDebug("OnError " + code + ": " + message);
        EventDispatcher.dispatchEvent(this, "OnError", code, message, requestId);
    }

    // =========================================================================
    // Program upload
    // =========================================================================
    private void UploadController() {
        if (!isConnected) { ErrorOccurred("Not connected"); return; }
        new Thread(() -> {
            try {
                // ---------------------------------------------------------------
                // Fast path: if this hub already has the current program, skip
                // the upload and just start the program via ProgramFlow.
                // ---------------------------------------------------------------
                String cachedHash = getCachedProgramHash(connectedDeviceAddress);
                if (PROGRAM_HASH.equals(cachedHash)) {
                    logDebug("UploadController: hash match — probing hub (skip upload)");
                    sendFramedMessage(MessageFramer.pack(
                        MessageBuilder.buildProgramFlowRequest(false, CONTROLLER_SLOT)));
                    if (waitForCapability(4000)) {
                        logDebug("UploadController: fast path OK");
                        final String dn = connectedDeviceName;
                        final String[] cap = buildCapabilityArgs();
                        mainHandler.post(() -> HubConnected(dn, cap[0], cap[1], cap[2], cap[3]));
                        return;
                    }
                    logDebug("UploadController: fast path failed — doing full upload");
                }

                // ---------------------------------------------------------------
                // Full upload path (first connection or stale program).
                // ---------------------------------------------------------------
                ProgramUploader up = new ProgramUploader(
                    "program.py", CONTROLLER_SLOT,
                    HUB_CONTROLLER_PROGRAM, maxChunkSize);

                uploadInProgress = true;
                uploadResponseQueue.clear();

                logDebug("UploadController: ClearSlot");
                sendFramedMessage(MessageFramer.pack(
                    new byte[]{0x46, (byte)(CONTROLLER_SLOT & 0xFF)}));
                awaitUploadResponse(3000);

                logDebug("UploadController: StartFileUpload");
                sendFramedMessage(up.getStartUploadMessage());
                byte[] startResp = awaitUploadResponse(5000);
                if (startResp == null) {
                    mainHandler.post(() -> ErrorOccurred("Upload timeout: StartFileUpload"));
                    return;
                }
                if (!ResponseParser.parseStatusResponse(startResp)) {
                    mainHandler.post(() -> ErrorOccurred("StartFileUpload rejected"));
                    return;
                }

                List<byte[]> chunks = up.getChunkMessages();
                logDebug("UploadController: " + chunks.size() + " chunk(s)");
                for (int i = 0; i < chunks.size(); i++) {
                    sendFramedMessage(chunks.get(i));
                    byte[] chunkResp = awaitUploadResponse(5000);
                    if (chunkResp == null) {
                        final int ci = i + 1;
                        mainHandler.post(() -> ErrorOccurred("Upload timeout: chunk " + ci));
                        return;
                    }
                    logDebug("Chunk " + (i + 1) + "/" + chunks.size() + " OK");
                }

                logDebug("UploadController: Execute");
                sendFramedMessage(up.getExecuteMessage());
                awaitUploadResponse(5000);

                // Cache hash so next reconnect can skip upload.
                setCachedProgramHash(connectedDeviceAddress, PROGRAM_HASH);

                // Wait for capability declaration before signalling ready.
                waitForCapability(3000);

                final String dn = connectedDeviceName;
                final String[] cap = buildCapabilityArgs();
                mainHandler.post(() -> HubConnected(dn, cap[0], cap[1], cap[2], cap[3]));

            } catch (Exception e) {
                logDebug("UploadController error: " + e);
                mainHandler.post(() -> ErrorOccurred("Upload failed: " + e.getMessage()));
            } finally {
                uploadInProgress = false;
            }
        }, "LegoSpikeUpload").start();
    }

    /** Blocks the upload thread until capability arrives or timeoutMs elapses. */
    private boolean waitForCapability(long timeoutMs) {
        return capabilityStore.waitForCapability(timeoutMs);
    }

    private String getCachedProgramHash(String address) {
        // In-memory cache checked first (fastest, works within a session).
        if (memCache.containsKey(address)) return memCache.get(address);
        // SharedPreferences for cross-session persistence.
        try {
            android.content.SharedPreferences prefs = form.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
            return prefs.getString("phash_" + address, null);
        } catch (Exception e) {
            logDebug("getCachedProgramHash error: " + e.getMessage());
            return null;
        }
    }

    private void setCachedProgramHash(String address, String hash) {
        memCache.put(address, hash);
        try {
            android.content.SharedPreferences prefs = form.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
            prefs.edit().putString("phash_" + address, hash).apply();
        } catch (Exception e) {
            logDebug("setCachedProgramHash error: " + e.getMessage());
        }
    }

    private byte[] awaitUploadResponse(long timeoutMs) {
        try {
            return uploadResponseQueue.poll(timeoutMs,
                java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================
    void logDebug(String msg) { if (debugMode) Log.d(LOG_TAG, msg); }

    private boolean isLegoSpikeHub(String name) {
        if (name == null || name.isEmpty()) return false;
        if (name.equalsIgnoreCase(customDeviceName)) return true;
        for (String n : LEGO_HUB_NAMES) { if (name.equals(n)) return true; }
        String lower = name.toLowerCase();
        return lower.contains("lego") || lower.contains("spike") || lower.contains("hub");
    }

    private List<LegoHub> getVisibleHubs() {
        List<LegoHub> v = new ArrayList<>();
        for (LegoHub h : legoHubs) { if (h.isVisible()) v.add(h); }
        return v;
    }

    private List<LegoHub> visibleFrom(List<LegoHub> list) {
        List<LegoHub> v = new ArrayList<>();
        for (LegoHub h : list) { if (h.isVisible()) v.add(h); }
        return v;
    }

    private String hubNames(List<LegoHub> hubs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hubs.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(hubs.get(i).getName());
        }
        return sb.toString();
    }
}
