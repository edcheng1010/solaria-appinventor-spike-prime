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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import io.github.appinventor.legospike.MessageBuilder;
import io.github.appinventor.legospike.MessageFramer;
import io.github.appinventor.legospike.ProgramUploader;
import io.github.appinventor.legospike.ResponseParser;

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

    // Default max packet size used until InfoResponse provides the real value.
    // 512 bytes covers the maximum BLE ATT MTU; will be replaced by InfoResponse.
    private static final int DEFAULT_MAX_PACKET_SIZE = 512;

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
    private static final String HUB_CONTROLLER_PROGRAM =
        "import hub\n" +
        "from hub import port\n" +
        "\n" +
        "tunnel = hub.config['module_tunnel']\n" +
        "\n" +
        "PORTS = {\n" +
        "    'A': port.A, 'B': port.B, 'C': port.C,\n" +
        "    'D': port.D, 'E': port.E, 'F': port.F,\n" +
        "}\n" +
        "\n" +
        "def on_message(data):\n" +
        "    if not data:\n" +
        "        return\n" +
        "    if isinstance(data, (bytes, bytearray)):\n" +
        "        data = data.decode('utf-8')\n" +
        "    i = 0\n" +
        "    while i + 5 <= len(data):\n" +
        "        p, s, n = data[i], data[i + 1], data[i + 2:i + 5]\n" +
        "        if p in PORTS and s in ('+', '-') and n.isdigit():\n" +
        "            PORTS[p].motor.run(int(s + n))\n" +
        "        i += 5\n" +
        "    tunnel.send(b'rdy')\n" +
        "\n" +
        "tunnel.callback(on_message)\n" +
        "tunnel.send(b'rdy')\n" +
        "while True:\n" +
        "    pass\n";

    // =========================================================================
    // State
    // =========================================================================
    private Component bluetoothLE;
    private BluetoothInterfaceImpl bluetoothInterface;

    private boolean isConnected   = false;
    private boolean isScanning    = false;
    // CLAUDE.md Rule 4: must stop scanning before connecting; resume on failure/disconnect.
    private boolean wasScanningBeforeConnection = false;

    private String connectedDeviceAddress = "";
    private String connectedDeviceName    = "";

    // Effective max packet size for sendFramedMessage(); updated when InfoResponse arrives.
    private int maxPacketSize = DEFAULT_MAX_PACKET_SIZE;
    // Effective max chunk size for program upload; updated when InfoResponse arrives.
    // 445 is the value etomasfe hard-codes when skipping InfoRequest.
    private int maxChunkSize  = 445;

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
        this.bluetoothLE = ble;
        bluetoothInterface.setBluetoothLE(ble);
        logDebug("BluetoothLE component set");
    }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "The BluetoothLE component used for BLE communication")
    public Component BluetoothDevice() { return bluetoothLE; }

    // =========================================================================
    // Simple properties
    // =========================================================================
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Enable verbose logcat output")
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
        defaultValue = "True")
    public void DebugMode(boolean v) { debugMode = v; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Enable verbose logcat output")
    public boolean DebugMode() { return debugMode; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Custom BLE device name to match during scanning")
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_STRING,
        defaultValue = "LEGO Hub")
    public void CustomDeviceName(String v) { customDeviceName = v; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Custom BLE device name to match during scanning")
    public String CustomDeviceName() { return customDeviceName; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Milliseconds between RSSI-staleness checks")
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_INTEGER,
        defaultValue = "1000")
    public void ScanInterval(int ms) { scanInterval = Math.max(100, ms); }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Milliseconds between RSSI-staleness checks")
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

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "BLE address of the currently connected hub, or empty string")
    public String ConnectedDeviceAddress() { return connectedDeviceAddress; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Max BLE packet size (set automatically from InfoResponse)")
    public int MaxPacketSize() { return maxPacketSize; }

    // =========================================================================
    // Scanning
    // =========================================================================

    /**
     * Scan for SPIKE Prime hubs advertising the fd02 service UUID.
     * Attempts UUID-filtered scanning first; falls back to a general scan.
     * Fires HubFound for each qualifying device discovered.
     */
    @SimpleFunction(description =
        "Start scanning for LEGO SPIKE Prime hubs (service UUID 0000fd02-...)")
    public void ScanForHub() {
        if (bluetoothLE == null) { ErrorOccurred("BluetoothLE component not set"); return; }
        if (isScanning) StopScanning();

        legoHubs.clear();
        logDebug("ScanForHub — attempting UUID-filtered scan for " + SPIKE_SERVICE_UUID);

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

    /** Start scanning without UUID filter (legacy / manual control). */
    @SimpleFunction(description = "Start scanning for all LEGO SPIKE Prime hubs")
    public void StartScanning() {
        if (bluetoothLE == null) { ErrorOccurred("BluetoothLE component not set"); return; }
        if (isScanning) StopScanning();

        legoHubs.clear();
        logDebug("StartScanning");
        try {
            bluetoothLE.getClass().getMethod("StartScanning").invoke(bluetoothLE);
            isScanning = true;
            startScanTimer();
            ScanningStarted();
        } catch (Exception e) {
            ErrorOccurred("Error starting scan: " + e.getMessage());
        }
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
    @SimpleFunction(description = "Refresh hub list using RSSI staleness detection")
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
            HubListChanged(hubNames(gained), hubNames(kept), hubNames(lost), LegoHubsList());
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
    public String LegoHubsList() {
        return hubNames(getVisibleHubs());
    }

    @SimpleFunction(description = "Number of visible LEGO SPIKE Prime hubs")
    public int GetLegoHubCount() { return getVisibleHubs().size(); }

    @SimpleFunction(description = "Name of hub at 1-based index in visible hub list")
    public String GetLegoHubName(int index) {
        List<LegoHub> v = getVisibleHubs();
        return (index >= 1 && index <= v.size()) ? v.get(index-1).getName() : "";
    }

    @SimpleFunction(description = "BLE address of hub at 1-based index in visible hub list")
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
    public boolean ConnectToHub(int index) {
        if (bluetoothLE == null) { ErrorOccurred("BluetoothLE component not set"); return false; }

        List<LegoHub> visible = getVisibleHubs();
        if (index < 1 || index > visible.size()) {
            ErrorOccurred("Invalid hub index: " + index); return false;
        }
        if (isConnected) Disconnect();

        // CLAUDE.md Rule 4: stop scanning before connecting, remember to resume.
        if (isScanning) {
            wasScanningBeforeConnection = true;
            stopScanTimer();
            isScanning = false;
            ScanningStopped();
        } else {
            wasScanningBeforeConnection = false;
        }

        LegoHub hub = visible.get(index - 1);
        logDebug("ConnectToHub → " + hub);
        try {
            bluetoothLE.getClass()
                .getMethod("ConnectWithAddress", String.class)
                .invoke(bluetoothLE, hub.getAddress());
            return true;
        } catch (Exception e) {
            ErrorOccurred("Connect failed: " + e.getMessage());
            if (wasScanningBeforeConnection) {
                isScanning = true; startScanTimer(); ScanningStarted();
            }
            return false;
        }
    }

    /** Disconnect from the currently connected hub. */
    @SimpleFunction(description = "Disconnect from the currently connected hub")
    public void Disconnect() {
        if (!isConnected || bluetoothLE == null) return;
        logDebug("Disconnect → " + connectedDeviceName);
        try {
            bluetoothLE.getClass().getMethod("Disconnect").invoke(bluetoothLE);
        } catch (Exception e) {
            ErrorOccurred("Disconnect failed: " + e.getMessage());
        }
    }

    /**
     * Subscribe to TX characteristic notifications.
     * Called from onConnected() immediately after GATT connection is established.
     * Hub → App data flows through this subscription.
     */
    private void registerForTXNotifications() {
        if (bluetoothLE == null) return;
        logDebug("Subscribing to TX notifications: " + TX_CHAR_UUID);
        try {
            bluetoothLE.getClass()
                .getMethod("RegisterForBytes", String.class, String.class)
                .invoke(bluetoothLE, SPIKE_SERVICE_UUID, TX_CHAR_UUID);
            logDebug("TX notification subscription OK");
        } catch (Exception e) {
            logDebug("RegisterForBytes failed: " + e.getMessage());
            ErrorOccurred("TX subscription failed: " + e.getMessage());
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
                    "WriteBytes", String.class, String.class, boolean.class, YailList.class);

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
    @SimpleFunction(description =
        "Feed bytes from BluetoothLE.BytesReceived into the SPIKE Prime frame buffer. "
        + "Wire BluetoothLE's BytesReceived event to this method.")
    public void OnBytesReceivedFromHub(String serviceUuid,
                                       String characteristicUuid,
                                       YailList byteValues) {
        // Only handle bytes from the TX characteristic
        if (!TX_CHAR_UUID.equalsIgnoreCase(characteristicUuid)) return;

        // Append incoming bytes to the receive buffer
        for (Object item : byteValues.toArray()) {
            try {
                receiveBuffer.add((byte)(Integer.parseInt(item.toString()) & 0xFF));
            } catch (NumberFormatException ignored) { /* skip malformed entry */ }
        }
        processReceiveBuffer();
    }

    /**
     * Also accepts the raw byte-list form that some BLE extension versions fire.
     * Delegates to OnBytesReceivedFromHub after normalising arguments.
     */
    public void BluetoothLE_BytesReceived(String serviceUuid,
                                          String characteristicUuid,
                                          YailList byteValues) {
        OnBytesReceivedFromHub(serviceUuid, characteristicUuid, byteValues);
    }

    /**
     * Scan receiveBuffer for complete SPIKE Prime frames.
     * A frame starts with 0x01 and ends with the first 0x02 seen after that.
     * Bytes appearing before the first 0x01 are discarded (protocol garbage).
     */
    private void processReceiveBuffer() {
        while (true) {
            // CLAUDE.md Rule 3: safe iteration, no index out-of-bounds
            if (receiveBuffer.isEmpty()) break;

            // Skip leading bytes that are not 0x01 (frame start)
            if ((receiveBuffer.get(0) & 0xFF) != 0x01) {
                receiveBuffer.remove(0);
                continue;
            }

            // Search for 0x02 (frame end) starting at index 1
            int endIdx = -1;
            for (int i = 1; i < receiveBuffer.size(); i++) {
                if ((receiveBuffer.get(i) & 0xFF) == 0x02) { endIdx = i; break; }
            }

            if (endIdx == -1) break; // Incomplete frame — wait for more bytes

            // Extract the complete frame [0 .. endIdx]
            byte[] frame = new byte[endIdx + 1];
            for (int i = 0; i <= endIdx; i++) frame[i] = receiveBuffer.get(i);
            // Remove consumed bytes
            for (int i = 0; i <= endIdx; i++) receiveBuffer.remove(0);

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
            } else if (msgId == 0x0D || msgId == 0x11 || msgId == 0x1F) {
                handleStatusResponse(raw);
            }
            // Future: TunnelMessage (0x32), DeviceNotification (0x3C), etc.
        } catch (Exception e) {
            logDebug("handleCompleteFrame error: " + e);
        }
    }

    /**
     * Parse an InfoResponse and update maxPacketSize / maxChunkSize.
     * Fires {@link #InfoResponseReceived} on the main thread.
     */
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

        final int fm = info.fwMajor, fn = info.fwMinor, fb = info.fwBuild;
        final int mcs = maxChunkSize, mps = maxPacketSize;
        mainHandler.post(() -> InfoResponseReceived(fm, fn, fb, mcs, mps));
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
            ErrorOccurred(name + " not acknowledged by hub");
        }
    }

    private static String statusResponseName(int msgId) {
        switch (msgId) {
            case 0x0D: return "StartFileUploadResponse";
            case 0x11: return "TransferChunkResponse";
            case 0x1F: return "ProgramFlowResponse";
            default:   return "StatusResponse(0x" + String.format("%02X", msgId) + ")";
        }
    }

    // =========================================================================
    // BLE event callbacks (called by App Inventor event dispatch or by user blocks)
    // =========================================================================

    /** BluetoothLE DeviceFound callback — update hub list, fire HubFound. */
    public void BluetoothLE_DeviceFound(String name, String address, int rssi) {
        // CLAUDE.md Rule 3: null-check address before any list access
        if (address == null) return;
        logDebug("DeviceFound: " + name + " (" + address + ") rssi=" + rssi);
        if (!isLegoSpikeHub(name)) return;

        boolean found = false;
        for (LegoHub h : legoHubs) {
            if (address.equals(h.getAddress())) { h.updateLastSeen(); found = true; break; }
        }
        if (!found) {
            LegoHub hub = new LegoHub(name != null ? name : "SPIKE Hub", address, -1);
            legoHubs.add(hub);
            final String n = hub.getName(), a = address;
            mainHandler.post(() -> HubFound(n, a));
        }
    }

    public void BluetoothLE_ScanningStateChanged(boolean scanning) {
        isScanning = scanning;
        if (scanning) ScanningStarted(); else ScanningStopped();
    }

    /** BluetoothLE Connected callback — sets state and subscribes to TX. */
    public void BluetoothLE_Connected(String address) {
        // CLAUDE.md Rule 3: null-check address
        if (address == null) { logDebug("BluetoothLE_Connected: null address, ignoring"); return; }
        logDebug("BluetoothLE_Connected: " + address);

        String name = "SPIKE Hub";
        for (LegoHub h : legoHubs) {
            if (address.equals(h.getAddress())) { name = h.getName(); break; }
        }
        onConnected(name, address);
    }

    public void BluetoothLE_Disconnected() {
        logDebug("BluetoothLE_Disconnected");
        onDisconnected();
    }

    // =========================================================================
    // Connection state transitions
    // =========================================================================

    /** Called after GATT connection is established. Subscribes to TX characteristic. */
    public void onConnected(String deviceName, String deviceAddress) {
        isConnected           = true;
        connectedDeviceName   = deviceName;
        connectedDeviceAddress = deviceAddress;
        receiveBuffer.clear(); // discard stale bytes from any previous session

        logDebug("onConnected: " + deviceName + " (" + deviceAddress + ")");

        // Subscribe to hub's TX characteristic so we receive notifications
        registerForTXNotifications();

        // InfoRequest handshake — must be the first message sent after subscribing to TX
        // so the hub's InfoResponse arrives and populates maxPacketSize / maxChunkSize.
        logDebug("Sending InfoRequest");
        sendFramedMessage(MessageFramer.pack(MessageBuilder.buildInfoRequest()));

        // Fire events on main thread
        final String n = deviceName, a = deviceAddress;
        mainHandler.post(() -> {
            Connected(n, a);
            HubConnected(n, a); // legacy alias kept for existing block users
        });
    }

    /** Called after GATT connection is lost. */
    public void onDisconnected() {
        isConnected = false;
        final String n = connectedDeviceName, a = connectedDeviceAddress;
        connectedDeviceName    = "";
        connectedDeviceAddress = "";
        receiveBuffer.clear();
        logDebug("onDisconnected: " + n);

        // CLAUDE.md Rule 4: resume scanning if we were scanning before connecting
        if (wasScanningBeforeConnection) {
            wasScanningBeforeConnection = false;
            isScanning = true;
            startScanTimer();
            ScanningStarted();
        }

        mainHandler.post(() -> {
            Disconnected();
            HubDisconnected(); // legacy alias
        });
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
     * @param deviceAddress hub BLE address
     */
    @SimpleEvent(description = "Fired when the app connects to a SPIKE Prime hub")
    public void Connected(String deviceName, String deviceAddress) {
        logDebug("Connected: " + deviceName);
        EventDispatcher.dispatchEvent(this, "Connected", deviceName, deviceAddress);
    }

    /** Fired when the connection to the hub is lost. */
    @SimpleEvent(description = "Fired when the connection to the hub is lost")
    public void Disconnected() {
        logDebug("Disconnected");
        EventDispatcher.dispatchEvent(this, "Disconnected");
    }

    /** Legacy alias for Connected — kept so existing block users are not broken. */
    @SimpleEvent(description = "Fired when the app connects to a hub (legacy name)")
    public void HubConnected(String deviceName, String deviceAddress) {
        EventDispatcher.dispatchEvent(this, "HubConnected", deviceName, deviceAddress);
    }

    /** Legacy alias for Disconnected — kept so existing block users are not broken. */
    @SimpleEvent(description = "Fired when the connection is lost (legacy name)")
    public void HubDisconnected() {
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
     * Fired when the hub replies to the InfoRequest handshake with its capabilities.
     * maxPacketSize and maxChunkSize are now stored and used by the extension automatically.
     *
     * @param fwMajor       firmware major version number
     * @param fwMinor       firmware minor version number
     * @param fwBuild       firmware build number
     * @param maxChunkSize  maximum bytes per TransferChunkRequest payload
     * @param maxPacketSize maximum bytes per BLE write-without-response packet
     */
    @SimpleEvent(description =
        "Fired after connecting when the hub returns its firmware version and BLE capabilities")
    public void InfoResponseReceived(int fwMajor, int fwMinor, int fwBuild,
                                     int maxChunkSize, int maxPacketSize) {
        logDebug("InfoResponseReceived: FW=" + fwMajor + "." + fwMinor + "." + fwBuild
            + " maxChunk=" + maxChunkSize + " maxPacket=" + maxPacketSize);
        EventDispatcher.dispatchEvent(this, "InfoResponseReceived",
            fwMajor, fwMinor, fwBuild, maxChunkSize, maxPacketSize);
    }

    /** Maximum chunk size received from the hub's InfoResponse (default 445). */
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Max program chunk size reported by InfoResponse (default 445)")
    public int MaxChunkSize() { return maxChunkSize; }

    /** Fired when the hub controller program has been fully uploaded and started. */
    @SimpleEvent(description =
        "Fired when UploadController finishes uploading and starting the hub program")
    public void ControllerUploaded() {
        logDebug("ControllerUploaded");
        EventDispatcher.dispatchEvent(this, "ControllerUploaded");
    }

    // =========================================================================
    // Program upload and motor control
    // =========================================================================

    /**
     * Upload the embedded hub_controller.py to slot 0 and start it.
     * Runs on a background thread; fires {@link #ControllerUploaded()} when done.
     * Uses the maxChunkSize from InfoResponse (or the default 445).
     */
    @SimpleFunction(description =
        "Upload the hub controller program and start it (runs in background)")
    public void UploadController() {
        if (!isConnected) { ErrorOccurred("Not connected"); return; }
        new Thread(() -> {
            try {
                ProgramUploader up = new ProgramUploader(
                    "program.py", CONTROLLER_SLOT,
                    HUB_CONTROLLER_PROGRAM, maxChunkSize);

                logDebug("UploadController: sending StartFileUpload");
                sendFramedMessage(up.getStartUploadMessage());
                Thread.sleep(CHUNK_DELAY_MS);

                List<byte[]> chunks = up.getChunkMessages();
                logDebug("UploadController: " + chunks.size() + " chunk(s) to send");
                for (int i = 0; i < chunks.size(); i++) {
                    sendFramedMessage(chunks.get(i));
                    logDebug("UploadController: chunk " + (i + 1) + "/" + chunks.size() + " sent");
                    Thread.sleep(CHUNK_DELAY_MS);
                }

                logDebug("UploadController: sending Execute");
                sendFramedMessage(up.getExecuteMessage());

                mainHandler.post(() -> ControllerUploaded());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logDebug("UploadController interrupted");
            } catch (Exception e) {
                logDebug("UploadController error: " + e);
                mainHandler.post(() -> ErrorOccurred("Upload failed: " + e.getMessage()));
            }
        }, "LegoSpikeUpload").start();
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

    // =========================================================================
    // Motor / LED — legacy direct-BLE commands.
    // NOTE: These do NOT work with SPIKE Prime 3.x firmware (CLAUDE.md Rule 5).
    // They are retained here only for backward compatibility.
    // Future work: replace with TunnelMessage-based control.
    // =========================================================================

    @SimpleFunction(description = "Set hub LED colour — NOT yet functional on SPIKE Prime 3.x")
    public boolean SetHubLEDColor(int red, int green, int blue) {
        if (!isConnected) { ErrorOccurred("Not connected"); return false; }
        return bluetoothInterface.sendMessage(buildSetLEDCommand(red, green, blue));
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
