package io.github.appinventor.legospikeprime;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;

/**
 * LegoSpikeMotor — individual motor control.
 * Matches LEGO SPIKE Prime "Motor Blocks Category".
 *
 * Configure Port and Direction in the Designer or via blocks, then call
 * StartMotor(), StopMotor(), or SetMotorSpeed(). One LegoSpikeMotor instance
 * per physical motor is the recommended pattern.
 */
@SimpleObject(external = true)
@DesignerComponent(version = 3,
    description = "Controls an individual motor on a LEGO SPIKE Prime hub. "
        + "Set Port (A-F) and Direction, then call StartMotor or StopMotor. "
        + "Set the Connectivity property to a LegoSpikeConnectivity component.",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = "aiwebres/legospike.png")
public class LegoSpikeMotor extends AndroidNonvisibleComponent {

    private LegoSpikeConnectivity connectivity;

    private String port      = "A";
    private String direction = "clockwise";
    private int    speed     = 50;

    public LegoSpikeMotor(ComponentContainer container) {
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
        if (component instanceof LegoSpikeConnectivity) {
            this.connectivity = (LegoSpikeConnectivity) component;
        }
    }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "The LegoSpikeConnectivity component managing the hub connection")
    public Component Connectivity() { return connectivity; }

    // =========================================================================
    // Port property — designer dropdown + block getter/setter
    // =========================================================================
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "The motor port (A–F) this component controls")
    @DesignerProperty(
        editorType  = PropertyTypeConstants.PROPERTY_TYPE_CHOICES,
        editorArgs  = {"A", "B", "C", "D", "E", "F"},
        defaultValue = "A")
    public void Port(String value) {
        if (value != null && value.toUpperCase().trim().matches("[A-F]")) {
            port = value.toUpperCase().trim();
        }
    }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "The motor port (A–F) this component controls")
    public String Port() { return port; }

    // =========================================================================
    // Direction property — designer dropdown + block getter/setter
    // =========================================================================
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "The motor direction: \"clockwise\" or \"counterclockwise\"")
    @DesignerProperty(
        editorType  = PropertyTypeConstants.PROPERTY_TYPE_CHOICES,
        editorArgs  = {"clockwise", "counterclockwise"},
        defaultValue = "clockwise")
    public void Direction(String value) {
        if ("clockwise".equalsIgnoreCase(value) || "counterclockwise".equalsIgnoreCase(value)) {
            direction = value.toLowerCase();
        }
    }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "The motor direction: \"clockwise\" or \"counterclockwise\"")
    public String Direction() { return direction; }

    // =========================================================================
    // Motor control blocks
    // =========================================================================

    /**
     * Start the motor using the configured Port, Direction, and speed.
     * Set Port, Direction, and optionally SetMotorSpeed before calling.
     */
    @SimpleFunction(description =
        "Start the motor using the configured Port and Direction. "
        + "Set Port and Direction in the Designer or via blocks first.")
    public void StartMotor() {
        if (!checkConnected()) return;
        String dirCode = direction.startsWith("counter") ? "CCW" : "CW";
        connectivity.sendCommand(String.format("MTR:%s:%s:%03d", port, dirCode, speed));
    }

    /**
     * Stop the motor on the configured Port.
     */
    @SimpleFunction(description = "Stop the motor on the configured Port")
    public void StopMotor() {
        if (!checkConnected()) return;
        connectivity.sendCommand("MTR:" + port + ":STOP");
    }

    /**
     * Set the speed used by the next StartMotor call.
     *
     * @param value 0–100 percent
     */
    @SimpleFunction(description =
        "Set the motor speed (0–100). Applied on the next StartMotor call.")
    public void SetMotorSpeed(int value) {
        speed = Math.max(0, Math.min(100, value));
    }

    // =========================================================================
    // Helpers
    // =========================================================================
    private boolean checkConnected() {
        if (connectivity == null)       { reportError("Connectivity not set");   return false; }
        if (!connectivity.IsConnected()){ reportError("Not connected to hub");   return false; }
        return true;
    }

    private void reportError(String msg) {
        if (connectivity != null) connectivity.ErrorOccurred(msg);
    }
}
