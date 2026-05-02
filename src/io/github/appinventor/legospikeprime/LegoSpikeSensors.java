package io.github.appinventor.legospikeprime;

import android.os.Handler;
import android.os.Looper;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.Options;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.Port;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.common.SensorColor;
import com.google.appinventor.components.common.TiltAxis;
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
    private String axis               = "pitch";

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
    // Axis property
    // =========================================================================
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Hub tilt axis used by GetTiltAngle: Pitch, Roll, or Yaw")
    @DesignerProperty(
        editorType   = PropertyTypeConstants.PROPERTY_TYPE_CHOICES,
        editorArgs   = {"Pitch", "Roll", "Yaw"},
        defaultValue = "Pitch")
    public void Axis(@Options(TiltAxis.class) String value) {
        if ("pitch".equalsIgnoreCase(value) || "roll".equalsIgnoreCase(value)
                || "yaw".equalsIgnoreCase(value)) {
            axis = value.toLowerCase();
        }
    }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Hub tilt axis used by GetTiltAngle: Pitch, Roll, or Yaw")
    public String Axis() { return axis; }

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
        sendSensorCommand("SEN:CLR:" + colorSensorPort);
    }

    /**
     * Request the distance measured by the distance sensor on the configured Port.
     * Fires DistanceRead(port, mm) when the hub responds. Returns -1 if out of range.
     */
    @SimpleFunction(description =
        "Request the distance (mm) from the distance sensor on the configured Port. "
        + "Fires DistanceRead when the hub responds. Returns -1 if out of range.")
    public void GetDistance() {
        sendSensorCommand("SEN:DST:" + distanceSensorPort);
    }

    /**
     * Request the force measured by the force sensor on the configured Port.
     * Fires PressureRead(port, value) when the hub responds.
     */
    @SimpleFunction(description =
        "Request the force value from the force sensor on the configured Port. "
        + "Fires PressureRead when the hub responds.")
    public void GetPressure() {
        sendSensorCommand("SEN:PRS:" + pressureSensorPort);
    }

    /**
     * Request whether the force sensor on the configured Port is pressed.
     * Fires PressureChecked(port, isPressed) when the hub responds.
     */
    @SimpleFunction(description =
        "Ask whether the force sensor on the configured Port is pressed. "
        + "Fires PressureChecked when the hub responds.")
    public void IsPressed() {
        sendSensorCommand("SEN:ISP:" + pressureSensorPort);
    }

    /**
     * Request the hub tilt angle for the configured Axis.
     * Fires TiltAngleRead(axis, degrees) when the hub responds.
     */
    @SimpleFunction(description =
        "Request the hub tilt angle for the configured Axis (Pitch, Roll, or Yaw). "
        + "Fires TiltAngleRead when the hub responds.")
    public void GetTiltAngle() {
        sendSensorCommand("SEN:TLT:" + axis);
    }

    /**
     * Request the elapsed time since the last ResetTimer call.
     * Fires TimerRead(seconds) when the hub responds.
     */
    @SimpleFunction(description =
        "Request the elapsed time (seconds) since the last ResetTimer. "
        + "Fires TimerRead when the hub responds.")
    public void GetTimer() {
        sendSensorCommand("SEN:TMR");
    }

    /** Reset the hub timer to zero. */
    @SimpleFunction(description = "Reset the hub timer to zero")
    public void ResetTimer() {
        if (!checkConnected()) return;
        connectivity.sendCommand("SEN:TMRR");
    }

    // =========================================================================
    // Events
    // =========================================================================

    @SimpleEvent(description =
        "Fired when the hub reports a color reading. "
        + "color: title-case name (e.g. Red, Green, None). "
        + "Use a SensorColor block from the dropdown to compare: if color = SensorColor.Red")
    public void ColorRead(String port, @Options(SensorColor.class) String color) {
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
    public void TiltAngleRead(String axis, int degrees) {
        EventDispatcher.dispatchEvent(this, "TiltAngleRead", axis, degrees);
    }

    @SimpleEvent(description =
        "Fired when the hub reports the elapsed timer value in whole seconds.")
    public void TimerRead(int seconds) {
        EventDispatcher.dispatchEvent(this, "TimerRead", seconds);
    }

    // =========================================================================
    // HubDataListener — parse sensor responses
    // =========================================================================
    @Override
    public void onHubData(String data) {
        if (data == null || !data.startsWith("SEN:")) return;
        String[] parts = data.split(":");
        if (parts.length < 3) return;

        final String type = parts[1];

        switch (type) {
            case "CLR":
                if (parts.length >= 4) {
                    final String p   = parts[2];
                    final String colorLower = parts[3].toLowerCase(); // "RED" -> "red"
                    mainHandler.post(() -> ColorRead(p, colorLower));
                }
                break;
            case "DST":
                if (parts.length >= 4) {
                    final String p = parts[2];
                    try {
                        final int mm = Integer.parseInt(parts[3]);
                        mainHandler.post(() -> DistanceRead(p, mm));
                    } catch (NumberFormatException ignored) {}
                }
                break;
            case "PRS":
                if (parts.length >= 4) {
                    final String p = parts[2];
                    try {
                        final int value = Integer.parseInt(parts[3]);
                        mainHandler.post(() -> PressureRead(p, value));
                    } catch (NumberFormatException ignored) {}
                }
                break;
            case "ISP":
                if (parts.length >= 4) {
                    final String p       = parts[2];
                    final boolean pressed = "1".equals(parts[3]);
                    mainHandler.post(() -> PressureChecked(p, pressed));
                }
                break;
            case "TLT":
                if (parts.length >= 4) {
                    // Hub echoes back whatever axis string we sent, now title-case
                    final String titleAx = parts[2].toLowerCase(); // hub echoes "PITCH" -> "pitch"
                    try {
                        final int degrees = Integer.parseInt(parts[3]);
                        mainHandler.post(() -> TiltAngleRead(titleAx, degrees));
                    } catch (NumberFormatException ignored) {}
                }
                break;
            case "TMR":
                if (parts.length >= 3) {
                    try {
                        final int seconds = Integer.parseInt(parts[2]);
                        mainHandler.post(() -> TimerRead(seconds));
                    } catch (NumberFormatException ignored) {}
                }
                break;
            default:
                break;
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================
    private void sendSensorCommand(String cmd) {
        if (!checkConnected()) return;
        connectivity.sendCommand(cmd);
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
