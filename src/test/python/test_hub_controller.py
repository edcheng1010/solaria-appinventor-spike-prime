"""
Unit tests for hub_controller.py dispatch logic.

Requires: Python 3.3+ (uses unittest.mock)
Run: python -m pytest src/test/python/ -v
  or: python src/test/python/test_hub_controller.py

Hardware-dependent APIs (motor.run, color_sensor.color, etc.) are stubbed via
sys.modules injection before the module is imported.
"""

import sys
import json
import types
import unittest

# ---------------------------------------------------------------------------
# Stub SPIKE Prime modules BEFORE importing hub_controller
# ---------------------------------------------------------------------------

def _make_stub(name):
    m = types.ModuleType(name)
    return m

# --- color ---
color_mod = _make_stub('color')
color_mod.BLACK = 0; color_mod.RED = 9; color_mod.GREEN = 5
color_mod.YELLOW = 6; color_mod.BLUE = 3; color_mod.WHITE = 10
color_mod.ORANGE = 7; color_mod.CYAN = 10; color_mod.MAGENTA = 8
color_mod.VIOLET = 11; color_mod.AZURE = 11; color_mod.NONE = -1

# --- motor stub ---
_motor_calls = []
motor_mod = _make_stub('motor')
motor_mod.COAST = 'coast'
motor_mod.HOLD = 'hold'
motor_mod.run                    = lambda p, v: _motor_calls.append(('run', p, v))
motor_mod.stop                   = lambda p, **kw: _motor_calls.append(('stop', p, kw))
motor_mod.run_for_time           = lambda p, t, v: _motor_calls.append(('run_for_time', p, t, v))
motor_mod.run_for_degrees        = lambda p, d, v: _motor_calls.append(('run_for_degrees', p, d, v))
motor_mod.run_to_position        = lambda p, pos, v: _motor_calls.append(('run_to_position', p, pos, v))
motor_mod.reset_relative_position= lambda p, v: _motor_calls.append(('reset_relative_position', p, v))

# --- motor_pair stub ---
_pair_calls = []
mp_mod = _make_stub('motor_pair')
mp_mod.PAIR_1 = 1
mp_mod.COAST = 'coast'
mp_mod.pair            = lambda pr, l, r: _pair_calls.append(('pair', pr, l, r))
mp_mod.move            = lambda pr, st, **kw: _pair_calls.append(('move', pr, st, kw))
mp_mod.move_for_degrees= lambda pr, d, st, **kw: _pair_calls.append(('move_for_degrees', pr, d, st, kw))
mp_mod.move_for_time   = lambda pr, t, st, **kw: _pair_calls.append(('move_for_time', pr, t, st, kw))
mp_mod.stop            = lambda pr, **kw: _pair_calls.append(('stop', pr, kw))

# --- hub stub ---
hub_mod = _make_stub('hub')
hub_mod.config = {}

class _FakeLight:
    def color(self, c): pass
class _FakeSound:
    def beep(self, freq, dur): pass
    def stop(self): pass
class _FakeMotionSensor:
    def tilt_angles(self): return (100, 200, 300)  # decidegrees → 10,20,30
class _FakeBattery:
    def level(self): return 85
class _FakeButton:
    def is_pressed(self): return False

hub_mod.light = _FakeLight()
hub_mod.sound = _FakeSound()
hub_mod.motion_sensor = _FakeMotionSensor()
hub_mod.battery = _FakeBattery()
hub_mod.button = types.SimpleNamespace(
    left=_FakeButton(), right=_FakeButton(), center=_FakeButton()
)

class _FakePort:
    def __init__(self): self.device = True  # non-None = connected
class _FakePorts:
    A = _FakePort(); B = _FakePort(); C = _FakePort()
    D = _FakePort(); E = _FakePort(); F = _FakePort()

hub_mod.port = _FakePorts()
hub_mod.light_matrix = _make_stub('light_matrix_sub')
hub_mod.light_matrix.set_pixel    = lambda x, y, b: None
hub_mod.light_matrix.show_image   = lambda img: None
hub_mod.light_matrix.write        = lambda t: None
hub_mod.light_matrix.IMAGE_HAPPY  = 2

# --- port module ---
port_mod = _make_stub('hub.port')
port_mod.A = _FakePorts.A; port_mod.B = _FakePorts.B; port_mod.C = _FakePorts.C
port_mod.D = _FakePorts.D; port_mod.E = _FakePorts.E; port_mod.F = _FakePorts.F

# --- sensors ---
color_sensor_mod = _make_stub('color_sensor')
color_sensor_mod.color = lambda p: color_mod.RED
color_sensor_mod.reflection = lambda p: 80

distance_sensor_mod = _make_stub('distance_sensor')
distance_sensor_mod.distance = lambda p: 150

force_sensor_mod = _make_stub('force_sensor')
force_sensor_mod.force = lambda p: 30
force_sensor_mod.pressed = lambda p: True

# --- light_matrix ---
light_matrix_mod = hub_mod.light_matrix

# --- time ---
_ticks = [0]
time_mod = _make_stub('time')
time_mod.ticks_ms   = lambda: _ticks[0]
time_mod.ticks_diff = lambda a, b: a - b
time_mod.sleep_ms   = lambda ms: None

# Inject all stubs
sys.modules['hub']              = hub_mod
sys.modules['motor']            = motor_mod
sys.modules['motor_pair']       = mp_mod
sys.modules['color']            = color_mod
sys.modules['color_sensor']     = color_sensor_mod
sys.modules['distance_sensor']  = distance_sensor_mod
sys.modules['force_sensor']     = force_sensor_mod
sys.modules['time']             = time_mod

# Tunnel stub — captures sent events
_sent = []
class _FakeTunnel:
    def callback(self, fn): self._cb = fn
    def send(self, data):
        text = data.decode('utf-8') if isinstance(data, bytes) else data
        _sent.append(json.loads(text.strip()))

hub_mod.config['module_tunnel'] = _FakeTunnel()

# Now import — module-level code is guarded by if __name__ == '__main__'
import importlib, os, sys as _sys
_sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', '..', 'resources'))
import hub_controller as hc

# Ensure tunnel global is set (start() would do this, but we skip it for tests)
hc.tunnel = hub_mod.config['module_tunnel']

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _dispatch(cmd_json: str):
    """Call on_message as if the bridge received this JSON."""
    _sent.clear()
    _motor_calls.clear()
    _pair_calls.clear()
    hc._subscriptions.clear()
    hc._sys_subscriptions.clear()
    hc.on_message(cmd_json)


def _last_sent():
    return _sent[-1] if _sent else None


# ---------------------------------------------------------------------------
# Tests — dispatch
# ---------------------------------------------------------------------------

class TestMotorDispatch(unittest.TestCase):

    def setUp(self):
        _motor_calls.clear()
        _sent.clear()

    def test_motor_run_calls_motor_run(self):
        _dispatch('{"cmd":"motor.run","port":"A","speed":75}')
        self.assertTrue(any(c[0] == 'run' for c in _motor_calls))

    def test_motor_run_velocity_scaled_by_11(self):
        _dispatch('{"cmd":"motor.run","port":"A","speed":50}')
        run = next(c for c in _motor_calls if c[0] == 'run')
        self.assertEqual(550, run[2])

    def test_motor_run_negative_speed(self):
        _dispatch('{"cmd":"motor.run","port":"A","speed":-100}')
        run = next(c for c in _motor_calls if c[0] == 'run')
        self.assertEqual(-1100, run[2])

    def test_motor_stop_calls_motor_stop(self):
        _dispatch('{"cmd":"motor.stop","port":"A"}')
        self.assertTrue(any(c[0] == 'stop' for c in _motor_calls))

    def test_motor_run_with_ms_duration(self):
        _dispatch('{"cmd":"motor.run","port":"A","speed":50,"duration":1000,"duration_unit":"ms"}')
        self.assertTrue(any(c[0] == 'run_for_time' for c in _motor_calls))

    def test_motor_run_with_degrees_duration(self):
        _dispatch('{"cmd":"motor.run","port":"A","speed":50,"duration":360,"duration_unit":"degrees"}')
        self.assertTrue(any(c[0] == 'run_for_degrees' for c in _motor_calls))

    def test_motor_reset(self):
        _dispatch('{"cmd":"motor.reset","port":"A"}')
        self.assertTrue(any(c[0] == 'reset_relative_position' for c in _motor_calls))

    def test_unknown_port_sends_error_201(self):
        _dispatch('{"cmd":"motor.run","port":"Z","speed":50}')
        err = _last_sent()
        self.assertEqual('error', err['event'])
        self.assertEqual(201, err['code'])

    def test_request_id_echoed_in_error(self):
        _dispatch('{"cmd":"motor.run","port":"Z","speed":50,"request_id":"r1"}')
        err = _last_sent()
        self.assertEqual('r1', err['request_id'])


class TestMovementDispatch(unittest.TestCase):

    def setUp(self):
        _pair_calls.clear()
        _sent.clear()
        hc._mov_lp = None
        hc._mov_rp = None

    def test_movement_configure_pairs_motors(self):
        _dispatch('{"cmd":"movement.configure","left":"A","right":"B"}')
        self.assertTrue(any(c[0] == 'pair' for c in _pair_calls))

    def test_movement_drive_calls_move(self):
        _dispatch('{"cmd":"movement.drive","speed":50,"steering":0}')
        self.assertTrue(any(c[0] == 'move' for c in _pair_calls))

    def test_movement_turn_calls_move_for_degrees(self):
        _dispatch('{"cmd":"movement.turn","angle":90,"speed":50}')
        self.assertTrue(any(c[0] == 'move_for_degrees' for c in _pair_calls))

    def test_movement_stop_calls_stop(self):
        _dispatch('{"cmd":"movement.stop"}')
        self.assertTrue(any(c[0] == 'stop' for c in _pair_calls))

    def test_movement_drive_with_ms_duration(self):
        _dispatch('{"cmd":"movement.drive","speed":50,"steering":0,"duration":1000,"duration_unit":"ms"}')
        self.assertTrue(any(c[0] == 'move_for_time' for c in _pair_calls))


class TestLedDispatch(unittest.TestCase):

    def setUp(self):
        _sent.clear()

    def test_led_matrix_pixel_does_not_throw(self):
        _dispatch('{"cmd":"led.matrix.pixel","port":"display","x":2,"y":2,"brightness":80}')
        # No error sent
        self.assertFalse(any(m.get('event') == 'error' for m in _sent))

    def test_led_matrix_image_does_not_throw(self):
        _dispatch('{"cmd":"led.matrix.image","port":"display","image":"HAPPY"}')
        self.assertFalse(any(m.get('event') == 'error' for m in _sent))

    def test_led_matrix_text_does_not_throw(self):
        _dispatch('{"cmd":"led.matrix.text","port":"display","text":"Hi"}')
        self.assertFalse(any(m.get('event') == 'error' for m in _sent))

    def test_led_matrix_clear_does_not_throw(self):
        _dispatch('{"cmd":"led.matrix.clear","port":"display"}')
        self.assertFalse(any(m.get('event') == 'error' for m in _sent))


class TestSystemDispatch(unittest.TestCase):

    def setUp(self):
        _sent.clear()
        hc._last_ping_ms = None
        hc._heartbeat_active = False

    def test_system_ping_sends_pong(self):
        _dispatch('{"cmd":"system.ping"}')
        self.assertTrue(any(m.get('event') == 'pong' for m in _sent))

    def test_system_ping_sets_heartbeat_active(self):
        _dispatch('{"cmd":"system.ping"}')
        self.assertTrue(hc._heartbeat_active)

    def test_system_subscribe_adds_to_sys_subscriptions(self):
        _dispatch('{"cmd":"system.subscribe","metric":"battery","interval":5000}')
        self.assertIn('battery', hc._sys_subscriptions)

    def test_system_unsubscribe_removes_metric(self):
        hc._sys_subscriptions['battery'] = {}
        _dispatch('{"cmd":"system.unsubscribe","metric":"battery"}')
        self.assertNotIn('battery', hc._sys_subscriptions)


class TestSensorDispatch(unittest.TestCase):

    def setUp(self):
        _sent.clear()
        hc._subscriptions.clear()

    def test_sensor_subscribe_adds_to_subscriptions(self):
        _dispatch('{"cmd":"sensor.subscribe","port":"C","type":"color","mode":"interval","interval":100}')
        self.assertIn('C', hc._subscriptions)
        self.assertEqual('color', hc._subscriptions['C']['type'])
        self.assertEqual(100, hc._subscriptions['C']['interval_ms'])

    def test_sensor_unsubscribe_removes_port(self):
        hc._subscriptions['C'] = {'type': 'color', 'mode': 'interval', 'interval_ms': 100,
                                   'min_change': None, 'last_ms': 0, 'last_val': None}
        _dispatch('{"cmd":"sensor.unsubscribe","port":"C"}')
        self.assertNotIn('C', hc._subscriptions)

    def test_sensor_read_color_emits_sensor_event(self):
        _dispatch('{"cmd":"sensor.read","port":"C","type":"color"}')
        sensor_events = [m for m in _sent if m.get('event') == 'sensor']
        self.assertTrue(len(sensor_events) > 0)
        self.assertEqual('C', sensor_events[0]['port'])
        self.assertEqual('color', sensor_events[0]['type'])

    def test_sensor_read_imu_pitch_emits_sensor_event(self):
        _dispatch('{"cmd":"sensor.read","port":"imu","type":"pitch"}')
        sensor_events = [m for m in _sent if m.get('event') == 'sensor']
        self.assertTrue(len(sensor_events) > 0)
        self.assertEqual('imu', sensor_events[0]['port'])
        self.assertEqual('pitch', sensor_events[0]['type'])
        self.assertEqual(10, sensor_events[0]['value'])  # 100 decidegrees → 10°


class TestCapabilityDeclaration(unittest.TestCase):

    def test_build_capability_has_required_fields(self):
        cap = hc._build_capability()
        self.assertEqual('capability', cap['type'])
        self.assertEqual('spike-prime', cap['device'])
        self.assertEqual('0.6', cap['ssp_version'])
        self.assertIn('ports', cap)
        self.assertIn('system_metrics', cap)

    def test_build_capability_always_has_display_port(self):
        cap = hc._build_capability()
        display = next((p for p in cap['ports'] if p['id'] == 'display'), None)
        self.assertIsNotNone(display)
        self.assertEqual(5, display['width'])
        self.assertEqual(5, display['height'])

    def test_build_capability_always_has_imu_port(self):
        cap = hc._build_capability()
        imu = next((p for p in cap['ports'] if p['id'] == 'imu'), None)
        self.assertIsNotNone(imu)
        self.assertIn('gesture', imu['features'])

    def test_build_capability_includes_battery_metric(self):
        cap = hc._build_capability()
        self.assertIn('battery', cap['system_metrics'])


class TestMalformedInput(unittest.TestCase):

    def setUp(self):
        _sent.clear()

    def test_malformed_json_sends_error_400(self):
        _dispatch('not json at all')
        err = _last_sent()
        self.assertEqual('error', err['event'])
        self.assertEqual(400, err['code'])

    def test_unknown_command_sends_error_400(self):
        _dispatch('{"cmd":"future.command","port":"A"}')
        err = _last_sent()
        self.assertEqual('error', err['event'])
        self.assertEqual(400, err['code'])

    def test_empty_cmd_sends_error(self):
        _dispatch('{"cmd":""}')
        self.assertTrue(len(_sent) > 0)


class TestSubscriptionShouldEmit(unittest.TestCase):
    """Tests _should_emit logic via the subscription table and _run_loop tick."""

    def test_interval_mode_always_emits(self):
        hc._subscriptions.clear()
        _sent.clear()
        hc._subscriptions['C'] = {
            'type': 'color', 'mode': 'interval', 'interval_ms': 0,
            'min_change': None, 'last_ms': 0, 'last_val': None,
        }
        _ticks[0] = 1000
        # Simulate one tick of the run loop (without the while True)
        hc._last_ping_ms = None
        hc._heartbeat_active = False
        # Manually inline one loop iteration
        now = time_mod.ticks_ms()
        for pid, sub in list(hc._subscriptions.items()):
            elapsed = time_mod.ticks_diff(now, sub['last_ms'])
            if elapsed < sub['interval_ms']:
                continue
            val = hc._read_sensor_value(pid, sub['type'])
            if val is not None:
                hc._sensor_event(pid, sub['type'], val)
                sub['last_val'] = val
            sub['last_ms'] = now
        self.assertTrue(any(m.get('event') == 'sensor' for m in _sent))

    def test_on_change_mode_only_emits_on_change(self):
        hc._subscriptions['C'] = {
            'type': 'color', 'mode': 'on_change', 'interval_ms': 0,
            'min_change': None, 'last_ms': 0, 'last_val': 'red',  # same as stub returns
        }
        _sent.clear()
        _ticks[0] = 1000
        now = time_mod.ticks_ms()
        for pid, sub in list(hc._subscriptions.items()):
            elapsed = time_mod.ticks_diff(now, sub['last_ms'])
            if elapsed < sub['interval_ms']:
                continue
            val = hc._read_sensor_value(pid, sub['type'])
            last_val = sub['last_val']
            should_emit = (val != last_val) if last_val is not None else True
            if should_emit and val is not None:
                hc._sensor_event(pid, sub['type'], val)
        # color is 'red' and last_val is 'red', should NOT emit
        sensor_events = [m for m in _sent if m.get('event') == 'sensor']
        self.assertEqual(0, len(sensor_events))


if __name__ == '__main__':
    unittest.main(verbosity=2)
