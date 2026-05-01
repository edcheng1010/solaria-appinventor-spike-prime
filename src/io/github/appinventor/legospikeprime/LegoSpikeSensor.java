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
import com.google.appinventor.components.common.MotorPort;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.common.TiltAxis;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;

/**
 * LegoSpikeSensor — sensor reads and hub state queries.
 * Matches LEGO SPIKE Prime "Sensor Blocks" + "More Sensors" categories.
 *
 * MVP read functions (each fires a corresponding event asynchronously):
 *   GetColor(port)        → ColorRead(port, color)
 *   GetDistance(port)     → DistanceRead(port, mm)
 *   GetPressure(port)     → PressureRead(port, value)
 *   IsPressed(port)       → PressureChecked(port, isPressed)
 *   GetTiltAngle(axis)    → TiltAngleRead(axis, degrees)
 *   GetTimer()            → TimerRead(seconds)
 *   ResetTimer()
 *
 * axis values for GetTiltAngle: "pitch", "roll", "yaw"
 *
 * Dependency: set the Connectivity property to a LegoSpikeConnectivity instance.
 * The hub controller program sends sensor data back via TunnelMessage;
 * this component parses those responses and fires the corresponding events.
 */
@SimpleObject(external = true)
@DesignerComponent(version = 2,
    description = "Reads sensors on a LEGO SPIKE Prime hub. "
        + "Each GetXxx() call requests a value; the result fires as an event. "
        + "Set the Connectivity property to a LegoSpikeConnectivity component.",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = "aiwebres/legospike.png")
public class LegoSpikeSensor extends AndroidNonvisibleComponent
        implements LegoSpikeConnectivity.HubDataListener {

    private LegoSpikeConnectivity connectivity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public LegoSpikeSensor(ComponentContainer container) {
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
        // Unregister from old connectivity
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
    // MVP read functions
    // =========================================================================

    /**
     * Request the color detected by the color sensor on the given port.
     * Fires ColorRead(port, color) when the hub responds.
     *
     * @param port port letter A–F
     */
    @SimpleFunction(description =
        "Request the color detected by the color sensor on the given port (A-F). "
        + "Fires ColorRead when the hub responds.")
    public void GetColor(@Options(MotorPort.class) String port) {
        sendSensorCommand("SEN:CLR:" + validatePort(port));
    }

    /**
     * Request the distance measured by the distance sensor on the given port.
     * Fires DistanceRead(port, mm) when the hub responds. Returns -1 if invalid.
     *
     * @param port port letter A–F
     */
    @SimpleFunction(description =
        "Request the distance (mm) from the distance sensor on the given port (A-F). "
        + "Fires DistanceRead when the hub responds. Returns -1 if out of range.")
    public void GetDistance(@Options(MotorPort.class) String port) {
        sendSensorCommand("SEN:DST:" + validatePort(port));
    }

    /**
     * Request the force measured by the force sensor on the given port.
     * Fires PressureRead(port, value) when the hub responds.
     *
     * @param port port letter A–F
     */
    @SimpleFunction(description =
        "Request the force value from the force sensor on the given port (A-F). "
        + "Fires PressureRead when the hub responds.")
    public void GetPressure(@Options(MotorPort.class) String port) {
        sendSensorCommand("SEN:PRS:" + validatePort(port));
    }

    /**
     * Request whether the force sensor on the given port is pressed.
     * Fires PressureChecked(port, isPressed) when the hub responds.
     *
     * @param port port letter A–F
     */
    @SimpleFunction(description =
        "Ask whether the force sensor on the given port (A-F) is currently pressed. "
        + "Fires PressureChecked when the hub responds.")
    public void IsPressed(@Options(MotorPort.class) String port) {
        sendSensorCommand("SEN:ISP:" + validatePort(port));
    }

    /**
     * Request the hub tilt angle for the given axis.
     * Fires TiltAngleRead(axis, degrees) when the hub responds.
     *
     * @param axis "pitch", "roll", or "yaw"
     */
    @SimpleFunction(description =
        "Request the hub tilt angle for the given axis (\"pitch\", \"roll\", or \"yaw\"). "
        + "Fires TiltAngleRead when the hub responds.")
    public void GetTiltAngle(@Options(TiltAxis.class) String axis) {
        if (axis == null || axis.isEmpty()) axis = "PITCH";
        String a = axis.toUpperCase().trim();
        if (!a.equals("PITCH") && !a.equals("ROLL") && !a.equals("YAW")) {
            reportError("Invalid axis: " + axis + " — use \"Pitch\", \"Roll\", or \"Yaw\"");
            return;
        }
        sendSensorCommand("SEN:TLT:" + a);
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

    /** Fired when the hub reports a color sensor reading. */
    @SimpleEvent(description =
        "Fired when the hub reports a color reading. "
        + "color: color name string (e.g. RED, GREEN, BLUE, NONE).")
    public void ColorRead(String port, String color) {
        EventDispatcher.dispatchEvent(this, "ColorRead", port, color);
    }

    /** Fired when the hub reports a distance sensor reading. */
    @SimpleEvent(description =
        "Fired when the hub reports a distance reading in millimetres. "
        + "Returns -1 if out of range or no sensor connected.")
    public void DistanceRead(String port, int mm) {
        EventDispatcher.dispatchEvent(this, "DistanceRead", port, mm);
    }

    /** Fired when the hub reports a force sensor reading. */
    @SimpleEvent(description =
        "Fired when the hub reports a force/pressure sensor reading (0-100).")
    public void PressureRead(String port, int value) {
        EventDispatcher.dispatchEvent(this, "PressureRead", port, value);
    }

    /** Fired when the hub reports whether the force sensor is pressed. */
    @SimpleEvent(description =
        "Fired when the hub reports whether the force sensor is pressed.")
    public void PressureChecked(String port, boolean isPressed) {
        EventDispatcher.dispatchEvent(this, "PressureChecked", port, isPressed);
    }

    /** Fired when the hub reports a tilt angle. */
    @SimpleEvent(description =
        "Fired when the hub reports a tilt angle. "
        + "axis: \"PITCH\", \"ROLL\", or \"YAW\". degrees: angle in degrees.")
    public void TiltAngleRead(String axis, int degrees) {
        EventDispatcher.dispatchEvent(this, "TiltAngleRead", axis, degrees);
    }

    /** Fired when the hub reports the elapsed timer value. */
    @SimpleEvent(description =
        "Fired when the hub reports the elapsed timer value in whole seconds.")
    public void TimerRead(int seconds) {
        EventDispatcher.dispatchEvent(this, "TimerRead", seconds);
    }

    // =========================================================================
    // HubDataListener — parse sensor responses from the hub
    // =========================================================================

    /**
     * Called by LegoSpikeConnectivity whenever the hub sends a non-rdy tunnel payload.
     * Expected formats:
     *   SEN:CLR:A:RED       → ColorRead("A", "RED")
     *   SEN:DST:A:150       → DistanceRead("A", 150)
     *   SEN:PRS:A:50        → PressureRead("A", 50)
     *   SEN:ISP:A:1         → PressureChecked("A", true)
     *   SEN:TLT:PITCH:15    → TiltAngleRead("PITCH", 15)
     *   SEN:TMR:3           → TimerRead(3)
     */
    @Override
    public void onHubData(String data) {
        if (data == null || !data.startsWith("SEN:")) return;
        String[] parts = data.split(":");
        if (parts.length < 3) return;

        final String type = parts[1];

        switch (type) {
            case "CLR":
                if (parts.length >= 4) {
                    final String port  = parts[2];
                    final String color = parts[3];
                    mainHandler.post(() -> ColorRead(port, color));
                }
                break;

            case "DST":
                if (parts.length >= 4) {
                    final String port = parts[2];
                    try {
                        final int mm = Integer.parseInt(parts[3]);
                        mainHandler.post(() -> DistanceRead(port, mm));
                    } catch (NumberFormatException ignored) {}
                }
                break;

            case "PRS":
                if (parts.length >= 4) {
                    final String port = parts[2];
                    try {
                        final int value = Integer.parseInt(parts[3]);
                        mainHandler.post(() -> PressureRead(port, value));
                    } catch (NumberFormatException ignored) {}
                }
                break;

            case "ISP":
                if (parts.length >= 4) {
                    final String port = parts[2];
                    final boolean pressed = "1".equals(parts[3]);
                    mainHandler.post(() -> PressureChecked(port, pressed));
                }
                break;

            case "TLT":
                if (parts.length >= 4) {
                    final String axis = parts[2];
                    try {
                        final int degrees = Integer.parseInt(parts[3]);
                        mainHandler.post(() -> TiltAngleRead(axis, degrees));
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

    private String validatePort(String port) {
        if (port == null || port.isEmpty()) return "A";
        String p = port.toUpperCase().trim();
        return p.matches("[A-F]") ? p : "A";
    }

    private void reportError(String msg) {
        if (connectivity != null) connectivity.ErrorOccurred(msg);
    }
}
