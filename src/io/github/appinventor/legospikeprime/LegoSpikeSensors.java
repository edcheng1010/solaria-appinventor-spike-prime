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
        + "Set ColorSensorPort, DistanceSensorPort, and ForceSensorPort independently "
        + "so different sensors can be read in parallel. Set Axis for hub tilt. "
        + "Set the Connectivity property to a LegoSpikeConnectivity component.",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = "aiwebres/legospike.png")
public class LegoSpikeSensors extends AndroidNonvisibleComponent
        implements LegoSpikeConnectivity.HubDataListener {

    private LegoSpikeConnectivity connectivity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final java.util.concurrent.atomic.AtomicInteger readSeq = new java.util.concurrent.atomic.AtomicInteger(0);

    private String colorSensorPort    = "C";
    private String distanceSensorPort = "D";
    private String forceSensorPort = "E";

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
        description = "Port (A–F) where the force sensor is connected (used by GetForce and IsForceSensorPressed)")
    @DesignerProperty(
        editorType   = PropertyTypeConstants.PROPERTY_TYPE_CHOICES,
        editorArgs   = {"A", "B", "C", "D", "E", "F"},
        defaultValue = "E")
    public void ForceSensorPort(@Options(Port.class) String value) {
        if (value != null && value.toUpperCase().trim().matches("[A-F]"))
            forceSensorPort = value.toUpperCase().trim();
    }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Port (A–F) where the force sensor is connected (used by GetForce and IsForceSensorPressed)")
    public String ForceSensorPort() { return forceSensorPort; }

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
     * Fires ForceRead(port, value) when the hub responds.
     */
    @SimpleFunction(description =
        "Request the force value from the force sensor on the configured Port. "
        + "Fires ForceRead when the hub responds.")
    public void GetForce() {
        sendSensorSSP(new SSPMessage("sensor.read")
            .withPort(forceSensorPort).withParam("type", "force"));
    }

    /**
     * Request whether the force sensor on the configured Port is pressed.
     * Fires ForceSensorPressed(port, isPressed) when the hub responds.
     */
    @SimpleFunction(description =
        "Ask whether the force sensor on the configured Port is pressed. "
        + "Fires ForceSensorPressed when the hub responds.")
    public void IsForceSensorPressed() {
        sendSensorSSP(new SSPMessage("sensor.read")
            .withPort(forceSensorPort).withParam("type", "touched"));
    }

    @SimpleFunction(description =
        "Ask whether the color sensor reads the given color. "
        + "Fires ColorChecked when the hub responds.")
    public void IsColor(@Options(SensorColor.class) String color) {
        SensorColor c = SensorColor.fromUnderlyingValue(color);
        String name = c != null ? c.toUnderlyingValue().toLowerCase() : color.toLowerCase();
        sendSensorSSP(new SSPMessage("sensor.read")
            .withPort(colorSensorPort).withParam("type", "is_color").withParam("color", name));
    }

    @SimpleFunction(description =
        "Ask whether the given hub face is currently pointing up. "
        + "Fires OrientationChecked when the hub responds.")
    public void IsHubOrientation(@Options(HubFace.class) String face) {
        HubFace f = HubFace.fromUnderlyingValue(face);
        String name = f != null ? f.toUnderlyingValue() : face;
        sendSensorSSP(new SSPMessage("sensor.read")
            .withPort("imu").withParam("type", "is_orientation").withParam("face", name));
    }

    @SimpleFunction(description =
        "Ask whether the hub is currently being shaken. "
        + "Fires ShakingChecked when the hub responds.")
    public void IsShaking() {
        sendSensorSSP(new SSPMessage("sensor.read")
            .withPort("imu").withParam("type", "is_shaking"));
    }

    @SimpleFunction(description =
        "Ask whether a hub button is currently pressed. "
        + "Fires HubButtonChecked when the hub responds. "
        + "Note: physically pressing the Center button stops the hub program.")
    public void IsHubButtonPressed(@Options(HubButton.class) String button) {
        if (!checkConnected()) return;
        HubButton b = HubButton.fromUnderlyingValue(button);
        String name = b != null ? b.toUnderlyingValue() : button.toLowerCase();
        connectivity.sendSSP(new SSPMessage("system.read")
            .withParam("metric", "is_button_pressed").withParam("button", name));
    }

    @SimpleFunction(description =
        "Ask whether the hub is tilted in the given direction from flat. "
        + "Fires TiltChecked when the hub responds. "
        + "Directions: Forward (USB tilted down), Backward (mic down), "
        + "Left (A/C/E side down), Right (B/D/F side down), Any.")
    public void IsTilted(@Options(TiltDirection.class) String direction) {
        TiltDirection d = TiltDirection.fromUnderlyingValue(direction);
        String dir = d != null ? d.toUnderlyingValue().toLowerCase() : "any";
        sendSensorSSP(new SSPMessage("sensor.read")
            .withPort("imu").withParam("type", "is_tilted").withParam("direction", dir));
    }

    @SimpleFunction(description =
        "Ask whether an object is closer than the given distance (mm). "
        + "Fires DistanceChecked when the hub responds.")
    public void IsCloserThan(int mm) {
        sendSensorSSP(new SSPMessage("sensor.read")
            .withPort(distanceSensorPort).withParam("type", "is_closer").withParam("mm", mm));
    }

    @SimpleFunction(description =
        "Ask whether the reflected light is above the given percent (0–100). "
        + "Fires ReflectedLightChecked when the hub responds.")
    public void IsReflectedLightAbove(int percent) {
        sendSensorSSP(new SSPMessage("sensor.read")
            .withPort(colorSensorPort).withParam("type", "is_reflected_above")
            .withParam("percent", percent));
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

    @SimpleFunction(description =
        "Request the elapsed time (seconds) on the hub's clock since the last ResetHubTimer. "
        + "Fires HubTimerRead when the hub responds.")
    public void GetHubTimer() {
        sendSensorSSP(new SSPMessage("timer.get"));
    }

    @SimpleFunction(description = "Reset the hub's built-in timer to zero.")
    public void ResetHubTimer() {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("timer.reset"));
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
        "Fired when the hub reports a force sensor reading (0–100).")
    public void ForceRead(String port, int value) {
        EventDispatcher.dispatchEvent(this, "ForceRead", port, value);
    }

    @SimpleEvent(description =
        "Fired when the hub reports whether the force sensor is pressed.")
    public void ForceSensorPressed(String port, boolean isPressed) {
        EventDispatcher.dispatchEvent(this, "ForceSensorPressed", port, isPressed);
    }

    @SimpleEvent(description =
        "Fired when IsTilted responds. isTilted: true if hub is tilted in the given direction.")
    public void TiltChecked(String direction, boolean isTilted) {
        EventDispatcher.dispatchEvent(this, "TiltChecked", direction, isTilted);
    }

    @SimpleEvent(description =
        "Fired when IsHubOrientation responds. isMatch: true if that face is up.")
    public void OrientationChecked(String face, boolean isMatch) {
        EventDispatcher.dispatchEvent(this, "OrientationChecked", face, isMatch);
    }

    @SimpleEvent(description =
        "Fired when IsShaking responds. isShaking: true if the hub is being shaken.")
    public void ShakingChecked(boolean isShaking) {
        EventDispatcher.dispatchEvent(this, "ShakingChecked", isShaking);
    }

    @SimpleEvent(description =
        "Fired when IsHubButtonPressed responds. isPressed: true if that button is pressed.")
    public void HubButtonChecked(String button, boolean isPressed) {
        EventDispatcher.dispatchEvent(this, "HubButtonChecked", button, isPressed);
    }

    @SimpleEvent(description =
        "Fired when IsColor responds. isMatch: true if color matches.")
    public void ColorChecked(String port, String color, boolean isMatch) {
        EventDispatcher.dispatchEvent(this, "ColorChecked", port, color, isMatch);
    }

    @SimpleEvent(description =
        "Fired when IsCloserThan responds. isCloser: true if object is within the threshold.")
    public void DistanceChecked(String port, boolean isCloser) {
        EventDispatcher.dispatchEvent(this, "DistanceChecked", port, isCloser);
    }

    @SimpleEvent(description =
        "Fired when IsReflectedLightAbove responds. isAbove: true if reflected light exceeds threshold.")
    public void ReflectedLightChecked(String port, boolean isAbove) {
        EventDispatcher.dispatchEvent(this, "ReflectedLightChecked", port, isAbove);
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
        "Fired when GetHubTimer responds. seconds: elapsed time on the hub's clock since last ResetHubTimer.")
    public void HubTimerRead(int seconds) {
        EventDispatcher.dispatchEvent(this, "HubTimerRead", seconds);
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

            // System events: button subscriptions + is_button_pressed one-shot reads
            if ("system".equals(event)) {
                final String metric = obj.optString("metric");
                if ("is_button_pressed".equals(metric)) {
                    try {
                        org.json.JSONObject d = (org.json.JSONObject) obj.opt("value");
                        final boolean pressed = d.optBoolean("pressed", false);
                        final String btn = d.optString("button", "");
                        mainHandler.post(() -> HubButtonChecked(btn, pressed));
                    } catch (Exception ignored) {}
                    return;
                }
                if (metric.startsWith("button.")) {
                    final String btnName = metric.substring("button.".length());
                    if ("center".equals(btnName)) return; // center button kills hub program
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

            final String port      = obj.optString("port");
            final String type      = obj.optString("type");
            final Object val       = obj.opt("value");
            final boolean isOneShot = obj.has("request_id");

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
                        mainHandler.post(() -> ForceRead(port, value));
                    } catch (Exception ignored) {}
                    break;
                }
                case "touched": {
                    final boolean pressed = val instanceof Boolean
                        ? (Boolean) val
                        : "true".equalsIgnoreCase(val != null ? val.toString() : "");
                    mainHandler.post(() -> ForceSensorPressed(port, pressed));
                    break;
                }
                case "is_tilted": {
                    try {
                        org.json.JSONObject d = (org.json.JSONObject) val;
                        final boolean tilted = d.optBoolean("tilted", false);
                        final String dir = d.optString("direction", "any");
                        mainHandler.post(() -> TiltChecked(dir, tilted));
                    } catch (Exception ignored) {}
                    break;
                }
                case "is_orientation": {
                    try {
                        org.json.JSONObject d = (org.json.JSONObject) val;
                        final boolean match = d.optBoolean("match", false);
                        final String face = d.optString("face", "");
                        mainHandler.post(() -> OrientationChecked(face, match));
                    } catch (Exception ignored) {}
                    break;
                }
                case "is_shaking": {
                    final boolean shaking = val instanceof Boolean ? (Boolean) val
                        : "true".equalsIgnoreCase(val != null ? val.toString() : "");
                    mainHandler.post(() -> ShakingChecked(shaking));
                    break;
                }
                case "is_color": {
                    try {
                        org.json.JSONObject d = (org.json.JSONObject) val;
                        final boolean match = d.optBoolean("match", false);
                        final String checkedColor = d.optString("color", "");
                        mainHandler.post(() -> ColorChecked(port, checkedColor, match));
                    } catch (Exception ignored) {}
                    break;
                }
                case "is_closer": {
                    final boolean closer = val instanceof Boolean ? (Boolean) val
                        : "true".equalsIgnoreCase(val != null ? val.toString() : "");
                    mainHandler.post(() -> DistanceChecked(port, closer));
                    break;
                }
                case "is_reflected_above": {
                    final boolean above = val instanceof Boolean ? (Boolean) val
                        : "true".equalsIgnoreCase(val != null ? val.toString() : "");
                    mainHandler.post(() -> ReflectedLightChecked(port, above));
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
                    if (isOneShot) {
                        mainHandler.post(() -> HubFaceOrientationRead(fo));
                    } else {
                        mainHandler.post(() -> HubFaceOrientationChanged(fo));
                    }
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
                case "elapsed": {
                    try {
                        final int seconds = val instanceof Number
                            ? ((Number) val).intValue()
                            : Integer.parseInt(val.toString());
                        mainHandler.post(() -> HubTimerRead(seconds));
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
        String name = f != null ? f.toUnderlyingValue() : "Top";
        connectivity.sendSSP(new SSPMessage("orientation.set_reference").withParam("face", name));
    }

    @SimpleFunction(description =
        "Subscribe to left hub button events. WhenHubButtonPressed/WhenHubButtonReleased fire on change.")
    public void SubscribeToHubLeftButton() {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("system.subscribe")
            .withParam("metric", "button.left").withParam("interval", 100));
    }

    @SimpleFunction(description =
        "Subscribe to right hub button events. WhenHubButtonPressed/WhenHubButtonReleased fire on change.")
    public void SubscribeToHubRightButton() {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("system.subscribe")
            .withParam("metric", "button.right").withParam("interval", 100));
    }

    // SubscribeToHubCenterButton removed: pressing the center button terminates the
    // hub Python program at firmware level — it cannot be detected from user code.

    @SimpleFunction(description =
        "Subscribe to gesture events. HubGestureDetected fires on shake, tap, double_tap, or fall.")
    public void SubscribeToHubGestures() {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("sensor.subscribe")
            .withPort("imu")
            .withParam("type", "gesture")
            .withParam("mode", "on_change"));
    }

    @SimpleFunction(description =
        "Subscribe to face orientation changes. HubFaceOrientationChanged fires when the hub flips.")
    public void SubscribeToHubFaceOrientation() {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("sensor.subscribe")
            .withPort("imu")
            .withParam("type", "face_orientation")
            .withParam("mode", "on_change"));
    }

    @SimpleFunction(description =
        "Request RGB values (0–255) from the color sensor. Fires ColorRGBRead when received.")
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
        + "faceOrientation: Top, Bottom, Front, Back, Left side, Right side.")
    public void HubFaceOrientationRead(String faceOrientation) {
        EventDispatcher.dispatchEvent(this, "HubFaceOrientationRead", faceOrientation);
    }

    @SimpleEvent(description =
        "Fired when the hub detects a gesture (after SubscribeToHubGestures). "
        + "gesture: shake, tap, double_tap, or fall.")
    public void HubGestureDetected(String gesture) {
        EventDispatcher.dispatchEvent(this, "HubGestureDetected", gesture);
    }

    @SimpleEvent(description =
        "Fired when the hub detects a face-orientation change (after SubscribeToHubFaceOrientation). "
        + "faceOrientation: Top, Bottom, Front, Back, Left side, Right side.")
    public void HubFaceOrientationChanged(String faceOrientation) {
        EventDispatcher.dispatchEvent(this, "HubFaceOrientationChanged", faceOrientation);
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
        "Light up the 4 indicator LEDs on the distance sensor. "
        + "Uses DistanceSensorPort. Each brightness value is 0–100.")
    public void LightUpDistanceSensor(int topLeft, int topRight,
                                      int bottomLeft, int bottomRight) {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("led.distance")
            .withPort(distanceSensorPort)
            .withParam("tl", Math.max(0, Math.min(100, topLeft)))
            .withParam("tr", Math.max(0, Math.min(100, topRight)))
            .withParam("bl", Math.max(0, Math.min(100, bottomLeft)))
            .withParam("br", Math.max(0, Math.min(100, bottomRight))));
    }

    // =========================================================================
    // Helpers
    // =========================================================================
    private void sendSensorSSP(SSPMessage msg) {
        if (!checkConnected()) return;
        connectivity.sendSSP(msg.withRequestId("r" + readSeq.incrementAndGet()));
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
