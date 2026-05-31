package io.github.appinventor.legospikeprime;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.Options;
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

/**
 * LegoSpikeSensors — sensor reads and hub state queries.
 * Matches LEGO SPIKE Prime "Sensor Blocks" + "More Sensors" categories.
 *
 * Configure Port (for port-based sensors) and Axis (for hub tilt) in the
 * Designer or via blocks, then call the sensor read functions.
 * Each call requests a value from the hub; the result fires as an event.
 * Use one LegoSpikeSensors instance per physical sensor.
 */
@SimpleObject(external = true)
@DesignerComponent(version = 4,
    description = "Reads sensors on a LEGO SPIKE Prime hub. "
        + "Set ColorSensorPort, DistanceSensorPort, and PressureSensorPort independently "
        + "so different sensors can be read in parallel. Set Axis for hub tilt. "
        + "Set the Connectivity property to a LegoSpikeConnectivity component.",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = "aiwebres/legospike.png")
public class LegoSpikeSensors extends AndroidNonvisibleComponent
        implements LegoSpikeConnectivity.HubDataListener {

    private LegoSpikeConnectivity connectivity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String colorSensorPort    = "C";
    private String distanceSensorPort = "D";
    private String pressureSensorPort = "E";
    private long   timerStartMs       = System.currentTimeMillis();

    // Accumulator for GetHubOrientation — assembles pitch+roll+yaw before firing HubOrientationRead.
    private volatile boolean orientationReadPending = false;
    private volatile Integer orientPitch = null;
    private volatile Integer orientRoll  = null;
    private volatile Integer orientYaw   = null;

    public LegoSpikeSensors(ComponentContainer container) {
        super(container.$form());
    }

    // =========================================================================
    // Connectivity property
    // =========================================================================
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "The LegoSpikeConnectivity component managing the hub connection")
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COMPONENT
        + ":io.github.appinventor.legospikeprime.LegoSpikeConnectivity")
    public void Connectivity(Component component) {
        if (this.connectivity != null) {
            this.connectivity.removeDataListener(this);
        }
        if (component instanceof LegoSpikeConnectivity) {
            this.connectivity = (LegoSpikeConnectivity) component;
            this.connectivity.addDataListener(this);
        }
    }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "The LegoSpikeConnectivity component managing the hub connection")
    public Component Connectivity() { return connectivity; }

    // =========================================================================
    // Port properties — one per sensor type so different sensors can sit on
    // different ports and be read in parallel without switching state.
    // =========================================================================

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Port (A–F) where the color sensor is connected")
    @DesignerProperty(
        editorType   = PropertyTypeConstants.PROPERTY_TYPE_CHOICES,
        editorArgs   = {"A", "B", "C", "D", "E", "F"},
        defaultValue = "C")
    public void ColorSensorPort(@Options(Port.class) String value) {
        if (value != null && value.toUpperCase().trim().matches("[A-F]"))
            colorSensorPort = value.toUpperCase().trim();
    }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Port (A–F) where the color sensor is connected")
    public String ColorSensorPort() { return colorSensorPort; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Port (A–F) where the distance sensor is connected")
    @DesignerProperty(
        editorType   = PropertyTypeConstants.PROPERTY_TYPE_CHOICES,
        editorArgs   = {"A", "B", "C", "D", "E", "F"},
        defaultValue = "D")
    public void DistanceSensorPort(@Options(Port.class) String value) {
        if (value != null && value.toUpperCase().trim().matches("[A-F]"))
            distanceSensorPort = value.toUpperCase().trim();
    }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Port (A–F) where the distance sensor is connected")
    public String DistanceSensorPort() { return distanceSensorPort; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Port (A–F) where the force sensor is connected (used by GetPressure and IsPressed)")
    @DesignerProperty(
        editorType   = PropertyTypeConstants.PROPERTY_TYPE_CHOICES,
        editorArgs   = {"A", "B", "C", "D", "E", "F"},
        defaultValue = "E")
    public void PressureSensorPort(@Options(Port.class) String value) {
        if (value != null && value.toUpperCase().trim().matches("[A-F]"))
            pressureSensorPort = value.toUpperCase().trim();
    }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Port (A–F) where the force sensor is connected (used by GetPressure and IsPressed)")
    public String PressureSensorPort() { return pressureSensorPort; }

    // =========================================================================
    // Sensor read functions
    // =========================================================================

    /**
     * Request the color detected by the color sensor on the configured Port.
     * Fires ColorRead(port, color) when the hub responds.
     */
    @SimpleFunction(description =
        "Request the color from the color sensor on the configured Port. "
        + "Fires ColorRead when the hub responds.")
    public void GetColor() {
        sendSensorSSP(new SSPMessage("sensor.read")
            .withPort(colorSensorPort).withParam("type", "color"));
    }

    /**
     * Request the distance measured by the distance sensor on the configured Port.
     * Fires DistanceRead(port, mm) when the hub responds. Returns -1 if out of range.
     */
    @SimpleFunction(description =
        "Request the distance (mm) from the distance sensor on the configured Port. "
        + "Fires DistanceRead when the hub responds. Returns -1 if out of range.")
    public void GetDistance() {
        sendSensorSSP(new SSPMessage("sensor.read")
            .withPort(distanceSensorPort).withParam("type", "distance"));
    }

    /**
     * Request the force measured by the force sensor on the configured Port.
     * Fires PressureRead(port, value) when the hub responds.
     */
    @SimpleFunction(description =
        "Request the force value from the force sensor on the configured Port. "
        + "Fires PressureRead when the hub responds.")
    public void GetPressure() {
        sendSensorSSP(new SSPMessage("sensor.read")
            .withPort(pressureSensorPort).withParam("type", "force"));
    }

    /**
     * Request whether the force sensor on the configured Port is pressed.
     * Fires PressureChecked(port, isPressed) when the hub responds.
     */
    @SimpleFunction(description =
        "Ask whether the force sensor on the configured Port is pressed. "
        + "Fires PressureChecked when the hub responds.")
    public void IsPressed() {
        sendSensorSSP(new SSPMessage("sensor.read")
            .withPort(pressureSensorPort).withParam("type", "touched"));
    }

    /**
     * Request the hub tilt angle for the configured Axis.
     * Fires HubTiltAngleRead(axis, degrees) when the hub responds.
     */
    @SimpleFunction(description =
        "Request the hub tilt angle for the given axis. Fires HubTiltAngleRead when the hub responds.")
    public void GetHubTiltAngle(@Options(TiltAxis.class) String axis) {
        TiltAxis a = TiltAxis.fromUnderlyingValue(axis);
        String name = a != null ? a.toUnderlyingValue().toLowerCase() : "pitch";
        sendSensorSSP(new SSPMessage("sensor.read")
            .withPort("imu").withParam("type", name));
    }

    /**
     * Request the elapsed time since the last ResetTimer call.
     * Fires TimerRead(seconds) when the hub responds.
     */
    @SimpleFunction(description =
        "Request the elapsed time (seconds) since the last ResetTimer. "
        + "Fires TimerRead when the hub responds.")
    public void GetTimer() {
        // Timer is now client-side; fire event directly
        long elapsed = (System.currentTimeMillis() - timerStartMs) / 1000;
        final long e = elapsed;
        mainHandler.post(() -> TimerRead((int) e));
    }

    /** Reset the hub timer to zero. */
    @SimpleFunction(description = "Reset the hub timer to zero")
    public void ResetTimer() {
        timerStartMs = System.currentTimeMillis();
    }

    // =========================================================================
    // Events
    // =========================================================================

    @SimpleEvent(description =
        "Fired when the hub reports a color reading. "
        + "color: title-case name (e.g. Red, Green, None). "
        + "Use a SensorColor option block from the ColorConstant function for comparison.")
    public void ColorRead(String port, String color) {
        EventDispatcher.dispatchEvent(this, "ColorRead", port, color);
    }

    @SimpleEvent(description =
        "Fired when the hub reports a distance reading in millimetres. "
        + "Returns -1 if out of range or no sensor connected.")
    public void DistanceRead(String port, int mm) {
        EventDispatcher.dispatchEvent(this, "DistanceRead", port, mm);
    }

    @SimpleEvent(description =
        "Fired when the hub reports a force/pressure sensor reading (0–100).")
    public void PressureRead(String port, int value) {
        EventDispatcher.dispatchEvent(this, "PressureRead", port, value);
    }

    @SimpleEvent(description =
        "Fired when the hub reports whether the force sensor is pressed.")
    public void PressureChecked(String port, boolean isPressed) {
        EventDispatcher.dispatchEvent(this, "PressureChecked", port, isPressed);
    }

    @SimpleEvent(description =
        "Fired when the hub reports a tilt angle. "
        + "axis: Pitch, Roll, or Yaw. degrees: angle in degrees.")
    public void HubTiltAngleRead(String axis, int degrees) {
        EventDispatcher.dispatchEvent(this, "HubTiltAngleRead", axis, degrees);
    }

    @SimpleEvent(description =
        "Fired when GetHubOrientation responds with all three axes at once. "
        + "pitch, roll, yaw: angles in degrees.")
    public void HubOrientationRead(int pitch, int roll, int yaw) {
        EventDispatcher.dispatchEvent(this, "HubOrientationRead", pitch, roll, yaw);
    }

    @SimpleEvent(description =
        "Fired when the hub reports the elapsed timer value in whole seconds.")
    public void TimerRead(int seconds) {
        EventDispatcher.dispatchEvent(this, "TimerRead", seconds);
    }

    // =========================================================================
    // HubDataListener — parse SSP v0.6 JSON sensor responses
    // =========================================================================
    @Override
    public void onHubData(String data) {
        if (data == null || !data.startsWith("{")) return;
        try {
            JSONObject obj = new JSONObject(data);
            String event = obj.optString("event");

            // Button events come as system events
            if ("system".equals(event)) {
                final String metric = obj.optString("metric");
                if (metric.startsWith("button.")) {
                    final String btnName = metric.substring("button.".length());
                    final String state   = obj.optString("value");
                    mainHandler.post(() -> {
                        if ("pressed".equals(state)) {
                            WhenHubButtonPressed(btnName);
                        } else if ("released".equals(state)) {
                            WhenHubButtonReleased(btnName);
                        }
                    });
                }
                return;
            }

            if (!"sensor".equals(event)) return;

            final String port = obj.optString("port");
            final String type = obj.optString("type");
            final Object val  = obj.opt("value");

            switch (type) {
                case "color": {
                    // "red" → "Red" (title-case matches SensorColor option block values)
                    String raw = val != null ? val.toString() : "";
                    final String colorTitle = raw.isEmpty() ? raw
                        : raw.substring(0, 1).toUpperCase() + raw.substring(1).toLowerCase();
                    mainHandler.post(() -> ColorRead(port, colorTitle));
                    break;
                }
                case "distance": {
                    try {
                        final int mm = val instanceof Number
                            ? ((Number) val).intValue()
                            : Integer.parseInt(val.toString());
                        mainHandler.post(() -> DistanceRead(port, mm));
                    } catch (Exception ignored) {}
                    break;
                }
                case "force": {
                    try {
                        final int value = val instanceof Number
                            ? ((Number) val).intValue()
                            : Integer.parseInt(val.toString());
                        mainHandler.post(() -> PressureRead(port, value));
                    } catch (Exception ignored) {}
                    break;
                }
                case "touched": {
                    final boolean pressed = val instanceof Boolean
                        ? (Boolean) val
                        : "true".equalsIgnoreCase(val != null ? val.toString() : "");
                    mainHandler.post(() -> PressureChecked(port, pressed));
                    break;
                }
                case "pitch":
                case "roll":
                case "yaw": {
                    final String titleAx = type.substring(0, 1).toUpperCase() + type.substring(1);
                    try {
                        final int degrees = val instanceof Number
                            ? ((Number) val).intValue()
                            : Integer.parseInt(val.toString());
                        if (orientationReadPending) {
                            if (type.equals("pitch"))      orientPitch = degrees;
                            else if (type.equals("roll"))  orientRoll  = degrees;
                            else                           orientYaw   = degrees;
                            if (orientPitch != null && orientRoll != null && orientYaw != null) {
                                orientationReadPending = false;
                                final int p = orientPitch, r = orientRoll, y = orientYaw;
                                orientPitch = null; orientRoll = null; orientYaw = null;
                                mainHandler.post(() -> HubOrientationRead(p, r, y));
                            }
                        } else {
                            mainHandler.post(() -> HubTiltAngleRead(titleAx, degrees));
                        }
                    } catch (Exception ignored) {}
                    break;
                }
                case "face_orientation": {
                    final String fo = val != null ? val.toString() : "";
                    mainHandler.post(() -> {
                        HubFaceOrientationRead(fo);
                        HubFaceOrientationChanged(fo);
                    });
                    break;
                }
                case "gesture": {
                    final String g = val != null ? val.toString() : "";
                    mainHandler.post(() -> HubGestureDetected(g));
                    break;
                }
                case "acceleration": {
                    try {
                        org.json.JSONObject acc = (org.json.JSONObject) val;
                        final double ax = acc.optDouble("x", 0);
                        final double ay = acc.optDouble("y", 0);
                        final double az = acc.optDouble("z", 0);
                        mainHandler.post(() -> HubAccelerationRead(ax, ay, az));
                    } catch (Exception ignored) {}
                    break;
                }
                case "angular_velocity": {
                    try {
                        org.json.JSONObject av = (org.json.JSONObject) val;
                        final double vx = av.optDouble("x", 0);
                        final double vy = av.optDouble("y", 0);
                        final double vz = av.optDouble("z", 0);
                        mainHandler.post(() -> HubAngularVelocityRead(vx, vy, vz));
                    } catch (Exception ignored) {}
                    break;
                }
                case "rgb": {
                    try {
                        org.json.JSONArray rgb = (org.json.JSONArray) val;
                        final int r = rgb.optInt(0, 0);
                        final int g = rgb.optInt(1, 0);
                        final int b = rgb.optInt(2, 0);
                        mainHandler.post(() -> ColorRGBRead(port, r, g, b));
                    } catch (Exception ignored) {}
                    break;
                }
                case "reflected": {
                    try {
                        final int v = val instanceof Number
                            ? ((Number) val).intValue()
                            : Integer.parseInt(val.toString());
                        mainHandler.post(() -> ReflectedLightRead(port, v));
                    } catch (Exception ignored) {}
                    break;
                }
                default:
                    break;
            }
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // Phase 3 IMU blocks (§3.4)
    // =========================================================================

    @SimpleFunction(description =
        "Request hub acceleration on all axes. Fires HubAccelerationRead when the hub responds.")
    public void GetHubAcceleration() {
        sendSensorSSP(new SSPMessage("sensor.read")
            .withPort("imu").withParam("type", "acceleration"));
    }

    @SimpleFunction(description =
        "Request hub angular velocity on all axes. Fires HubAngularVelocityRead when received.")
    public void GetHubAngularVelocity() {
        sendSensorSSP(new SSPMessage("sensor.read")
            .withPort("imu").withParam("type", "angular_velocity"));
    }

    @SimpleFunction(description =
        "Request all three tilt angles at once. Fires HubOrientationRead(pitch, roll, yaw) "
        + "once all three responses arrive.")
    public void GetHubOrientation() {
        orientPitch = null; orientRoll = null; orientYaw = null;
        orientationReadPending = true;
        sendSensorSSP(new SSPMessage("sensor.read")
            .withPort("imu").withParam("type", "pitch"));
        sendSensorSSP(new SSPMessage("sensor.read")
            .withPort("imu").withParam("type", "roll"));
        sendSensorSSP(new SSPMessage("sensor.read")
            .withPort("imu").withParam("type", "yaw"));
    }

    @SimpleFunction(description =
        "Request which face of the hub is currently up. Fires HubFaceOrientationRead when received.")
    public void GetHubFaceOrientation() {
        sendSensorSSP(new SSPMessage("sensor.read")
            .withPort("imu").withParam("type", "face_orientation"));
    }

    @SimpleFunction(description = "Reset the hub yaw angle to zero.")
    public void ResetHubYaw() {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("orientation.reset_yaw"));
    }

    @SimpleFunction(description = "Set the hub yaw to a specific angle in degrees.")
    public void SetHubYaw(int degrees) {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("orientation.set_yaw").withParam("angle", degrees));
    }

    @SimpleFunction(description =
        "Configure which face of the hub is 'up' for orientation readings.")
    public void SetHubOrientation(@Options(HubFace.class) String face) {
        if (!checkConnected()) return;
        HubFace f = HubFace.fromUnderlyingValue(face);
        String name = f != null ? f.toUnderlyingValue() : "face_up";
        connectivity.sendSSP(new SSPMessage("orientation.set_reference").withParam("face", name));
    }

    @SimpleFunction(description =
        "Subscribe to left hub button events. WhenHubButtonPressed/WhenHubButtonReleased fire on change.")
    public void SubscribeToLeftButton() {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("system.subscribe")
            .withParam("metric", "button.left").withParam("interval", 100));
    }

    @SimpleFunction(description =
        "Subscribe to right hub button events. WhenHubButtonPressed/WhenHubButtonReleased fire on change.")
    public void SubscribeToRightButton() {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("system.subscribe")
            .withParam("metric", "button.right").withParam("interval", 100));
    }

    @SimpleFunction(description =
        "Subscribe to center hub button events. WhenHubButtonPressed/WhenHubButtonReleased fire on change.")
    public void SubscribeToCenterButton() {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("system.subscribe")
            .withParam("metric", "button.center").withParam("interval", 100));
    }

    @SimpleFunction(description =
        "Subscribe to gesture events. HubGestureDetected fires on shake, tap, double_tap, fall, face_up, or face_down.")
    public void SubscribeToGestures() {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("sensor.subscribe")
            .withPort("imu")
            .withParam("type", "gesture")
            .withParam("mode", "on_change"));
    }

    @SimpleFunction(description =
        "Subscribe to face orientation changes. HubFaceOrientationChanged fires when the hub flips.")
    public void SubscribeToFaceOrientation() {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("sensor.subscribe")
            .withPort("imu")
            .withParam("type", "face_orientation")
            .withParam("mode", "on_change"));
    }

    @SimpleFunction(description =
        "Request RGB values from the color sensor. Fires ColorRGBRead when received.")
    public void GetColorRGB() {
        sendSensorSSP(new SSPMessage("sensor.read")
            .withPort(colorSensorPort).withParam("type", "rgb"));
    }

    @SimpleFunction(description =
        "Request reflected light intensity (0–100) from the color sensor. "
        + "Fires ReflectedLightRead when received.")
    public void GetReflectedLight() {
        sendSensorSSP(new SSPMessage("sensor.read")
            .withPort(colorSensorPort).withParam("type", "reflected"));
    }

    // =========================================================================
    // Phase 3 new events
    // =========================================================================

    @SimpleEvent(description = "Fired when GetHubAcceleration responds. Values in m/s².")
    public void HubAccelerationRead(double x, double y, double z) {
        EventDispatcher.dispatchEvent(this, "HubAccelerationRead", x, y, z);
    }

    @SimpleEvent(description = "Fired when GetHubAngularVelocity responds. Values in deg/s.")
    public void HubAngularVelocityRead(double x, double y, double z) {
        EventDispatcher.dispatchEvent(this, "HubAngularVelocityRead", x, y, z);
    }

    @SimpleEvent(description =
        "Fired when GetHubFaceOrientation responds. "
        + "orientation: face_up, face_down, port_a_up, port_a_down, port_e_up, port_e_down.")
    public void HubFaceOrientationRead(String orientation) {
        EventDispatcher.dispatchEvent(this, "HubFaceOrientationRead", orientation);
    }

    @SimpleEvent(description =
        "Fired when the hub detects a gesture (after SubscribeToGestures). "
        + "gesture: shake, tap, double_tap, fall, face_up, face_down.")
    public void HubGestureDetected(String gesture) {
        EventDispatcher.dispatchEvent(this, "HubGestureDetected", gesture);
    }

    @SimpleEvent(description =
        "Fired when the hub detects a face-orientation change (after SubscribeToFaceOrientation).")
    public void HubFaceOrientationChanged(String orientation) {
        EventDispatcher.dispatchEvent(this, "HubFaceOrientationChanged", orientation);
    }

    @SimpleEvent(description =
        "Fired when GetColorRGB responds. r, g, b: 0–255.")
    public void ColorRGBRead(String port, int r, int g, int b) {
        EventDispatcher.dispatchEvent(this, "ColorRGBRead", port, r, g, b);
    }

    @SimpleEvent(description =
        "Fired when GetReflectedLight responds. value: 0–100 percent.")
    public void ReflectedLightRead(String port, int value) {
        EventDispatcher.dispatchEvent(this, "ReflectedLightRead", port, value);
    }

    @SimpleEvent(description =
        "Fired when a hub button is pressed. button: 'left', 'right', or 'center'.")
    public void WhenHubButtonPressed(String button) {
        EventDispatcher.dispatchEvent(this, "WhenHubButtonPressed", button);
    }

    @SimpleEvent(description =
        "Fired when a hub button is released. button: 'left', 'right', or 'center'.")
    public void WhenHubButtonReleased(String button) {
        EventDispatcher.dispatchEvent(this, "WhenHubButtonReleased", button);
    }

    // =========================================================================
    // ColorConstant — exposes SensorColor option blocks for ColorRead comparisons
    // =========================================================================

    @SimpleFunction(description =
        "Returns a SensorColor constant for comparing with the color parameter "
        + "in ColorRead events. Use the dropdown to pick a color without typing.")
    public String ColorConstant(@Options(SensorColor.class) String color) {
        return color;
    }

    @SimpleFunction(description =
        "Light up the 4 indicator LEDs on a distance sensor accessory. "
        + "Each value is brightness 0–100. port: the sensor port (A–F).")
    public void LightUpDistanceSensor(@Options(Port.class) String port,
                                      int topLeft, int topRight,
                                      int bottomLeft, int bottomRight) {
        if (!checkConnected()) return;
        String dp = port.toUpperCase() + "_display";
        int[][] pixels = {{topLeft, topRight}, {bottomLeft, bottomRight}};
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                connectivity.sendSSP(new SSPMessage("led.matrix.pixel")
                    .withPort(dp)
                    .withParam("x", x)
                    .withParam("y", y)
                    .withParam("brightness", Math.max(0, Math.min(100, pixels[y][x]))));
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================
    private void sendSensorSSP(SSPMessage msg) {
        if (!checkConnected()) return;
        connectivity.sendSSP(msg);
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
