package io.github.appinventor.legospikeprime;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.Options;
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
    public void Port(@Options(Port.class) String value) {
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
    public void Direction(@Options(MotorDirection.class) String value) {
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
        int effectiveSpeed = "counterclockwise".equalsIgnoreCase(direction) ? -speed : speed;
        connectivity.sendSSP(
            new SSPMessage("motor.run")
                .withPort(port)
                .withParam("speed", effectiveSpeed));
    }

    @SimpleFunction(description = "Stop the motor on the configured Port")
    public void StopMotor() {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("motor.stop").withPort(port));
    }

    @SimpleFunction(description =
        "Set the motor speed (0–100). Applied on the next StartMotor call.")
    public void SetMotorSpeed(int value) {
        speed = Math.max(0, Math.min(100, value));
    }

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
