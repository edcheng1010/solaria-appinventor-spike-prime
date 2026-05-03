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

@SimpleObject(external = true)
@DesignerComponent(version = 3,
    description = "Controls an individual motor on a LEGO SPIKE Prime hub. "
        + "Set Port (A-F) and Direction, then call StartMotor or StopMotor. "
        + "Set the Connectivity property to a LegoSpikeConnectivity component.",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = "aiwebres/legospike.png")
public class LegoSpikeMotors extends AndroidNonvisibleComponent {

    private LegoSpikeConnectivity connectivity;

    private String port      = "A";
    private String direction = "Clockwise";
    private int    speed     = 50;

    public LegoSpikeMotors(ComponentContainer container) {
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
    // Port property
    // =========================================================================
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "The motor port (A–F) this component controls")
    @DesignerProperty(
        editorType   = PropertyTypeConstants.PROPERTY_TYPE_CHOICES,
        editorArgs   = {"A", "B", "C", "D", "E", "F"},
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
    // Direction property
    // =========================================================================
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "The motor direction. Use the Clockwise or Counterclockwise constant blocks.")
    @DesignerProperty(
        editorType   = PropertyTypeConstants.PROPERTY_TYPE_CHOICES,
        editorArgs   = {"Clockwise", "Counterclockwise"},
        defaultValue = "Clockwise")
    public void Direction(String value) {
        if ("clockwise".equalsIgnoreCase(value) || "counterclockwise".equalsIgnoreCase(value)) {
            String v = value.trim();
            direction = v.substring(0, 1).toUpperCase() + v.substring(1).toLowerCase();
        }
    }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "The motor direction. Use the Clockwise or Counterclockwise constant blocks.")
    public String Direction() { return direction; }

    // =========================================================================
    // Motor control blocks
    // =========================================================================
    @SimpleFunction(description =
        "Start the motor using the configured Port and Direction. "
        + "Set Port and Direction in the Designer or via blocks first.")
    public void StartMotor() {
        if (!checkConnected()) return;
        String dirCode = "counterclockwise".equalsIgnoreCase(direction) ? "CCW" : "CW";
        connectivity.sendCommand(String.format("MTR:%s:%s:%03d", port, dirCode, speed));
    }

    @SimpleFunction(description = "Stop the motor on the configured Port")
    public void StopMotor() {
        if (!checkConnected()) return;
        connectivity.sendCommand("MTR:" + port + ":STOP");
    }

    @SimpleFunction(description =
        "Set the motor speed (0–100). Applied on the next StartMotor call.")
    public void SetMotorSpeed(int value) {
        speed = Math.max(0, Math.min(100, value));
    }

    // =========================================================================
    // Direction constants — drag into the Direction property setter block
    // =========================================================================
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Direction constant: Clockwise. Use with set Direction to Clockwise.")
    public String Clockwise() { return "Clockwise"; }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Direction constant: Counterclockwise. Use with set Direction to Counterclockwise.")
    public String Counterclockwise() { return "Counterclockwise"; }

    // =========================================================================
    // Helpers
    // =========================================================================
    private boolean checkConnected() {
        if (connectivity == null)        { reportError("Connectivity not set");  return false; }
        if (!connectivity.IsConnected()) { reportError("Not connected to hub");  return false; }
        return true;
    }

    private void reportError(String msg) {
        if (connectivity != null) connectivity.ErrorOccurred(msg);
    }
}
