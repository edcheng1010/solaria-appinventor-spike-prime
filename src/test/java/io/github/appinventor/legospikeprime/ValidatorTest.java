package io.github.appinventor.legospikeprime;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * TDD tests for Validator — pre-send SSP message constraint checker.
 *
 * All tests written BEFORE the implementation class exists.
 *
 * Validator consults a CapabilityStore and validates SSPMessage params.
 * On violation it fires a ValidationError callback (code 2xx per SSP §7)
 * and returns false. On valid message it returns true and does not fire.
 */
public class ValidatorTest {

    private CapabilityStore store;
    private final java.util.List<String> errors = new java.util.ArrayList<>();

    /** Captures violation messages for assertion. */
    private final Validator.ErrorCallback capture = (code, message) -> errors.add(code + ":" + message);

    @Before
    public void setUp() {
        store = new CapabilityStore();
        store.load(new JSONObject()
            .put("type", "capability")
            .put("device", "spike-prime")
            .put("ssp_version", "0.6")
            .put("ports", new JSONArray()
                .put(new JSONObject()
                    .put("id", "A").put("type", "motor")
                    .put("features", new JSONArray().put("speed").put("position"))
                    .put("constraints", new JSONObject()
                        .put("speed",    new JSONObject().put("type","int").put("min",-100).put("max",100))
                        .put("position", new JSONObject().put("type","int").put("min",0).put("max",359).put("wraps",true))
                    )
                )
                .put(new JSONObject()
                    .put("id", "status").put("type", "led")
                    .put("features", new JSONArray().put("set"))
                    .put("constraints", new JSONObject()
                        .put("color", new JSONObject()
                            .put("type","enum")
                            .put("values", new JSONArray().put("red").put("green").put("off"))
                        )
                    )
                )
                .put(new JSONObject()
                    .put("id", "display").put("type", "display")
                    .put("width", 5).put("height", 5).put("depth", "grayscale")
                    .put("features", new JSONArray().put("pixel").put("brightness"))
                )
            )
        );
        errors.clear();
    }

    private Validator validator() {
        return new Validator(store, capture);
    }

    // -----------------------------------------------------------------------
    // Unknown port
    // -----------------------------------------------------------------------

    @Test
    public void validate_unknownPort_returnsFalseAndFiresError() {
        SSPMessage msg = new SSPMessage("motor.run").withPort("Z").withParam("speed", 50);
        assertFalse(validator().validate(msg));
        assertFalse("error must have been fired", errors.isEmpty());
    }

    // -----------------------------------------------------------------------
    // Int constraint — in-range passes
    // -----------------------------------------------------------------------

    @Test
    public void validate_motorRunSpeedInRange_returnsTrue() {
        SSPMessage msg = new SSPMessage("motor.run").withPort("A").withParam("speed", 75);
        assertTrue(validator().validate(msg));
        assertTrue(errors.isEmpty());
    }

    @Test
    public void validate_motorRunSpeedAtMinBoundary_returnsTrue() {
        assertTrue(validator().validate(
            new SSPMessage("motor.run").withPort("A").withParam("speed", -100)));
        assertTrue(errors.isEmpty());
    }

    @Test
    public void validate_motorRunSpeedAtMaxBoundary_returnsTrue() {
        assertTrue(validator().validate(
            new SSPMessage("motor.run").withPort("A").withParam("speed", 100)));
        assertTrue(errors.isEmpty());
    }

    // -----------------------------------------------------------------------
    // Int constraint — out-of-range fails
    // -----------------------------------------------------------------------

    @Test
    public void validate_motorRunSpeedAboveMax_returnsFalseAndFiresError() {
        SSPMessage msg = new SSPMessage("motor.run").withPort("A").withParam("speed", 200);
        assertFalse(validator().validate(msg));
        assertFalse(errors.isEmpty());
        assertTrue("error code must be 2xx", errors.get(0).startsWith("2"));
    }

    @Test
    public void validate_motorRunSpeedBelowMin_returnsFalseAndFiresError() {
        assertFalse(validator().validate(
            new SSPMessage("motor.run").withPort("A").withParam("speed", -150)));
        assertFalse(errors.isEmpty());
    }

    // -----------------------------------------------------------------------
    // Wrapping constraint — any value allowed (no violation)
    // -----------------------------------------------------------------------

    @Test
    public void validate_positionWithWrappingConstraint_anValueInRangeIsOk() {
        assertTrue(validator().validate(
            new SSPMessage("motor.goto").withPort("A").withParam("position", 180)));
        assertTrue(errors.isEmpty());
    }

    @Test
    public void validate_positionOutOfRange_returnsFalse() {
        assertFalse(validator().validate(
            new SSPMessage("motor.goto").withPort("A").withParam("position", 400)));
        assertFalse(errors.isEmpty());
    }

    // -----------------------------------------------------------------------
    // Enum constraint — valid value passes
    // -----------------------------------------------------------------------

    @Test
    public void validate_ledSetValidColor_returnsTrue() {
        assertTrue(validator().validate(
            new SSPMessage("led.set").withPort("status").withParam("color", "red")));
        assertTrue(errors.isEmpty());
    }

    @Test
    public void validate_ledSetInvalidColor_returnsFalseAndFiresError() {
        assertFalse(validator().validate(
            new SSPMessage("led.set").withPort("status").withParam("color", "purple")));
        assertFalse(errors.isEmpty());
        assertTrue("error code must be 2xx", errors.get(0).startsWith("2"));
    }

    // -----------------------------------------------------------------------
    // Unconstrained params — pass through
    // -----------------------------------------------------------------------

    @Test
    public void validate_paramWithNoConstraintDefined_returnsTrue() {
        // "brightness" on display has no explicit constraint (implicit from depth)
        assertTrue(validator().validate(
            new SSPMessage("led.matrix.brightness").withPort("display").withParam("level", 50)));
        assertTrue(errors.isEmpty());
    }

    // -----------------------------------------------------------------------
    // No port field — some commands don't target a port (system.ping, batch)
    // -----------------------------------------------------------------------

    @Test
    public void validate_commandWithoutPort_returnsTrue() {
        assertTrue(validator().validate(new SSPMessage("system.ping")));
        assertTrue(errors.isEmpty());
    }

    // -----------------------------------------------------------------------
    // Rate-limit: same (port, param) error fires at most once per second
    // -----------------------------------------------------------------------

    @Test
    public void validate_repeatedViolationWithinOneSecond_firesOnlyOnce() {
        Validator v = validator();
        SSPMessage bad = new SSPMessage("motor.run").withPort("A").withParam("speed", 999);
        v.validate(bad);
        v.validate(bad);
        v.validate(bad);
        assertEquals("rate-limited to 1 error per (port,param) per second", 1, errors.size());
    }

    @Test
    public void validate_differentParamViolations_bothFire() {
        Validator v = validator();
        v.validate(new SSPMessage("motor.run").withPort("A").withParam("speed", 999));
        v.validate(new SSPMessage("motor.goto").withPort("A").withParam("position", 999));
        assertEquals("two different violations fire independently", 2, errors.size());
    }
}
