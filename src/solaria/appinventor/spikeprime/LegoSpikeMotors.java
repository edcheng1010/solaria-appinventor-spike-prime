package solaria.appinventor.spikeprime;

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
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;

import org.json.JSONObject;

@SimpleObject(external = true)
@DesignerComponent(version = 4,
    description = "Controls an individual motor on a LEGO SPIKE Prime hub. "
        + "Set Port (A-F) and Direction, then call StartMotor or StopMotor. "
        + "Set the Connectivity property to a LegoSpikeConnectivity component.",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = "aiwebres/legospike.png")
public class LegoSpikeMotors extends AndroidNonvisibleComponent
        implements LegoSpikeConnectivity.HubDataListener {

    private LegoSpikeConnectivity connectivity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String port       = "A";
    private String direction  = "Clockwise";
    private int    speed      = 50;
    private int    power      = 50;
    private String motorMode  = "speed"; // "speed" or "power"
    private String stopAction = "brake";

    public LegoSpikeMotors(ComponentContainer container) {
        super(container.$form());
    }

    // =========================================================================
    // Connectivity property
    // =========================================================================
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
        description = "The LegoSpikeConnectivity component managing the hub connection")
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COMPONENT
        + ":solaria.appinventor.spikeprime.LegoSpikeConnectivity")
    public void Connectivity(Component component) {
        if (this.connectivity != null) this.connectivity.removeDataListener(this);
        if (component instanceof LegoSpikeConnectivity) {
            this.connectivity = (LegoSpikeConnectivity) component;
            this.connectivity.addDataListener(this);
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
        "Start the motor using the last SetMotorSpeed or SetMotorPower configuration. "
        + "Set Port and Direction in the Designer or via blocks first.")
    public void StartMotor() {
        if (!checkConnected()) return;
        if ("power".equals(motorMode)) {
            int effectivePower = "counterclockwise".equalsIgnoreCase(direction) ? -power : power;
            connectivity.sendSSP(new SSPMessage("motor.run")
                .withPort(port)
                .withParam("speed", effectivePower)
                .withParam("mode", "power"));
        } else {
            int effectiveSpeed = "counterclockwise".equalsIgnoreCase(direction) ? -speed : speed;
            connectivity.sendSSP(new SSPMessage("motor.run")
                .withPort(port)
                .withParam("speed", effectiveSpeed));
        }
    }

    @SimpleFunction(description =
        "Stop the motor using the stop action configured by SetMotorBrakeAtStop (default: Brake).")
    public void StopMotor() {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("motor.stop")
            .withPort(port).withParam("stop_action", stopAction));
    }

    @SimpleFunction(description =
        "Set the stop action used by StopMotor. "
        + "Brake (default): electrical braking. Coast: free-spin to stop. "
        + "Hold: actively locks position — note: Hold may not resist manual rotation on all firmware versions.")
    public void SetMotorBrakeAtStop(@Options(StopAction.class) String action) {
        StopAction sa = StopAction.fromUnderlyingValue(action);
        stopAction = sa != null ? sa.toUnderlyingValue() : "brake";
    }

    @SimpleFunction(description =
        "Set the motor speed (0–100) and switch to speed mode. Applied on the next StartMotor call.")
    public void SetMotorSpeed(int value) {
        speed = Math.max(0, Math.min(100, value));
        motorMode = "speed";
    }

    @SimpleFunction(description =
        "Set the motor power (0–100, duty cycle) and switch to power mode. Applied on the next StartMotor call. "
        + "Unlike speed mode, the motor does not compensate for load — it slows down under resistance.")
    public void SetMotorPower(int value) {
        power = Math.max(0, Math.min(100, value));
        motorMode = "power";
    }

    // =========================================================================
    // Phase 3 expansion blocks
    // =========================================================================

    @SimpleFunction(description =
        "Run the motor for a specific amount. "
        + "Use the DurationUnit constant blocks (Milliseconds, Degrees, Rotations).")
    public void RunMotorForDuration(int amount, @Options(DurationUnit.class) String unit) {
        if (!checkConnected()) return;
        int effectiveSpeed = "counterclockwise".equalsIgnoreCase(direction) ? -speed : speed;
        connectivity.sendSSP(new SSPMessage("motor.run")
            .withPort(port)
            .withParam("speed", effectiveSpeed)
            .withParam("duration", amount)
            .withParam("duration_unit", normaliseUnit(unit)));
    }

    /** Accepts either the SSP wire value ("ms") or the enum label ("Milliseconds"). */
    private static String normaliseUnit(String unit) {
        if (unit == null) return "ms";
        DurationUnit du = DurationUnit.fromUnderlyingValue(unit);
        return du != null ? du.toUnderlyingValue() : unit;
    }

    @SimpleFunction(description =
        "Move the motor to an absolute position (0–359 degrees).")
    public void GoToMotorAbsolutePosition(int position) {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("motor.goto")
            .withPort(port)
            .withParam("position", Math.max(0, Math.min(359, position)))
            .withParam("speed", speed)
            .withParam("mode", "absolute"));
    }

    @SimpleFunction(description =
        "Move the motor forward or backward by a relative number of degrees.")
    public void GoToMotorRelativePosition(int degrees) {
        if (!checkConnected()) return;
        int d = "counterclockwise".equalsIgnoreCase(direction) ? -degrees : degrees;
        connectivity.sendSSP(new SSPMessage("motor.goto")
            .withPort(port)
            .withParam("position", d)
            .withParam("speed", speed)
            .withParam("mode", "relative"));
    }

    @SimpleFunction(description =
        "Reset the relative position counter that GetMotorRelativePosition reports — like a trip odometer. "
        + "No physical movement. Does NOT affect GoToRelativeMotorPosition (which always moves "
        + "relative to where the motor currently is, regardless of this counter).")
    public void ResetRelativeMotorPosition() {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("motor.reset").withPort(port));
    }

    @SimpleFunction(description =
        "Request the cumulative motor position since last ResetRelativeMotorPosition "
        + "(can exceed 360 or go negative — useful for tracking distance traveled). "
        + "Async — wire MotorRelativePositionRead(port, degrees) to receive the value.")
    public void GetMotorRelativePosition() {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("sensor.read")
            .withPort(port).withParam("type", "position"));
    }

    @SimpleFunction(description =
        "Request the current motor orientation as an angle in 0–359 degrees "
        + "(useful for knowing which way a steering wheel or arm joint is pointing). "
        + "Async — wire MotorAbsolutePositionRead(port, degrees) to receive the value.")
    public void GetMotorAbsolutePosition() {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("sensor.read")
            .withPort(port).withParam("type", "absolute_position"));
    }

    @SimpleFunction(description =
        "Request the current motor speed. This is asynchronous — wire the "
        + "MotorSpeedRead(port, speed) event to receive the value when the hub responds.")
    public void GetMotorSpeed() {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("sensor.read")
            .withPort(port).withParam("type", "speed"));
    }

    @SimpleFunction(description =
        "Set the acceleration ramp rate (ms to reach full speed, 0–10000). "
        + "Applies to RunMotorForDuration and, if the hub firmware supports it, "
        + "to StartMotor as well. Effect is most visible at higher speeds.")
    public void SetMotorAcceleration(int rate) {
        if (!checkConnected()) return;
        connectivity.sendSSP(new SSPMessage("motor.set_acceleration")
            .withPort(port)
            .withParam("rate", Math.max(0, Math.min(10000, rate))));
    }

    // =========================================================================
    // Events
    // =========================================================================

    @SimpleEvent(description =
        "Fired when the hub responds to GetMotorRelativePosition. "
        + "degrees: cumulative position since last reset (can be > 360 or negative).")
    public void MotorRelativePositionRead(String port, int degrees) {
        EventDispatcher.dispatchEvent(this, "MotorRelativePositionRead", port, degrees);
    }

    @SimpleEvent(description =
        "Fired when the hub responds to GetMotorAbsolutePosition. "
        + "degrees: current motor orientation, 0–359.")
    public void MotorAbsolutePositionRead(String port, int degrees) {
        EventDispatcher.dispatchEvent(this, "MotorAbsolutePositionRead", port, degrees);
    }

    @SimpleEvent(description =
        "Fired when the hub responds to GetMotorSpeed. speed: current speed percent.")
    public void MotorSpeedRead(String port, int speed) {
        EventDispatcher.dispatchEvent(this, "MotorSpeedRead", port, speed);
    }

    // =========================================================================
    // HubDataListener — parse SSP sensor events for motor ports
    // =========================================================================
    @Override
    public void onHubData(String data) {
        if (data == null || !data.startsWith("{")) return;
        try {
            JSONObject obj = new JSONObject(data);
            if (!"sensor".equals(obj.optString("event"))) return;
            final String p    = obj.optString("port");
            final String type = obj.optString("type");
            final Object val  = obj.opt("value");
            if (val == null) return;

            switch (type) {
                case "position": {
                    final int degrees = ((Number) val).intValue();
                    mainHandler.post(() -> MotorRelativePositionRead(p, degrees));
                    break;
                }
                case "absolute_position": {
                    final int degrees = ((Number) val).intValue();
                    mainHandler.post(() -> MotorAbsolutePositionRead(p, degrees));
                    break;
                }
                case "speed": {
                    final int spd = ((Number) val).intValue();
                    mainHandler.post(() -> MotorSpeedRead(p, spd));
                    break;
                }
                default:
                    break;
            }
        } catch (Exception ignored) {}
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
