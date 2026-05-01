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
import com.google.appinventor.components.common.MotorPort;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;

/**
 * LegoSpikeMovement — synchronized two-motor drivebase control.
 * Matches LEGO SPIKE Prime "Movement Blocks" category.
 *
 * MVP blocks: SetMovementMotors, SetMovementSpeed, StartMoving,
 *             StartMovingWithSteering, StopMoving.
 *
 * Dependency: set the Connectivity property to a LegoSpikeConnectivity instance.
 * Call SetMovementMotors once after HubConnected to configure the drivebase ports.
 */
@SimpleObject(external = true)
@DesignerComponent(version = 2,
    description = "Controls a two-motor drivebase on a LEGO SPIKE Prime hub. "
        + "Set the Connectivity property to a LegoSpikeConnectivity component.",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = "aiwebres/legospike.png")
public class LegoSpikeMovement extends AndroidNonvisibleComponent {

    private LegoSpikeConnectivity connectivity;

    private int    movementSpeed = 50;   // 0-100, stored locally
    private String leftPort      = "A";
    private String rightPort     = "B";

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
    // MVP blocks
    // =========================================================================

    /**
     * Configure which ports are the left and right motors of the drivebase.
     * Call once after HubConnected. Default: A = left, B = right.
     *
     * @param leftPort  port letter A–F for the left motor
     * @param rightPort port letter A–F for the right motor
     */
    @SimpleFunction(description =
        "Set the left and right motor ports of the drivebase (A-F). "
        + "Call once after HubConnected. Default: A = left, B = right.")
    public void SetMovementMotors(@Options(MotorPort.class) String leftPort,
                                  @Options(MotorPort.class) String rightPort) {
        if (!checkConnected()) return;
        leftPort  = leftPort.toUpperCase().trim();
        rightPort = rightPort.toUpperCase().trim();
        if (!isValidPort(leftPort))  { reportError("Invalid left port: "  + leftPort);  return; }
        if (!isValidPort(rightPort)) { reportError("Invalid right port: " + rightPort); return; }
        this.leftPort  = leftPort;
        this.rightPort = rightPort;
        connectivity.sendCommand("MOV:PAIR:" + leftPort + ":" + rightPort);
    }

    /**
     * Set the speed used by StartMoving and StartMovingWithSteering.
     * Stored locally — no command is sent to the hub.
     *
     * @param speed 0–100 percent
     */
    @SimpleFunction(description =
        "Set the movement speed (0-100). Applied on the next StartMoving or "
        + "StartMovingWithSteering call.")
    public void SetMovementSpeed(int speed) {
        movementSpeed = Math.max(0, Math.min(100, speed));
    }

    /**
     * Start moving the drivebase in the given direction using the stored speed.
     *
     * @param direction "forward" or "backward"
     */
    @SimpleFunction(description =
        "Start moving the drivebase. direction: \"forward\" or \"backward\". "
        + "Uses the speed set by SetMovementSpeed.")
    public void StartMoving(@Options(MovementDirection.class) String direction) {
        if (!checkConnected()) return;
        String dir = direction.toLowerCase().trim();
        String cmd;
        if (dir.equals("forward")) {
            cmd = String.format("MOV:FWD:%03d", movementSpeed);
        } else if (dir.equals("backward")) {
            cmd = String.format("MOV:BWD:%03d", movementSpeed);
        } else {
            reportError("Invalid direction: " + direction
                + " — use \"forward\" or \"backward\"");
            return;
        }
        connectivity.sendCommand(cmd);
    }

    /**
     * Start moving with a steering offset.
     * steering = 0: straight. steering = -100: full left. steering = +100: full right.
     * Uses the stored speed from SetMovementSpeed.
     *
     * @param steering steering value –100 to +100
     */
    @SimpleFunction(description =
        "Start moving with steering (-100 to +100, 0 = straight). "
        + "Uses the speed set by SetMovementSpeed.")
    public void StartMovingWithSteering(int steering) {
        if (!checkConnected()) return;
        steering = Math.max(-100, Math.min(100, steering));
        // %+d always emits a sign: +50 or -50 (Python int("+50")=50, int("-50")=-50)
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

    private static boolean isValidPort(String port) { return port.matches("[A-F]"); }

    private void reportError(String msg) {
        if (connectivity != null) connectivity.ErrorOccurred(msg);
    }
}
