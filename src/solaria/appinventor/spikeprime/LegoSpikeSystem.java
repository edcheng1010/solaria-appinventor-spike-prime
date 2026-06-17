package solaria.appinventor.spikeprime;

import android.os.Handler;
import android.os.Looper;

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

import org.json.JSONObject;

@SimpleObject(external = true)
@DesignerComponent(version = 1,
    description = "Reads hub-internal metrics (battery, temperature, buttons) on a LEGO SPIKE Prime hub. "
        + "Set the Connectivity property to a LegoSpikeConnectivity component.",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = "aiwebres/legospike.png")
public class LegoSpikeSystem extends AndroidNonvisibleComponent
        implements LegoSpikeConnectivity.HubDataListener {

    private LegoSpikeConnectivity connectivity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public LegoSpikeSystem(ComponentContainer container) {
        super(container.$form());
    }

    // =========================================================================
    // Connectivity
    // =========================================================================
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "The LegoSpikeConnectivity component managing the hub connection")
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COMPONENT
        + ":solaria.appinventor.spikeprime.LegoSpikeConnectivity")
    public void Connectivity(Component component) {
        if (this.connectivity != null) this.connectivity.removeDataListener(this);
        if (component instanceof LegoSpikeConnectivity) {
            this.connectivity = (LegoSpikeConnectivity) component;
            this.connectivity.addDataListener(this);
        }
    }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "The LegoSpikeConnectivity component managing the hub connection")
    public Component Connectivity() { return connectivity; }

    // =========================================================================
    // System metric blocks
    // =========================================================================

    @SimpleFunction(description =
        "Request the hub battery level (0–100%). Fires BatteryLevelRead when received.")
    public void GetBatteryLevel() {
        sendSystemRead("battery");
    }

    @SimpleFunction(description =
        "Request the hub internal temperature (°C). Fires TemperatureRead when received.")
    public void GetTemperature() {
        sendSystemRead("temperature");
    }

    @SimpleFunction(description =
        "Request whether the hub is currently charging. Fires ChargingStateRead when received.")
    public void IsCharging() {
        sendSystemRead("charging");
    }

    @SimpleFunction(description =
        "Request the live BLE connection signal strength (dBm). Fires RSSIRead when received.")
    public void GetRSSI() {
        if (!checkConnected()) return;
        final Component ble = connectivity.getBluetoothLE();
        if (ble == null) return;
        new Thread(() -> {
            try {
                // Trigger a fresh radio read, then sample the updated cache.
                ble.getClass().getMethod("ReadConnectedRssi").invoke(ble);
                Thread.sleep(250);
                int rssi = (Integer) ble.getClass()
                    .getMethod("ConnectedDeviceRssi").invoke(ble);
                mainHandler.post(() -> RSSIRead(rssi));
            } catch (Exception e) {
                reportError("RSSI unavailable: " + e.getMessage());
            }
        }, "GetRSSI").start();
    }

    // =========================================================================
    // Events
    // =========================================================================

    @SimpleEvent(description = "Fired when hub battery level is received. percent: 0–100.")
    public void BatteryLevelRead(int percent) {
        EventDispatcher.dispatchEvent(this, "BatteryLevelRead", percent);
    }

    @SimpleEvent(description = "Fired when hub temperature is received. celsius: degrees C.")
    public void TemperatureRead(double celsius) {
        EventDispatcher.dispatchEvent(this, "TemperatureRead", celsius);
    }

    @SimpleEvent(description = "Fired when charging state is received.")
    public void ChargingStateRead(boolean charging) {
        EventDispatcher.dispatchEvent(this, "ChargingStateRead", charging);
    }

    @SimpleEvent(description = "Fired when BLE RSSI is received. rssi: dBm (negative number).")
    public void RSSIRead(int rssi) {
        EventDispatcher.dispatchEvent(this, "RSSIRead", rssi);
    }

    // =========================================================================
    // HubDataListener — parse system events
    // =========================================================================
    @Override
    public void onHubData(String data) {
        if (data == null || !data.startsWith("{")) return;
        try {
            JSONObject obj = new JSONObject(data);
            if (!"system".equals(obj.optString("event"))) return;

            final String metric = obj.optString("metric");
            final Object val    = obj.opt("value");
            if (val == null) return;

            switch (metric) {
                case "battery": {
                    final int pct = ((Number) val).intValue();
                    mainHandler.post(() -> BatteryLevelRead(pct));
                    break;
                }
                case "temperature": {
                    final double c = ((Number) val).doubleValue();
                    mainHandler.post(() -> TemperatureRead(c));
                    break;
                }
                case "charging": {
                    final boolean ch = val instanceof Boolean ? (Boolean) val
                        : "true".equalsIgnoreCase(val.toString());
                    mainHandler.post(() -> ChargingStateRead(ch));
                    break;
                }
                case "connection_rssi": {
                    final int rssi = ((Number) val).intValue();
                    mainHandler.post(() -> RSSIRead(rssi));
                    break;
                }
                default:
                    break;
            }
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // Helpers
    // =========================================================================
    private void sendSystemRead(String metric) {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("system.read")
            .withParam("metric", metric));
    }

    private boolean checkConnected() {
        if (connectivity == null) { reportError("Connectivity not set"); return false; }
        if (!connectivity.IsConnected()) { reportError("Not connected to hub"); return false; }
        return true;
    }

    private void reportError(String msg) {
        if (connectivity != null) connectivity.ErrorOccurred(msg);
    }
}
