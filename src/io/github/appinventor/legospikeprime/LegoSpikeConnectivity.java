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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
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
        + "HubConnected fires when the hub is ready.",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = "aiwebres/legospike.png")
public class LegoSpikeConnectivity extends AndroidNonvisibleComponent {

    private static final String LOG_TAG = "LegoSpikeConnectivity";

    // SPIKE Prime 3.x BLE UUIDs — DO NOT CHANGE (CLAUDE.md Rule 1)
    public static final String SPIKE_SERVICE_UUID = "0000fd02-0000-1000-8000-00805f9b34fb";
    public static final String RX_CHAR_UUID       = "0000fd02-0001-1000-8000-00805f9b34fb";
    public static final String TX_CHAR_UUID       = "0000fd02-0002-1000-8000-00805f9b34fb";

    private static final int DEFAULT_MAX_PACKET_SIZE = 20;
    private static final int CHUNK_DELAY_MS          = 100;
    private static final int CONTROLLER_SLOT         = 0;

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
        "from hub import light_matrix, port\n" +
        "import hub, motor, motor_pair, time\n" +
        "print('hub.dir:', [x for x in dir(hub) if not x.startswith('_')])\n" +
        "try: print('sl.type:', type(hub.status_light))\n" +
        "except: print('sl.type: no attr')\n" +
        "try: print('sl.dir:', [x for x in dir(hub.status_light) if not x.startswith('_')])\n" +
        "except: print('sl.dir: no attr')\n" +
        "try:\n" +
        "    import color_sensor, distance_sensor, force_sensor, color\n" +
        "    _clr_map = {}\n" +
        "    for _n in ('BLACK','RED','GREEN','YELLOW','BLUE','WHITE','CYAN','MAGENTA','ORANGE','VIOLET','AZURE','NONE'):\n" +
        "        try: _clr_map[getattr(color, _n)] = _n\n" +
        "        except: pass\n" +
        "    _sensors_ok = True\n" +
        "except:\n" +
        "    _sensors_ok = False\n" +
        "    _clr_map = {}\n" +
        "light_matrix.set_pixel(2, 2, 100)\n" +
        "tunnel = hub.config['module_tunnel']\n" +
        "PORTS = {'A': port.A, 'B': port.B, 'C': port.C, 'D': port.D, 'E': port.E, 'F': port.F}\n" +
        "_IMG_CONST = {\n" +
        "    'HEART':'IMAGE_HEART','HEARTSMALL':'IMAGE_HEART_SMALL',\n" +
        "    'HAPPY':'IMAGE_HAPPY','SMILE':'IMAGE_SMILE','SAD':'IMAGE_SAD',\n" +
        "    'CONFUSED':'IMAGE_CONFUSED','ANGRY':'IMAGE_ANGRY','ASLEEP':'IMAGE_ASLEEP',\n" +
        "    'SURPRISED':'IMAGE_SURPRISED','YES':'IMAGE_YES','NO':'IMAGE_NO',\n" +
        "    'ARROWNORTH':'IMAGE_ARROW_N','ARROWEAST':'IMAGE_ARROW_E',\n" +
        "    'ARROWSOUTH':'IMAGE_ARROW_S','ARROWWEST':'IMAGE_ARROW_W'}\n" +
        "IMAGES = {'HEART':0,'HEART_SMALL':1,'HEARTSMALL':1,\n" +
        "          'HAPPY':2,'SMILE':3,'SAD':4,'CONFUSED':5,'ANGRY':6,'ASLEEP':7,\n" +
        "          'SURPRISED':8,'YES':12,'NO':13,\n" +
        "          'ARROW_N':16,'ARROWNORTH':16,'ARROW_E':18,'ARROWEAST':18,\n" +
        "          'ARROW_S':20,'ARROWSOUTH':20,'ARROW_W':22,'ARROWWEST':22}\n" +
        "_HUB_LED={'BLACK':0,'MAGENTA':1,'VIOLET':2,'BLUE':3,'AZURE':4,\n" +
        "          'CYAN':5,'GREEN':6,'YELLOW':7,'ORANGE':8,'RED':9,'WHITE':10}\n" +
        "_timer_start = time.ticks_ms()\n" +
        "def on_message(data):\n" +
        "    global _timer_start\n" +
        "    if not isinstance(data, str):\n" +
        "        data = ''.join(chr(b) for b in data)\n" +
        "    parts = data.split(':')\n" +
        "    resp = b'rdy'\n" +
        "    try:\n" +
        "        cmd = parts[0]\n" +
        "        if cmd == 'MTR' and len(parts) >= 3:\n" +
        "            p = parts[1].upper()\n" +
        "            act = parts[2].upper()\n" +
        "            if p in PORTS:\n" +
        "                if act == 'STOP':\n" +
        "                    motor.stop(PORTS[p])\n" +
        "                elif act in ('CW', 'CCW') and len(parts) >= 4:\n" +
        "                    spd = int(parts[3]) * 11\n" +
        "                    if act == 'CCW': spd = -spd\n" +
        "                    motor.run(PORTS[p], spd)\n" +
        "        elif cmd == 'MOV' and len(parts) >= 2:\n" +
        "            sub = parts[1].upper()\n" +
        "            if sub == 'PAIR' and len(parts) >= 4:\n" +
        "                lp, rp = parts[2].upper(), parts[3].upper()\n" +
        "                if lp in PORTS and rp in PORTS:\n" +
        "                    motor_pair.pair(motor_pair.PAIR_1, PORTS[lp], PORTS[rp])\n" +
        "            elif sub == 'FWD' and len(parts) >= 3:\n" +
        "                motor_pair.move(motor_pair.PAIR_1, 0, velocity=int(parts[2]) * 11)\n" +
        "            elif sub == 'BWD' and len(parts) >= 3:\n" +
        "                motor_pair.move(motor_pair.PAIR_1, 0, velocity=-(int(parts[2]) * 11))\n" +
        "            elif sub == 'STEER' and len(parts) >= 4:\n" +
        "                motor_pair.move(motor_pair.PAIR_1, int(parts[2]), velocity=int(parts[3]) * 11)\n" +
        "            elif sub == 'STOP':\n" +
        "                motor_pair.stop(motor_pair.PAIR_1)\n" +
        "        elif cmd == 'LGT' and len(parts) >= 2:\n" +
        "            sub = parts[1].upper()\n" +
        "            if sub == 'ON' and len(parts) >= 3:\n" +
        "                _n = parts[2].upper()\n" +
        "                _const = _IMG_CONST.get(_n)\n" +
        "                if _const:\n" +
        "                    try: _img = getattr(light_matrix, _const)\n" +
        "                    except AttributeError: _img = IMAGES.get(_n, 2)\n" +
        "                else:\n" +
        "                    _img = IMAGES.get(_n, 2)\n" +
        "                light_matrix.show_image(_img)\n" +
        "            elif sub == 'OFF':\n" +
        "                for _x in range(5):\n" +
        "                    for _y in range(5):\n" +
        "                        light_matrix.set_pixel(_x, _y, 0)\n" +
        "            elif sub == 'TXT' and len(parts) >= 3:\n" +
        "                light_matrix.write(':'.join(parts[2:]))\n" +
        "            elif sub == 'PIX' and len(parts) >= 5:\n" +
        "                light_matrix.set_pixel(int(parts[2]), int(parts[3]), int(parts[4]))\n" +
        "            elif sub == 'BTN' and len(parts) >= 3:\n" +
        "                _bn = parts[2].upper()\n" +
        "                try:\n" +
        "                    try: _cc = getattr(color, _bn)\n" +
        "                    except AttributeError: _cc = _HUB_LED.get(_bn, 10)\n" +
        "                    try: hub.status_light.on(_cc)\n" +
        "                    except: hub.status_light(_cc)\n" +
        "                except: pass\n" +
        "        elif cmd == 'SEN' and len(parts) >= 2 and _sensors_ok:\n" +
        "            sub = parts[1].upper()\n" +
        "            if sub == 'CLR' and len(parts) >= 3:\n" +
        "                p = parts[2].upper()\n" +
        "                if p in PORTS:\n" +
        "                    try:\n" +
        "                        c = color_sensor.color(PORTS[p])\n" +
        "                        resp = ('SEN:CLR:' + p + ':' + _clr_map.get(c, str(c))).encode()\n" +
        "                    except: resp = ('SEN:CLR:' + p + ':NONE').encode()\n" +
        "            elif sub == 'DST' and len(parts) >= 3:\n" +
        "                p = parts[2].upper()\n" +
        "                if p in PORTS:\n" +
        "                    try:\n" +
        "                        resp = ('SEN:DST:' + p + ':' + str(distance_sensor.distance(PORTS[p]))).encode()\n" +
        "                    except: resp = ('SEN:DST:' + p + ':-1').encode()\n" +
        "            elif sub == 'PRS' and len(parts) >= 3:\n" +
        "                p = parts[2].upper()\n" +
        "                if p in PORTS:\n" +
        "                    try:\n" +
        "                        resp = ('SEN:PRS:' + p + ':' + str(force_sensor.force(PORTS[p]))).encode()\n" +
        "                    except: resp = ('SEN:PRS:' + p + ':0').encode()\n" +
        "            elif sub == 'ISP' and len(parts) >= 3:\n" +
        "                p = parts[2].upper()\n" +
        "                if p in PORTS:\n" +
        "                    try:\n" +
        "                        resp = ('SEN:ISP:' + p + ':' + ('1' if force_sensor.pressed(PORTS[p]) else '0')).encode()\n" +
        "                    except: resp = ('SEN:ISP:' + p + ':0').encode()\n" +
        "            elif sub == 'TLT' and len(parts) >= 3:\n" +
        "                axis = parts[2].upper()\n" +
        "                try:\n" +
        "                    try:\n" +
        "                        angles = hub.motion_sensor.tilt_angles()\n" +
        "                    except AttributeError:\n" +
        "                        angles = hub.imu.tilt_angles()\n" +
        "                    val = {'PITCH': angles[0], 'ROLL': angles[1], 'YAW': angles[2]}.get(axis.upper(), 0)\n" +
        "                    resp = ('SEN:TLT:' + axis + ':' + str(val // 10)).encode()\n" +
        "                except: resp = ('SEN:TLT:' + axis + ':0').encode()\n" +
        "            elif sub == 'TMR':\n" +
        "                elapsed = time.ticks_diff(time.ticks_ms(), _timer_start) // 1000\n" +
        "                resp = ('SEN:TMR:' + str(elapsed)).encode()\n" +
        "            elif sub == 'TMRR':\n" +
        "                _timer_start = time.ticks_ms()\n" +
        "    except: resp = b'err'\n" +
        "    tunnel.send(resp)\n" +
        "tunnel.callback(on_message)\n" +
        "tunnel.send(b'rdy')\n" +
        "while True:\n" +
        "    pass\n";

    // =========================================================================
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
    private int maxChunkSize  = 445;

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
                            java.nio.charset.StandardCharsets.UTF_8).trim();
                        logDebug("TunnelMessage: " + text);
                        if (!"rdy".equals(text) && !"err".equals(text)) {
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

        mainHandler.post(() -> HubDisconnected());
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

    @SimpleEvent(description = "Fired when the hub is connected and ready to use")
    public void HubConnected(String deviceName) {
        logDebug("HubConnected: " + deviceName);
        EventDispatcher.dispatchEvent(this, "HubConnected", deviceName);
    }

    @SimpleEvent(description = "Fired when the connection to the hub is lost")
    public void HubDisconnected() {
        logDebug("HubDisconnected");
        EventDispatcher.dispatchEvent(this, "HubDisconnected");
    }

    @SimpleEvent(description = "Fired when an error occurs")
    public void ErrorOccurred(String errorMessage) {
        logDebug("ErrorOccurred: " + errorMessage);
        EventDispatcher.dispatchEvent(this, "ErrorOccurred", errorMessage);
    }

    // =========================================================================
    // Program upload
    // =========================================================================
    private void UploadController() {
        if (!isConnected) { ErrorOccurred("Not connected"); return; }
        new Thread(() -> {
            try {
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

                final String dn = connectedDeviceName;
                mainHandler.post(() -> HubConnected(dn));

            } catch (Exception e) {
                logDebug("UploadController error: " + e);
                mainHandler.post(() -> ErrorOccurred("Upload failed: " + e.getMessage()));
            } finally {
                uploadInProgress = false;
            }
        }, "LegoSpikeUpload").start();
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
