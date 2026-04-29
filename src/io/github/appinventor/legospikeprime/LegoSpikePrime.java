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

// MessageBuilder, MessageFramer, ProgramUploader, ResponseParser are now in the
// same package (io.github.appinventor.legospikeprime) — no imports needed.

/**
 * LegoSpikePrime — MIT App Inventor extension for LEGO SPIKE Prime hubs.
 *
 * Communicates over BLE using the SPIKE Prime 3.x fd02 service UUID.
 * All outbound data goes through the COBS/XOR framing pipeline
 * (MessageFramer.pack) before being written to the RX characteristic.
 * Inbound data from the TX characteristic is accumulated in a byte buffer
 * and dispatched as complete frames (0x01 … 0x02) via MessageFramer.unpack.
 */
@SimpleObject(external = true)
@DesignerComponent(version = 1,
    description = "Extension for communicating with LEGO SPIKE Prime hubs via BLE",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = "aiwebres/legospike.png")
public class LegoSpikePrime extends AndroidNonvisibleComponent {

    private static final String LOG_TAG = "LegoSpikePrime";

    // =========================================================================
    // SPIKE Prime 3.x BLE UUIDs  —  DO NOT CHANGE (CLAUDE.md Rule 1)
    // These are specific to SPIKE Prime 3.x firmware and MUST NOT be replaced
    // with the generic LEGO Wireless Protocol UUIDs (00001623...).
    // =========================================================================
    public static final String SPIKE_SERVICE_UUID = "0000fd02-0000-1000-8000-00805f9b34fb";
    public static final String RX_CHAR_UUID       = "0000fd02-0001-1000-8000-00805f9b34fb";
    public static final String TX_CHAR_UUID       = "0000fd02-0002-1000-8000-00805f9b34fb";

    // 20 bytes = minimum safe BLE write size (fits within 23-byte ATT MTU floor).
    // InfoResponse updates this to the hub's actual max_packet_size.
    private static final int DEFAULT_MAX_PACKET_SIZE = 20;

    // Milliseconds between BLE write requests during a program upload.
    // Gives the hub time to process each chunk before the next arrives.
    private static final int CHUNK_DELAY_MS = 100;

    // Hub program slot used by UploadController.
    private static final int CONTROLLER_SLOT = 0;

    /**
     * hub_controller.py embedded as a String constant (comments stripped).
     * Sourced from src/resources/hub_controller.py.
     * Receives TunnelMessage payloads, drives motors via hub.port.X.motor.run(speed).
     */
    // Production hub controller. Lights centre LED on start. Receives TunnelMessage
    // motor commands in 5-char chunks {port A-F}{+|-}{NNN}, runs motor at NNN×10 deg/s,
    // acknowledges each command with tunnel.send(b'rdy').
    private static final String HUB_CONTROLLER_PROGRAM =
        "from hub import light_matrix, port\n" +
        "import hub\n" +
        "import motor\n" +
        "light_matrix.set_pixel(2, 2, 100)\n" +
        "tunnel = hub.config['module_tunnel']\n" +
        "PORTS = {'A': port.A, 'B': port.B, 'C': port.C, 'D': port.D, 'E': port.E, 'F': port.F}\n" +
        "def on_message(data):\n" +
        "    if not isinstance(data, str):\n" +
        "        data = ''.join(chr(b) for b in data)\n" +
        "    i = 0\n" +
        "    while i + 5 <= len(data):\n" +
        "        p, s, n = data[i], data[i + 1], data[i + 2:i + 5]\n" +
        "        if p in PORTS and s in ('+', '-') and n.isdigit():\n" +
        "            motor.run(PORTS[p], int(s + n) * 11)\n" +
        "        i += 5\n" +
        "    tunnel.send(b'rdy')\n" +
        "tunnel.callback(on_message)\n" +
        "tunnel.send(b'rdy')\n" +
        "while True:\n" +
        "    pass\n";

    // =========================================================================
    // State
    // =========================================================================
    private Component bluetoothLE;
    private BluetoothInterfaceImpl bluetoothInterface;

    private volatile boolean isConnected   = false;
    private volatile boolean isScanning    = false;
    // CLAUDE.md Rule 4: must stop scanning before connecting; resume on failure/disconnect.
    private volatile boolean wasScanningBeforeConnection = false;

    private String connectedDeviceAddress = "";
    private String connectedDeviceName    = "";

    // Effective max packet size for sendFramedMessage(); updated when InfoResponse arrives.
    private int maxPacketSize = DEFAULT_MAX_PACKET_SIZE;
    // Effective max chunk size for program upload; updated when InfoResponse arrives.
    // 445 is the value etomasfe hard-codes when skipping InfoRequest.
    private int maxChunkSize  = 445;

    // Sequential upload: flag + blocking queue so UploadController waits for
    // each hub acknowledgment before sending the next message.
    private volatile boolean uploadInProgress = false;
    private final java.util.concurrent.LinkedBlockingQueue<byte[]> uploadResponseQueue =
        new java.util.concurrent.LinkedBlockingQueue<>(8);

    // Dynamic proxy registered with BluetoothLE as a BluetoothConnectionListener.
    private Object connectionListenerProxy = null;

    // BLE address we are in the process of connecting to (set in ConnectToHub,
    // cleared after connection succeeds or fails).
    private volatile String pendingConnectAddress = "";

    // Polling fallback: detects connection via IsDeviceConnected() if the dynamic
    // proxy listener fails (e.g., older BLE extension build).
    private Timer   connectionPollTimer             = null;
    private static final int CONNECTION_POLL_INTERVAL_MS = 500;
    private static final int CONNECTION_POLL_TIMEOUT_MS  = 10000;

    // =========================================================================
    // Receive buffer for incoming TX characteristic notifications.
    // SPIKE Prime frames: [0x01] … [0x02]
    // Bytes accumulate here across potentially fragmented BLE packets.
    // =========================================================================
    private final List<Byte> receiveBuffer = new ArrayList<>();

    // =========================================================================
    // Scanning / device-discovery state
    // =========================================================================
    private boolean debugMode      = true;
    private int     scanInterval   = 1000; // ms between RSSI-staleness checks
    private Timer   scanTimer;
    private Handler mainHandler;

    // Known hub display names used as a fallback filter alongside UUID detection.
    private String customDeviceName = "LEGO Hub";
    private static final Set<String> LEGO_HUB_NAMES = new HashSet<>(Arrays.asList(
        "MITNodeHub", "LEGO Technic Hub", "LEGO Hub", "SPIKE Prime Hub", "SPIKE Hub"
    ));

    // All discovered LEGO hubs (visible + hidden ghost devices).
    private final List<LegoHub> legoHubs = new ArrayList<>();

    // =========================================================================
    // LegoHub — inner class with RSSI-staleness detection.
    // CLAUDE.md Rule 2: DO NOT REMOVE this class or its isVisible() logic.
    // Ghost devices (BLE cache artefacts) have static RSSI over 3+ scans.
    // =========================================================================
    private class LegoHub {
        private final String name;
        private final String address;
        private int    bleIndex;
        private long   lastSeenTimestamp;
        private int    lastRssi      = Integer.MIN_VALUE;
        private int    rssiStaleCount = 0;
        private Boolean frozenVisibility = null; // set only on snapshot copies

        LegoHub(String name, String address, int bleIndex) {
            this.name    = name;
            this.address = address;
            this.bleIndex = bleIndex;
            this.lastSeenTimestamp = System.currentTimeMillis();
        }

        /** Snapshot copy with frozen visibility for change-delta calculation. */
        LegoHub(LegoHub src, boolean frozen) {
            this.name              = src.name;
            this.address           = src.address;
            this.bleIndex          = src.bleIndex;
            this.lastSeenTimestamp = src.lastSeenTimestamp;
            this.lastRssi          = src.lastRssi;
            this.rssiStaleCount    = src.rssiStaleCount;
            this.frozenVisibility  = frozen;
        }

        String getName()    { return name; }
        String getAddress() { return address; }
        int    getBleIndex(){ return bleIndex; }

        void updateLastSeen() { lastSeenTimestamp = System.currentTimeMillis(); }

        /** Returns true when RSSI changed (fresh advertisement); false when stale. */
        boolean updateRssi(int newRssi) {
            if (lastRssi == newRssi) { rssiStaleCount++; return false; }
            lastRssi = newRssi; rssiStaleCount = 0; return true;
        }

        /** Hybrid staleness: hidden only when BOTH RSSI and timestamp are stale. */
        boolean isVisible() {
            if (frozenVisibility != null) return frozenVisibility;
            boolean rssiStale = rssiStaleCount >= 3;
            boolean timeStale = (System.currentTimeMillis() - lastSeenTimestamp)
                                 > (2L * LegoSpikePrime.this.scanInterval);
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
    public LegoSpikePrime(ComponentContainer container) {
        super(container.$form());
        mainHandler        = new Handler(Looper.getMainLooper());
        bluetoothInterface = new BluetoothInterfaceImpl();
        bluetoothInterface.setExtension(this);
        logDebug("LegoSpikePrime initialised — service UUID: " + SPIKE_SERVICE_UUID);
    }

    // =========================================================================
    // BluetoothDevice property — wires the BluetoothLE component
    // =========================================================================
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "The BluetoothLE component used for BLE communication")
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COMPONENT
        + ":edu.mit.appinventor.ble.BluetoothLE")
    public void BluetoothDevice(Component ble) {
        // Remove the old proxy listener before switching the BLE component
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

        // Register a dynamic proxy that implements BluetoothConnectionListener.
        // This is the primary mechanism for detecting connection / disconnection.
        // (BluetoothLE.java: addConnectionListener / BluetoothLEint.java: fires onConnected)
        try {
            // getClassLoader() can return null for bootstrap-loaded classes on Android.
            // Fall back to the thread context classloader if needed.
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
            // Log type + message so we can diagnose NPE (null message) vs ClassNotFoundException
            logDebug("Could not register connection listener: "
                + e.getClass().getSimpleName() + ": " + e.getMessage());
            if (e.getCause() != null) {
                logDebug("  Caused by: " + e.getCause().getClass().getSimpleName()
                    + ": " + e.getCause().getMessage());
            }
            // Connection polling fallback (started in ConnectToHub) will still detect connection.
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

    /** Internal — scan timer interval is fixed at 1000ms, not student-configurable. */
    public void ScanInterval(int ms) { scanInterval = Math.max(100, ms); }

    /** Internal getter. */
    public int ScanInterval() { return scanInterval; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "True while a BLE scan is running")
    public boolean IsScanning() { return isScanning; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "True when connected to a hub")
    public boolean IsConnected() { return isConnected; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Name of the currently connected hub, or empty string")
    public String ConnectedDeviceName() { return connectedDeviceName; }

    /** Internal — raw BLE MAC address is not meaningful to students. */
    public String ConnectedDeviceAddress() { return connectedDeviceAddress; }

    // =========================================================================
    // Scanning
    // =========================================================================

    /**
     * Scan for SPIKE Prime hubs advertising the fd02 service UUID.
     * Attempts UUID-filtered scanning first; falls back to a general scan.
     * Fires HubFound for each qualifying device discovered.
     */
    @SimpleFunction(description = "Start scanning for LEGO SPIKE Prime hubs")
    public void StartScanning() {
        if (bluetoothLE == null) { ErrorOccurred("BluetoothLE component not set"); return; }
        if (isScanning) StopScanning();

        legoHubs.clear();
        logDebug("StartScanning — attempting UUID-filtered scan for " + SPIKE_SERVICE_UUID);

        boolean started = false;
        // Try UUID-filtered scan first (available in newer BLE extension builds).
        for (String method : new String[]{"StartScanningWithUUIDs", "ScanForService",
                                          "StartScanningFiltered"}) {
            try {
                bluetoothLE.getClass()
                    .getMethod(method, String.class)
                    .invoke(bluetoothLE, SPIKE_SERVICE_UUID);
                logDebug("UUID-filtered scan started via " + method);
                started = true;
                break;
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                logDebug(method + " failed: " + e.getMessage());
            }
        }

        if (!started) {
            // Fall back to unfiltered scan; device names are checked in CheckAllDevices.
            try {
                bluetoothLE.getClass().getMethod("StartScanning").invoke(bluetoothLE);
                logDebug("Unfiltered BLE scan started (UUID filter not available)");
                started = true;
            } catch (Exception e) {
                ErrorOccurred("Cannot start scan: " + e.getMessage()); return;
            }
        }

        isScanning = true;
        startScanTimer();
        ScanningStarted();
    }

    /** Stop the current BLE scan. */
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

    /**
     * Iterate all BLE-cached devices, update RSSI staleness, fire HubListChanged
     * when visibility changes.  CLAUDE.md Rule 2: DO NOT REMOVE this method.
     */
    /** Internal RSSI-staleness maintenance — called by scan timer. */
    public void CheckAllDevices() {
        if (bluetoothLE == null) return;
        try {
            // Snapshot visibility before update
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

    /** CLAUDE.md Rule 3: null-safe BLE index inspection. */
    private String checkBLEDeviceAtIndex(int bleIndex, Set<String> seen) {
        try {
            String name = (String) bluetoothLE.getClass()
                .getMethod("FoundDeviceName", int.class).invoke(bluetoothLE, bleIndex);
            String addr = (String) bluetoothLE.getClass()
                .getMethod("FoundDeviceAddress", int.class).invoke(bluetoothLE, bleIndex);
            Integer rssi = (Integer) bluetoothLE.getClass()
                .getMethod("FoundDeviceRssi", int.class).invoke(bluetoothLE, bleIndex);

            // CLAUDE.md Rule 3: always null-check address before map access
            if (addr == null) return null;

            if (!isLegoSpikeHub(name)) return null;

            seen.add(addr);

            // Find or create hub entry
            LegoHub existing = null;
            for (LegoHub h : legoHubs) {
                if (addr.equals(h.getAddress())) { existing = h; break; }
            }
            if (existing == null) {
                LegoHub hub = new LegoHub(name != null ? name : "SPIKE Hub", addr, bleIndex);
                if (rssi != null) hub.updateRssi(rssi);
                legoHubs.add(hub);
                // Fire HubFound on the main thread for the new discovery
                final String finalName = hub.getName();
                final String finalAddr = addr;
                mainHandler.post(() -> HubFound(finalName, finalAddr));
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

    /** Compare visibility snapshots and fire HubListChanged if anything changed. */
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
    public String HubList() {
        return hubNames(getVisibleHubs());
    }

    @SimpleFunction(description = "Number of visible LEGO SPIKE Prime hubs")
    public int HubCount() { return getVisibleHubs().size(); }

    @SimpleFunction(description = "Name of hub at 1-based index in visible hub list")
    public String HubName(int index) {
        List<LegoHub> v = getVisibleHubs();
        return (index >= 1 && index <= v.size()) ? v.get(index-1).getName() : "";
    }

    /** Internal — students connect by index; raw MAC addresses aren't student-meaningful. */
    public String GetLegoHubAddress(int index) {
        List<LegoHub> v = getVisibleHubs();
        return (index >= 1 && index <= v.size()) ? v.get(index-1).getAddress() : "";
    }

    // =========================================================================
    // Connection
    // =========================================================================

    /**
     * Connect to a hub by its 1-based index in the visible hub list.
     * Stops scanning before connecting (CLAUDE.md Rule 4).
     * After BLE fires Connected, onConnected() subscribes to the TX characteristic.
     */
    @SimpleFunction(description =
        "Connect to the LEGO SPIKE Prime hub at the given index (1-based)")
    public void ConnectToHub(int index) {
        if (bluetoothLE == null) { ErrorOccurred("BluetoothLE component not set"); return; }

        List<LegoHub> visible = getVisibleHubs();
        if (index < 1 || index > visible.size()) {
            ErrorOccurred("Invalid hub index: " + index); return;
        }
        // Cancel any in-flight connection attempt (polling or BLE connecting).
        stopConnectionPolling();
        pendingConnectAddress = "";
        // Disconnect if already connected.
        if (isConnected) Disconnect();

        // Only stop scanning if currently scanning (CLAUDE.md Rule 4).
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

    /** Disconnect from the currently connected hub. Safe to call multiple times. */
    @SimpleFunction(description = "Disconnect from the currently connected hub")
    public void Disconnect() {
        stopConnectionPolling();
        pendingConnectAddress = "";
        if (bluetoothLE == null) return;
        if (!isConnected) {
            logDebug("Disconnect called but not connected — ignored");
            return;
        }
        // Mark as disconnected immediately so re-entrant calls are blocked
        // before the async BLE confirmation arrives via onDisconnected().
        isConnected = false;
        logDebug("Disconnect → " + connectedDeviceName);
        try {
            bluetoothLE.getClass().getMethod("Disconnect").invoke(bluetoothLE);
        } catch (Exception e) {
            // BLE may already be disconnected; state is already cleaned up above.
            logDebug("BLE Disconnect: " + e.getMessage());
        }
    }

    /**
     * Call this from App Inventor blocks when BluetoothLE.ConnectionFailed fires.
     * Stops connection polling immediately (no need to wait for the 10-second timeout),
     * resumes scanning if scanning was active before the connect attempt, and fires
     * ErrorOccurred with the reason supplied by the BLE component.
     *
     * Wiring: when BluetoothLE.ConnectionFailed(reason) → call LegoSpikePrime.OnConnectionFailed(reason)
     *
     * @param reason human-readable failure reason from the BluetoothLE component
     */
    /** Internal: called by connection polling timeout. ErrorOccurred is the student-facing signal. */
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

    /**
     * Subscribe to TX characteristic notifications.
     * Called from onConnected() immediately after GATT connection is established.
     * Hub → App data flows through this subscription.
     */
    private void registerForTXNotifications() {
        if (bluetoothLE == null) return;
        logDebug("Subscribing to TX notifications: " + TX_CHAR_UUID);
        if (registerForBytesViaInner()) {
            logDebug("TX notifications auto-wired via BLEResponseHandler — no BytesReceived block needed");
            return;
        }
        // Fallback: public RegisterForBytes (fires BytesReceived via EventDispatcher).
        // If this path is taken, wire BluetoothLE.BytesReceived → OnBytesReceived.
        try {
            bluetoothLE.getClass()
                .getMethod("RegisterForBytes", String.class, String.class, boolean.class)
                .invoke(bluetoothLE, SPIKE_SERVICE_UUID, TX_CHAR_UUID, false);
            logDebug("TX notification subscription OK (fallback — wire BytesReceived if needed)");
        } catch (Exception e) {
            logDebug("RegisterForBytes failed: " + e.getMessage());
            ErrorOccurred("TX subscription failed: " + e.getMessage());
        }
    }

    /**
     * Registers directly on BluetoothLEint.inner's BLEResponseHandler, bypassing
     * EventDispatcher entirely so no user BytesReceived block wiring is required.
     */
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
                                logDebug("BLE bytes (auto): " + vals.size()
                                    + " from " + charUuid);
                                for (Object v : vals) {
                                    try {
                                        receiveBuffer.add(
                                            (byte)(((Number) v).intValue() & 0xFF));
                                    } catch (Exception ignored) {}
                                }
                                logDebug("  receiveBuffer: " + receiveBuffer.size());
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
    // Outbound — sendFramedMessage
    // =========================================================================

    /**
     * Write a fully-framed message (output of MessageFramer.pack) to the hub's
     * RX characteristic, splitting into chunks of maxPacketSize if needed.
     *
     * @param framedMessage bytes produced by MessageFramer.pack(rawMsg)
     */
    private void sendFramedMessage(byte[] framedMessage) {
        if (bluetoothLE == null || !isConnected) {
            logDebug("sendFramedMessage: not connected, dropping message");
            return;
        }
        logDebug("sendFramedMessage: " + framedMessage.length
            + " bytes, packetSize=" + maxPacketSize);
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
                    false /*unsigned*/, packet);
                logDebug("  wrote packet [" + offset + ".." + (end-1) + "]");
            }
        } catch (Exception e) {
            logDebug("sendFramedMessage error: " + e);
            ErrorOccurred("Send error: " + e.getMessage());
        }
    }

    // =========================================================================
    // Inbound — frame buffer and detection
    // =========================================================================

    /**
     * Call this from App Inventor blocks when the BluetoothLE BytesReceived
     * event fires, wired to the TX characteristic.
     * Accumulates bytes and extracts complete SPIKE Prime frames (0x01 … 0x02).
     *
     * @param serviceUuid     should equal SPIKE_SERVICE_UUID
     * @param characteristicUuid should equal TX_CHAR_UUID
     * @param byteValues      unsigned byte values from the BLE notification
     */
    /**
     * Wire BluetoothLE.BytesReceived to this method in your App Inventor blocks.
     * Required as a fallback when automatic BLE byte interception is unavailable
     * on this BLE extension version.
     */
    @SimpleFunction(description =
        "Wire BluetoothLE.BytesReceived to this to receive data from the hub. "
        + "Add one block: when BluetoothLE1.BytesReceived → call LegoSpikePrime1.OnBytesReceived")
    public void OnBytesReceived(String serviceUuid,
                                       String characteristicUuid,
                                       YailList byteValues) {
        if (!TX_CHAR_UUID.equalsIgnoreCase(characteristicUuid)) return;
        for (Object item : byteValues.toArray()) {
            try {
                receiveBuffer.add((byte)(Integer.parseInt(item.toString()) & 0xFF));
            } catch (NumberFormatException ignored) {}
        }
        logDebug("OnBytesReceived: " + byteValues.size() + " bytes");
        processReceiveBuffer();
    }

    /**
     * Scan receiveBuffer for complete SPIKE Prime frames and dispatch each one.
     *
     * Frame boundary rule: 0x02 (DELIMITER) is the ONLY mandatory frame marker.
     *
     * The leading 0x01 "priority byte" is OPTIONAL. We add it to outbound
     * messages (high-priority) but the hub never adds it to its responses
     * (low-priority). The old code skipped everything that didn't start with
     * 0x01, silently discarding all hub responses.
     *
     * Scanning for 0x02 as the end marker is safe because after COBS encoding
     * and XOR-with-0x03 the encoded body can never contain 0x02 — it only
     * appears as the appended delimiter. MessageFramer.unpack() already handles
     * both the with-0x01 and without-0x01 cases.
     */
    private void processReceiveBuffer() {
        while (!receiveBuffer.isEmpty()) {
            // Find the next 0x02 (frame end delimiter)
            int endIdx = -1;
            for (int i = 0; i < receiveBuffer.size(); i++) {
                if ((receiveBuffer.get(i) & 0xFF) == 0x02) { endIdx = i; break; }
            }

            if (endIdx == -1) break; // No complete frame yet — wait for more bytes

            // Extract complete frame [0 .. endIdx] inclusive
            byte[] frame = new byte[endIdx + 1];
            for (int i = 0; i <= endIdx; i++) frame[i] = receiveBuffer.get(i);
            for (int i = 0; i <= endIdx; i++) receiveBuffer.remove(0);

            logDebug("Received frame: " + frame.length + " bytes");
            handleCompleteFrame(frame);
        }
    }

    /**
     * Decode a complete received frame and route by message ID.
     */
    private void handleCompleteFrame(byte[] frame) {
        try {
            byte[] raw = MessageFramer.unpack(frame);
            if (raw == null || raw.length == 0) {
                logDebug("handleCompleteFrame: empty after unpack");
                return;
            }
            int msgId = raw[0] & 0xFF;
            logDebug("Received frame, msgId=0x" + String.format("%02X", msgId)
                + " rawLen=" + raw.length);

            if (msgId == ResponseParser.MSG_INFO_RESPONSE) {
                handleInfoResponse(raw);
            } else if (msgId == 0x0D || msgId == 0x11 || msgId == 0x1F || msgId == 0x47) {
                handleStatusResponse(raw);
            } else if (msgId == 0x20) {
                // ProgramFlowNotification: stop=0x01 means program stopped, 0x00 means started
                if (raw.length >= 2) {
                    logDebug("ProgramFlowNotification: "
                        + (raw[1] == 0 ? "program started" : "program stopped"));
                }
            } else if (msgId == 0x21) {
                // ConsoleNotification — output from Python print() statements
                if (raw.length > 1) {
                    // strip message ID and trailing nulls, decode as UTF-8
                    int len = raw.length - 1;
                    while (len > 0 && raw[len] == 0) len--;
                    String console = new String(raw, 1, len,
                        java.nio.charset.StandardCharsets.UTF_8).trim();
                    logDebug("HUB PRINT: " + console);
                }
            } else if (msgId == 0x32) {
                // TunnelMessage from hub — payload starts at byte 3 (after [0x32][sizeL][sizeH])
                if (raw.length >= 3) {
                    int payloadSize = (raw[1] & 0xFF) | ((raw[2] & 0xFF) << 8);
                    logDebug("TunnelMessage from hub: " + payloadSize + " bytes payload");
                    if (raw.length >= 3 + payloadSize) {
                        try {
                            String text = new String(raw, 3, payloadSize,
                                java.nio.charset.StandardCharsets.UTF_8).trim();
                            logDebug("  tunnel payload (text): " + text);
                        } catch (Exception ex) {
                            logDebug("  tunnel payload (non-UTF8, " + payloadSize + " bytes)");
                        }
                    }
                }
            } else {
                logDebug("Unhandled msgId=0x" + String.format("%02X", msgId));
            }
        } catch (Exception e) {
            logDebug("handleCompleteFrame error: " + e);
        }
    }

    /** Parse an InfoResponse and silently update maxPacketSize / maxChunkSize. */
    private void handleInfoResponse(byte[] raw) {
        ResponseParser.InfoResponse info = ResponseParser.parseInfoResponse(raw);
        if (info == null) {
            logDebug("handleInfoResponse: parse failed (rawLen=" + raw.length + ")");
            return;
        }
        maxPacketSize = info.maxPacketSize;
        maxChunkSize  = info.maxChunkSize;
        logDebug("SPIKE FW: " + info.fwMajor + "." + info.fwMinor + "." + info.fwBuild
            + "  maxPacket=" + maxPacketSize + "  maxChunk=" + maxChunkSize);
    }

    /**
     * Log the success or failure of an upload-pipeline status response.
     * Covers StartFileUploadResponse (0x0D), TransferChunkResponse (0x11),
     * and ProgramFlowResponse (0x1F).
     */
    private void handleStatusResponse(byte[] raw) {
        int     msgId   = raw[0] & 0xFF;
        boolean success = ResponseParser.parseStatusResponse(raw);
        String  name    = statusResponseName(msgId);
        if (success) {
            logDebug("Status OK: " + name + " (0x" + String.format("%02X", msgId) + ")");
        } else {
            logDebug("Status NACK: " + name + " (0x" + String.format("%02X", msgId) + ")");
            if (msgId != 0x47) { // ClearSlot NACK is normal (slot may be empty)
                ErrorOccurred(name + " not acknowledged by hub");
            }
        }
        // Unblock UploadController if it is waiting for this response
        if (uploadInProgress) {
            uploadResponseQueue.offer(raw);
        }
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
    // Connection callbacks — fired by the dynamic proxy BluetoothConnectionListener
    // =========================================================================

    /**
     * Called by the dynamic proxy when BluetoothLE fires onConnected.
     * Extracts device name from the BLE component, falls back to our hub list.
     * Runs on the BLE callback thread — marshals to main thread via onConnected().
     */
    private void handleBleConnected(Object bleInstance) {
        stopConnectionPolling();
        if (isConnected) return; // guard: proxy + polling could both fire

        // Try to read the device name from the BluetoothLE component
        String name = "SPIKE Hub";
        if (bleInstance != null) {
            try {
                String n = (String) bleInstance.getClass()
                    .getMethod("ConnectedDeviceName").invoke(bleInstance);
                // "NEEDS_PERMISSION" is returned if Bluetooth Connect permission not granted yet
                if (n != null && !n.isEmpty() && !"NEEDS_PERMISSION".equals(n)) {
                    name = n;
                }
            } catch (Exception e) {
                logDebug("ConnectedDeviceName: " + e.getMessage());
            }
        }
        // Fall back to the name from our discovered hub list
        if ("SPIKE Hub".equals(name) && !pendingConnectAddress.isEmpty()) {
            for (LegoHub h : legoHubs) {
                if (pendingConnectAddress.equals(h.getAddress())) { name = h.getName(); break; }
            }
        }

        final String finalName = name;
        final String finalAddr = pendingConnectAddress;
        mainHandler.post(() -> onConnected(finalName, finalAddr));
    }

    /** Called by the dynamic proxy when BluetoothLE fires onDisconnected. */
    private void handleBleDisconnected() {
        stopConnectionPolling();
        mainHandler.post(() -> onDisconnected());
    }

    // =========================================================================
    // Connection polling fallback (Bug 5)
    // Detects connection via IsDeviceConnected() if the proxy callback does not fire.
    // =========================================================================

    private void startConnectionPolling(final String targetName, final String targetAddress) {
        stopConnectionPolling();
        final long startTime = System.currentTimeMillis();
        connectionPollTimer = new Timer("LegoConnPoll", true /*daemon*/);
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
                        logDebug("Connection detected by polling fallback");
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
        if (connectionPollTimer != null) {
            connectionPollTimer.cancel();
            connectionPollTimer = null;
        }
    }

    // =========================================================================
    // Connection state transitions
    // =========================================================================

    /** Called once GATT is up. Subscribes to TX, sends InfoRequest, fires HubConnected. */
    public void onConnected(String deviceName, String deviceAddress) {
        if (isConnected) return; // guard against double-calls (proxy + polling)
        stopConnectionPolling();

        isConnected            = true;
        connectedDeviceName    = deviceName;
        connectedDeviceAddress = deviceAddress;
        pendingConnectAddress  = "";
        receiveBuffer.clear();

        logDebug("onConnected: " + deviceName + " (" + deviceAddress + ")");

        // NOTE: RequestMTU is intentionally NOT called here.
        // Calling it before RegisterForBytes causes the Android BLE stack to
        // serialise the two GATT operations incorrectly, silently invalidating
        // the TX notification subscription (zero inbound data).
        // Instead, DEFAULT_MAX_PACKET_SIZE = 100 ensures all BLE writes fit
        // within any negotiated MTU without RequestMTU interference.

        registerForTXNotifications();

        logDebug("Sending InfoRequest");
        sendFramedMessage(MessageFramer.pack(MessageBuilder.buildInfoRequest()));

        // Auto-upload the hub controller — HubConnected fires when motors are ready.
        // 500ms delay gives the GATT subscription (RegisterForBytes CCCD write)
        // time to complete before the first upload message is sent.
        mainHandler.postDelayed(() -> UploadController(), 500);
    }

    /** Called when the GATT connection is lost. */
    public void onDisconnected() {
        stopConnectionPolling();
        // Guard: if Disconnect() already cleaned up state (set isConnected=false and
        // cleared names), the BLE async callback may still fire — don't double-fire events.
        if (!isConnected && connectedDeviceName.isEmpty()) {
            logDebug("onDisconnected: state already clean, skipping");
            return;
        }
        isConnected = false;
        final String n = connectedDeviceName;
        connectedDeviceName    = "";
        connectedDeviceAddress = "";
        pendingConnectAddress  = "";
        receiveBuffer.clear();
        logDebug("onDisconnected: " + n);

        // CLAUDE.md Rule 4: resume scanning if we were scanning before connecting
        if (wasScanningBeforeConnection) {
            wasScanningBeforeConnection = false;
            isScanning = true;
            startScanTimer();
            ScanningStarted();
        }

        mainHandler.post(() -> HubDisconnected());
    }

    // =========================================================================
    // App Inventor events
    // =========================================================================

    /** Fired when scanning starts. */
    @SimpleEvent(description = "Fired when BLE scanning starts")
    public void ScanningStarted() {
        mainHandler.post(() ->
            EventDispatcher.dispatchEvent(LegoSpikePrime.this, "ScanningStarted"));
    }

    /** Fired when scanning stops. */
    @SimpleEvent(description = "Fired when BLE scanning stops")
    public void ScanningStopped() {
        mainHandler.post(() ->
            EventDispatcher.dispatchEvent(LegoSpikePrime.this, "ScanningStopped"));
    }

    /**
     * Fired when a new LEGO SPIKE Prime hub is discovered during scanning.
     *
     * @param deviceName    advertised BLE device name
     * @param deviceAddress BLE MAC address
     */
    @SimpleEvent(description = "Fired when a LEGO SPIKE Prime hub is discovered during scanning")
    public void HubFound(String deviceName, String deviceAddress) {
        logDebug("HubFound: " + deviceName + " (" + deviceAddress + ")");
        EventDispatcher.dispatchEvent(this, "HubFound", deviceName, deviceAddress);
    }

    /** Fired when the visible hub list changes (new / retained / lost). */
    @SimpleEvent(description = "Fired when the visible hub list changes")
    public void HubListChanged(String newHubs, String retainedHubs,
                               String lostHubs, String allCurrentHubs) {
        logDebug("HubListChanged new=[" + newHubs + "] lost=[" + lostHubs + "]");
        mainHandler.post(() ->
            EventDispatcher.dispatchEvent(LegoSpikePrime.this, "HubListChanged",
                newHubs, retainedHubs, lostHubs, allCurrentHubs));
    }

    /**
     * Fired when successfully connected to a SPIKE Prime hub.
     *
     * @param deviceName    hub BLE name
     * @param deviceAddress hub BLE MAC address
     */
    /** Internal only — BLE layer connected but upload not yet complete. Not fired to students. */
    private void notifyBleConnected(String deviceName, String deviceAddress) {
        logDebug("BLE connected (internal): " + deviceName + " (" + deviceAddress + ")");
    }

    /** Fired when the connection to the hub is lost. */
    @SimpleEvent(description = "Fired when the connection to the hub is lost")
    public void HubDisconnected() {
        logDebug("HubDisconnected");
        EventDispatcher.dispatchEvent(this, "HubDisconnected");
    }

    /**
     * Fired when an error occurs in the extension.
     *
     * @param errorMessage human-readable description of the error
     */
    @SimpleEvent(description = "Fired when an error occurs")
    public void ErrorOccurred(String errorMessage) {
        logDebug("ErrorOccurred: " + errorMessage);
        EventDispatcher.dispatchEvent(this, "ErrorOccurred", errorMessage);
    }

    /**
     * Fired when the hub is fully connected and motors are ready to use.
     * This is the only connection event students need.
     */
    @SimpleEvent(description =
        "Fired when the hub is connected and ready — motors can now be controlled")
    public void HubConnected() {
        logDebug("HubConnected");
        EventDispatcher.dispatchEvent(this, "HubConnected");
    }

    // =========================================================================
    // Program upload and motor control
    // =========================================================================

    /**
     * Uploads hub_controller.py to slot 0 and starts it — called automatically
     * from onConnected(). Fires HubConnected() when motors are ready to use.
     */
    private void UploadController() {
        if (!isConnected) { ErrorOccurred("Not connected"); return; }
        new Thread(() -> {
            try {
                ProgramUploader up = new ProgramUploader(
                    "program.py", CONTROLLER_SLOT,
                    HUB_CONTROLLER_PROGRAM, maxChunkSize);

                uploadInProgress = true;
                uploadResponseQueue.clear();

                // Step 1: ClearSlotRequest — erase old program data before upload.
                // Required by the official LEGO protocol. NACK is acceptable (slot empty).
                logDebug("UploadController: sending ClearSlot");
                sendFramedMessage(MessageFramer.pack(
                    new byte[]{0x46, (byte)(CONTROLLER_SLOT & 0xFF)}));
                awaitUploadResponse(3000); // ignore result

                // Step 2: StartFileUploadRequest — MUST be acknowledged before chunks.
                logDebug("UploadController: sending StartFileUpload");
                sendFramedMessage(up.getStartUploadMessage());
                byte[] startResp = awaitUploadResponse(5000);
                if (startResp == null) {
                    mainHandler.post(() -> ErrorOccurred("Upload timeout: StartFileUpload"));
                    return;
                }
                if (!ResponseParser.parseStatusResponse(startResp)) {
                    mainHandler.post(() -> ErrorOccurred("StartFileUpload rejected by hub"));
                    return;
                }

                // Step 3: TransferChunkRequests — wait for each acknowledgment.
                List<byte[]> chunks = up.getChunkMessages();
                logDebug("UploadController: " + chunks.size() + " chunk(s) to send");
                for (int i = 0; i < chunks.size(); i++) {
                    sendFramedMessage(chunks.get(i));
                    byte[] chunkResp = awaitUploadResponse(5000);
                    if (chunkResp == null) {
                        final int ci = i + 1;
                        mainHandler.post(() -> ErrorOccurred("Upload timeout: chunk " + ci));
                        return;
                    }
                    logDebug("UploadController: chunk " + (i + 1) + "/" + chunks.size() + " acknowledged");
                }

                // Step 4: ProgramFlowRequest (Execute).
                logDebug("UploadController: sending Execute");
                sendFramedMessage(up.getExecuteMessage());
                awaitUploadResponse(5000); // wait for ProgramFlowResponse

                mainHandler.post(() -> HubConnected());

            } catch (Exception e) {
                logDebug("UploadController error: " + e);
                mainHandler.post(() -> ErrorOccurred("Upload failed: " + e.getMessage()));
            } finally {
                uploadInProgress = false;
            }
        }, "LegoSpikeUpload").start();
    }

    /** Block the upload thread until the hub sends a status response or the timeout expires. */
    private byte[] awaitUploadResponse(long timeoutMs) {
        try {
            return uploadResponseQueue.poll(timeoutMs,
                java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * Send a raw motor command string to the running hub controller via TunnelMessage.
     * Format: one or more 5-char chunks {@code {port A-F}{+|-}{NNN}} where NNN is deg/s.
     * Example: {@code "A+050B-030"} → port A at +50 deg/s, port B at -30 deg/s.
     */
    @SimpleFunction(description =
        "Send a motor command string (e.g. \"A+050B+050\") to the hub via TunnelMessage")
    public void SendMotorCommand(String command) {
        if (!isConnected) { ErrorOccurred("Not connected"); return; }
        if (command == null || command.isEmpty()) { ErrorOccurred("Empty command"); return; }
        logDebug("SendMotorCommand: " + command);
        sendFramedMessage(MessageFramer.pack(MessageBuilder.buildTunnelMessage(command)));
    }

    /**
     * Run the motor on the given port at the given speed.
     * Requires the hub controller program to be running (call UploadController first).
     *
     * @param port  port letter A–F
     * @param speed degrees/second, clamped to [-100, 100]
     */
    @SimpleFunction(description =
        "Run the motor on the given port (A-F) at the given speed (-100 to 100 deg/s)")
    public void RunMotor(String port, int speed) {
        if (!isConnected) { ErrorOccurred("Not connected"); return; }
        if (port == null || !port.matches("[A-Fa-f]")) {
            ErrorOccurred("Invalid port: " + port); return;
        }
        speed = Math.max(-100, Math.min(100, speed));
        String sign = (speed >= 0) ? "+" : "-";
        String cmd  = String.format("%s%s%03d", port.toUpperCase(), sign, Math.abs(speed));
        SendMotorCommand(cmd);
    }

    /**
     * Stop the motor on the given port (sends speed 0).
     * Requires the hub controller program to be running.
     *
     * @param port port letter A–F
     */
    @SimpleFunction(description = "Stop the motor on the given port (A-F)")
    public void StopMotor(String port) {
        RunMotor(port, 0);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private void logDebug(String msg) { if (debugMode) Log.d(LOG_TAG, msg); }

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

    private int portLetterToNumber(String p) {
        switch (p) {
            case "A": return 0; case "B": return 1; case "C": return 2;
            case "D": return 3; case "E": return 4; case "F": return 5;
            default: return 0;
        }
    }

    private byte[] buildSetLEDCommand(int r, int g, int b) {
        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));
        return new byte[]{0x0A, 0x00, 0x32, 0x00, 0x01, (byte)r, (byte)g, (byte)b};
    }

    private byte[] buildMotorCommand(int port, int power) {
        return new byte[]{0x0A, 0x00, (byte)port, 0x00, 0x01, (byte)power};
    }
}
