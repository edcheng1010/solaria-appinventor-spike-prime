package io.github.appinventor.legospikeprime;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.Options;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.common.MovementDirection;
import com.google.appinventor.components.common.Port;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;

/**
 * LegoSpikeMovement — synchronized two-motor drivebase control.
 * Matches LEGO SPIKE Prime "Movement Blocks" category.
 *
 * Configure LeftPort, RightPort, Direction, and Speed in the Designer or
 * via blocks, then call SetMovementMotors, StartMoving, StopMoving, etc.
 * One LegoSpikeMovement instance controls one drivebase pair.
 */
@SimpleObject(external = true)
@DesignerComponent(version = 4,
    description = "Controls a two-motor drivebase on a LEGO SPIKE Prime hub. "
        + "Set LeftPort, RightPort, and Direction, then call SetMovementMotors and StartMoving. "
        + "Set the Connectivity property to a LegoSpikeConnectivity component.",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = "aiwebres/legospike.png")
public class LegoSpikeMovement extends AndroidNonvisibleComponent {

    private LegoSpikeConnectivity connectivity;

    private String leftPort      = "A";
    private String rightPort     = "B";
    private String direction     = "Forward";
    private int    movementSpeed = 50;

    public LegoSpikeMovement(ComponentContainer container) {
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
    // LeftPort property
    // =========================================================================
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Port (A–F) of the left motor in the drivebase")
    @DesignerProperty(
        editorType   = PropertyTypeConstants.PROPERTY_TYPE_CHOICES,
        editorArgs   = {"A", "B", "C", "D", "E", "F"},
        defaultValue = "A")
    public void LeftPort(@Options(Port.class) String value) {
        if (value != null && value.toUpperCase().trim().matches("[A-F]")) {
            leftPort = value.toUpperCase().trim();
        }
    }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Port (A–F) of the left motor in the drivebase")
    public String LeftPort() { return leftPort; }

    // =========================================================================
    // RightPort property
    // =========================================================================
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Port (A–F) of the right motor in the drivebase")
    @DesignerProperty(
        editorType   = PropertyTypeConstants.PROPERTY_TYPE_CHOICES,
        editorArgs   = {"A", "B", "C", "D", "E", "F"},
        defaultValue = "B")
    public void RightPort(@Options(Port.class) String value) {
        if (value != null && value.toUpperCase().trim().matches("[A-F]")) {
            rightPort = value.toUpperCase().trim();
        }
    }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Port (A–F) of the right motor in the drivebase")
    public String RightPort() { return rightPort; }

    // =========================================================================
    // Direction property
    // =========================================================================
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Movement direction: \"Forward\" or \"Backward\"")
    @DesignerProperty(
        editorType   = PropertyTypeConstants.PROPERTY_TYPE_CHOICES,
        editorArgs   = {"Forward", "Backward"},
        defaultValue = "Forward")
    public void Direction(@Options(MovementDirection.class) String value) {
        if ("forward".equalsIgnoreCase(value) || "backward".equalsIgnoreCase(value)) {
            String v = value.trim();
            direction = v.substring(0, 1).toUpperCase() + v.substring(1).toLowerCase();
        }
    }

    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "Movement direction: \"Forward\" or \"Backward\"")
    public String Direction() { return direction; }

    // =========================================================================
    // Movement blocks
    // =========================================================================

    /**
     * Pair the configured LeftPort and RightPort as the drivebase motors.
     * Call once after HubConnected. Default: A = left, B = right.
     */
    @SimpleFunction(description =
        "Pair the configured LeftPort and RightPort as the drivebase motors. "
        + "Call once after HubConnected.")
    public void SetMovementMotors() {
        if (!checkConnected()) return;
        connectivity.sendCommand("MOV:PAIR:" + leftPort + ":" + rightPort);
    }

    /**
     * Set the speed used by StartMoving and StartMovingWithSteering.
     * Stored locally — no command is sent to the hub.
     *
     * @param speed 0–100 percent
     */
    @SimpleFunction(description =
        "Set the movement speed (0–100). Applied on the next StartMoving or "
        + "StartMovingWithSteering call.")
    public void SetMovementSpeed(int speed) {
        movementSpeed = Math.max(0, Math.min(100, speed));
    }

    /**
     * Start moving the drivebase using the configured Direction and speed.
     */
    @SimpleFunction(description =
        "Start moving the drivebase using the configured Direction and speed.")
    public void StartMoving() {
        if (!checkConnected()) return;
        String cmd = direction.equalsIgnoreCase("forward")
            ? String.format("MOV:FWD:%03d", movementSpeed)
            : String.format("MOV:BWD:%03d", movementSpeed);
        connectivity.sendCommand(cmd);
    }

    /**
     * Start moving with a steering offset.
     * steering = 0: straight. steering = -100: full left. steering = +100: full right.
     * Uses the stored speed from SetMovementSpeed.
     *
     * @param steering –100 to +100
     */
    @SimpleFunction(description =
        "Start moving with steering (–100 to +100, 0 = straight). "
        + "Uses the speed set by SetMovementSpeed.")
    public void StartMovingWithSteering(int steering) {
        if (!checkConnected()) return;
        steering = Math.max(-100, Math.min(100, steering));
        connectivity.sendCommand(
            String.format("MOV:STEER:%+d:%03d", steering, movementSpeed));
    }

    /** Stop the drivebase immediately. */
    @SimpleFunction(description = "Stop the drivebase immediately")
    public void StopMoving() {
        if (!checkConnected()) return;
        connectivity.sendCommand("MOV:STOP");
    }

    // =========================================================================
    // Helpers
    // =========================================================================
    private boolean checkConnected() {
        if (connectivity == null) { reportError("Connectivity not set"); return false; }
        if (!connectivity.IsConnected()) { reportError("Not connected to hub"); return false; }
        return true;
    }

    private void reportError(String msg) {
        if (connectivity != null) connectivity.ErrorOccurred(msg);
    }
}
