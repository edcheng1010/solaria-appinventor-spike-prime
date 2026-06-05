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

/**
 * LegoSpikeMovement — synchronized two-motor drivebase control.
 * Matches LEGO SPIKE Prime "Movement Blocks" category.
 *
 * Configure LeftPort, RightPort, Direction, and Speed in the Designer or
 * via blocks, then call SetMovementMotors, StartMoving, StopMoving, etc.
 * One LegoSpikeMovement instance controls one drivebase pair.
 */
@SimpleObject(external = true)
@DesignerComponent(version = 6,
    description = "Controls a two-motor drivebase on a LEGO SPIKE Prime hub. "
        + "Set LeftPort, RightPort, and Direction, then call SetMovementMotors and StartMoving. "
        + "Set the Connectivity property to a LegoSpikeConnectivity component.",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = "aiwebres/legospike.png")
public class LegoSpikeMovement extends AndroidNonvisibleComponent {

    private LegoSpikeConnectivity connectivity;

    private String leftPort        = "A";
    private String rightPort       = "B";
    private String direction       = "Forward";
    private int    movementSpeed   = 50;
    private long   lastMoveSentMs  = 0;
    private double cmPerRotation   = 17.6; // default: ~56mm wheel circumference
    private String stopAction      = "brake";
    private static final long MOVE_THROTTLE_MS = 50; // 20Hz cap to prevent BLE flood

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
     * Set the speed used by StartMoving and StartMovingWithSteering.
     * Stored locally — no command is sent to the hub.
     *
     * @param speed 0–100 percent
     */
    @SimpleFunction(description =
        "Set the left and right motor ports for this drivebase in one call. "
        + "Equivalent to setting LeftPort and RightPort separately.")
    public void SetMovementPair(@Options(Port.class) String leftMotorPort,
                                @Options(Port.class) String rightMotorPort) {
        Port l = Port.fromUnderlyingValue(leftMotorPort);
        Port r = Port.fromUnderlyingValue(rightMotorPort);
        if (l != null) leftPort = l.toUnderlyingValue();
        if (r != null) rightPort = r.toUnderlyingValue();
        // Send configure command immediately so hub re-pairs without waiting for next move.
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("movement.configure")
            .withParam("left", leftPort).withParam("right", rightPort));
    }

    @SimpleFunction(description =
        "Set the movement speed (0–100). Applied on the next StartMoving or "
        + "StartMovingWithSteering call.")
    public void SetMovementSpeed(int speed) {
        movementSpeed = Math.max(0, Math.min(100, speed));
    }

    /**
     * Start moving the drivebase using the configured Direction and speed.
     * Automatically pairs LeftPort and RightPort before moving.
     */
    @SimpleFunction(description =
        "Start moving the drivebase using the configured Direction and speed.")
    public void StartMoving() {
        if (!checkConnected()) return;
        long now = System.currentTimeMillis();
        if (now - lastMoveSentMs < MOVE_THROTTLE_MS) return;
        lastMoveSentMs = now;
        int effectiveSpeed = direction.equalsIgnoreCase("backward") ? -movementSpeed : movementSpeed;
        connectivity.sendSSP(
            new SSPMessage("movement.drive")
                .withParam("left", leftPort)
                .withParam("right", rightPort)
                .withParam("speed", effectiveSpeed)
                .withParam("steering", 0));
    }

    @SimpleFunction(description =
        "Start moving with steering (–100 to +100, 0 = straight).")
    public void StartMovingWithSteering(int steering) {
        if (!checkConnected()) return;
        long now = System.currentTimeMillis();
        if (now - lastMoveSentMs < MOVE_THROTTLE_MS) return;
        lastMoveSentMs = now;
        steering = Math.max(-100, Math.min(100, steering));
        int effectiveSpeed = direction.equalsIgnoreCase("backward") ? -movementSpeed : movementSpeed;
        connectivity.sendSSP(
            new SSPMessage("movement.drive")
                .withParam("left", leftPort)
                .withParam("right", rightPort)
                .withParam("speed", effectiveSpeed)
                .withParam("steering", steering));
    }

    /** Stop the drivebase immediately. */
    @SimpleFunction(description = "Stop the drivebase immediately")
    public void StopMoving() {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("movement.stop")
            .withParam("stop_action", stopAction));
    }

    // =========================================================================
    // Phase 3 expansion blocks
    // =========================================================================

    @SimpleFunction(description =
        "Move the drivebase for a specific amount. "
        + "Use the DurationUnit constant blocks (Milliseconds, Degrees, Rotations).")
    public void MoveForDuration(int amount, @Options(DurationUnit.class) String unit) {
        if (!checkConnected()) return;
        int effectiveSpeed = direction.equalsIgnoreCase("backward") ? -movementSpeed : movementSpeed;
        connectivity.sendSSP(new SSPMessage("movement.drive")
            .withParam("left", leftPort).withParam("right", rightPort)
            .withParam("speed", effectiveSpeed).withParam("steering", 0)
            .withParam("duration", amount).withParam("duration_unit", normaliseUnit(unit)));
    }

    @SimpleFunction(description =
        "Move with steering for a specific amount. steering: –100 to +100. "
        + "Use the DurationUnit constant blocks for the unit.")
    public void MoveWithSteeringForDuration(int steering, int amount,
                                             @Options(DurationUnit.class) String unit) {
        if (!checkConnected()) return;
        int effectiveSpeed = direction.equalsIgnoreCase("backward") ? -movementSpeed : movementSpeed;
        connectivity.sendSSP(new SSPMessage("movement.drive")
            .withParam("left", leftPort).withParam("right", rightPort)
            .withParam("speed", effectiveSpeed)
            .withParam("steering", Math.max(-100, Math.min(100, steering)))
            .withParam("duration", amount).withParam("duration_unit", normaliseUnit(unit)));
    }

    /** Accepts either the SSP wire value ("ms") or the enum label ("Milliseconds"). */
    private static String normaliseUnit(String unit) {
        if (unit == null) return "ms";
        DurationUnit du = DurationUnit.fromUnderlyingValue(unit);
        return du != null ? du.toUnderlyingValue() : unit;
    }

    @SimpleFunction(description =
        "Tank drive: control left and right wheels independently. "
        + "leftSpeed, rightSpeed: –100 to +100.")
    public void StartMovingAtSpeed(int leftSpeed, int rightSpeed) {
        if (!checkConnected()) return;
        long now = System.currentTimeMillis();
        if (now - lastMoveSentMs < MOVE_THROTTLE_MS) return;
        lastMoveSentMs = now;
        connectivity.sendSSP(new SSPMessage("movement.drive")
            .withParam("left", leftPort).withParam("right", rightPort)
            .withParam("left_speed",  Math.max(-100, Math.min(100, leftSpeed)))
            .withParam("right_speed", Math.max(-100, Math.min(100, rightSpeed))));
    }

    @SimpleFunction(description =
        "Set cm per full wheel rotation. Used by MoveForDistance to convert centimetres to "
        + "motor degrees. Default: 17.6 cm (≈56 mm wheel circumference). Adjust to match "
        + "your actual wheels.")
    public void SetMovementRotationDistance(double cmPerRotation) {
        this.cmPerRotation = Math.max(0.1, cmPerRotation);
    }

    @SimpleFunction(description =
        "Move the drivebase a specific distance in centimetres. "
        + "Converts cm → degrees client-side using the SetMovementRotationDistance value "
        + "(default 17.6 cm/rotation). Uses the configured Direction and Speed. "
        + "Matches the LEGO SPIKE 'move [N] cm' block.")
    public void MoveForDistance(double cm) {
        if (!checkConnected()) return;
        int effectiveSpeed = direction.equalsIgnoreCase("backward") ? -movementSpeed : movementSpeed;
        int degrees = (int) Math.round((cm / cmPerRotation) * 360.0);
        connectivity.sendSSP(new SSPMessage("movement.drive")
            .withParam("left", leftPort).withParam("right", rightPort)
            .withParam("speed", effectiveSpeed).withParam("steering", 0)
            .withParam("duration", degrees).withParam("duration_unit", "degrees"));
    }

    @SimpleFunction(description =
        "Move the drivebase a specific distance (cm) with steering (−100 to +100). "
        + "Converts cm to degrees using SetMovementRotationDistance (default 17.6 cm/rotation). "
        + "steering: −100 = full left, 0 = straight, +100 = full right. "
        + "Matches Scratch's moveForCm block which includes a steering parameter.")
    public void MoveWithSteeringForDistance(int steering, double cm) {
        if (!checkConnected()) return;
        int effectiveSpeed = direction.equalsIgnoreCase("backward") ? -movementSpeed : movementSpeed;
        int clampedSteering = Math.max(-100, Math.min(100, steering));
        int degrees = (int) Math.round((cm / cmPerRotation) * 360.0);
        connectivity.sendSSP(new SSPMessage("movement.drive")
            .withParam("left", leftPort).withParam("right", rightPort)
            .withParam("speed", effectiveSpeed).withParam("steering", clampedSteering)
            .withParam("duration", degrees).withParam("duration_unit", "degrees"));
    }

    @SimpleFunction(description =
        "Set the stop action applied by StopMoving. "
        + "Brake (default): electrical braking. Coast: free-spin to stop. "
        + "Hold: actively locks position — note: Hold may not resist manual rotation on all firmware versions.")
    public void SetMovementBrakeAtStop(@Options(StopAction.class) String mode) {
        StopAction sa = StopAction.fromUnderlyingValue(mode);
        this.stopAction = sa != null ? sa.toUnderlyingValue() : "brake";
    }

    @SimpleFunction(description =
        "Set the acceleration ramp rate for the drive base in milliseconds (0–10000).")
    public void SetMovementAcceleration(int rate) {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("movement.set_acceleration")
            .withParam("rate", Math.max(0, Math.min(10000, rate))));
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
