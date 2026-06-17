package solaria.appinventor.spikeprime;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * TDD tests for CapabilityStore — cache for the SSP v0.6 capability declaration.
 *
 * All tests written BEFORE the implementation class exists.
 *
 * Uses a synthetic capability JSON matching the SPIKE Prime v0.6 example
 * from the implementation plan.
 */
public class CapabilityStoreTest {

    private CapabilityStore store;

    // Synthetic SPIKE Prime capability JSON (v0.6 §5 example)
    private static JSONObject buildCapability() {
        return new JSONObject()
            .put("type", "capability")
            .put("device", "spike-prime")
            .put("encodings", new JSONArray().put("json-utf8-newline"))
            .put("firmware", "3.4.0")
            .put("ssp_version", "0.6")
            .put("encodings", new JSONArray().put("json-utf8-newline"))
            .put("supports_batch", true)
            .put("system_metrics", new JSONArray()
                .put("battery").put("charging").put("temperature")
                .put("button.left").put("button.right").put("button.center"))
            .put("ports", new JSONArray()
                // Motor port A
                .put(new JSONObject()
                    .put("id", "A")
                    .put("type", "motor")
                    .put("features", new JSONArray().put("speed").put("position").put("stall"))
                    .put("constraints", new JSONObject()
                        .put("speed",    new JSONObject().put("type","int").put("min",-100).put("max",100))
                        .put("position", new JSONObject().put("type","int").put("min",0).put("max",359).put("wraps",true))
                    )
                )
                // Status LED
                .put(new JSONObject()
                    .put("id", "status")
                    .put("type", "led")
                    .put("features", new JSONArray().put("set"))
                    .put("constraints", new JSONObject()
                        .put("color", new JSONObject()
                            .put("type","enum")
                            .put("values", new JSONArray().put("red").put("orange").put("yellow").put("green").put("off"))
                        )
                    )
                )
                // Display (5x5)
                .put(new JSONObject()
                    .put("id", "display")
                    .put("type", "display")
                    .put("width", 5).put("height", 5).put("depth", "grayscale")
                    .put("features", new JSONArray().put("pixel").put("image").put("text").put("brightness").put("orientation"))
                )
                // IMU
                .put(new JSONObject()
                    .put("id", "imu")
                    .put("type", "orientation")
                    .put("features", new JSONArray().put("pitch").put("roll").put("yaw").put("gesture"))
                    .put("constraints", new JSONObject()
                        .put("gesture", new JSONObject()
                            .put("type","enum")
                            .put("values", new JSONArray().put("shake").put("tap").put("double_tap").put("fall").put("face_up").put("face_down"))
                        )
                    )
                )
                // Speaker
                .put(new JSONObject()
                    .put("id", "speaker")
                    .put("type", "speaker")
                    .put("features", new JSONArray().put("beep").put("builtin").put("volume"))
                    .put("builtin_sounds", new JSONArray().put("giggle").put("alert"))
                )
            );
    }

    @Before
    public void setUp() {
        store = new CapabilityStore();
        store.load(buildCapability());
    }

    // -----------------------------------------------------------------------
    // Top-level fields
    // -----------------------------------------------------------------------

    @Test
    public void getDeviceType_returnsDeviceField() {
        assertEquals("spike-prime", store.getDeviceType());
    }

    @Test
    public void getSspVersion_returnsVersionField() {
        assertEquals("0.6", store.getSspVersion());
    }

    @Test
    public void supportsBatch_returnsTrueWhenSet() {
        assertTrue(store.supportsBatch());
    }

    @Test
    public void supportsBatch_returnsFalseWhenAbsent() {
        CapabilityStore empty = new CapabilityStore();
        empty.load(new JSONObject().put("type","capability").put("device","x").put("ssp_version","0.6").put("ports",new JSONArray()));
        assertFalse(empty.supportsBatch());
    }

    @Test
    public void getSystemMetrics_returnsAllDeclaredMetrics() {
        assertTrue(store.getSystemMetrics().contains("battery"));
        assertTrue(store.getSystemMetrics().contains("button.left"));
        assertEquals(6, store.getSystemMetrics().size());
    }

    // -----------------------------------------------------------------------
    // Port presence & type
    // -----------------------------------------------------------------------

    @Test
    public void hasPort_returnsTrueForDeclaredPort() {
        assertTrue(store.hasPort("A"));
        assertTrue(store.hasPort("status"));
        assertTrue(store.hasPort("display"));
        assertTrue(store.hasPort("imu"));
        assertTrue(store.hasPort("speaker"));
    }

    @Test
    public void hasPort_returnsFalseForUndeclaredPort() {
        assertFalse(store.hasPort("B"));
        assertFalse(store.hasPort("unknown"));
    }

    @Test
    public void getPortType_returnsTypeString() {
        assertEquals("motor",       store.getPortType("A"));
        assertEquals("led",         store.getPortType("status"));
        assertEquals("display",     store.getPortType("display"));
        assertEquals("orientation", store.getPortType("imu"));
        assertEquals("speaker",     store.getPortType("speaker"));
    }

    @Test
    public void getPortType_returnsNullForUnknownPort() {
        assertNull(store.getPortType("Z"));
    }

    // -----------------------------------------------------------------------
    // Feature checking
    // -----------------------------------------------------------------------

    @Test
    public void hasFeature_returnsTrueForDeclaredFeature() {
        assertTrue(store.hasFeature("A", "speed"));
        assertTrue(store.hasFeature("A", "position"));
        assertTrue(store.hasFeature("A", "stall"));
        assertTrue(store.hasFeature("display", "pixel"));
        assertTrue(store.hasFeature("imu", "gesture"));
        assertTrue(store.hasFeature("speaker", "volume"));
    }

    @Test
    public void hasFeature_returnsFalseForUndeclaredFeature() {
        assertFalse(store.hasFeature("A", "volume"));        // volume not on motor
        assertFalse(store.hasFeature("speaker", "position")); // position not on speaker
        assertFalse(store.hasFeature("B", "speed"));          // port B not declared
    }

    // -----------------------------------------------------------------------
    // Constraint queries — int type
    // -----------------------------------------------------------------------

    @Test
    public void getConstraintType_returnsIntForSpeedOnMotor() {
        assertEquals("int", store.getConstraintType("A", "speed"));
    }

    @Test
    public void getConstraintMin_returnsMinusHundredForMotorSpeed() {
        assertEquals(-100, store.getConstraintMin("A", "speed"));
    }

    @Test
    public void getConstraintMax_returnsPlusHundredForMotorSpeed() {
        assertEquals(100, store.getConstraintMax("A", "speed"));
    }

    @Test
    public void getConstraintWraps_returnsTrueForMotorPosition() {
        assertTrue(store.getConstraintWraps("A", "position"));
    }

    @Test
    public void getConstraintWraps_returnsFalseWhenAbsent() {
        assertFalse(store.getConstraintWraps("A", "speed"));
    }

    // -----------------------------------------------------------------------
    // Constraint queries — enum type
    // -----------------------------------------------------------------------

    @Test
    public void getConstraintType_returnsEnumForStatusLedColor() {
        assertEquals("enum", store.getConstraintType("status", "color"));
    }

    @Test
    public void getConstraintEnumValues_returnsAllValues() {
        java.util.List<String> values = store.getConstraintEnumValues("status", "color");
        assertNotNull(values);
        assertTrue(values.contains("red"));
        assertTrue(values.contains("off"));
        assertEquals(5, values.size());
    }

    @Test
    public void getConstraintEnumValues_returnsGestureVocabularyForImu() {
        java.util.List<String> values = store.getConstraintEnumValues("imu", "gesture");
        assertNotNull(values);
        assertTrue(values.contains("shake"));
        assertTrue(values.contains("face_down"));
        assertEquals(6, values.size());
    }

    // -----------------------------------------------------------------------
    // Constraint queries — absent constraints
    // -----------------------------------------------------------------------

    @Test
    public void getConstraintType_returnsNullForUnconstrained() {
        assertNull(store.getConstraintType("A", "stall"));     // stall has no constraint
        assertNull(store.getConstraintType("display", "pixel")); // implicit from dimensions
    }

    @Test
    public void getConstraintEnumValues_returnsNullForNonEnum() {
        assertNull(store.getConstraintEnumValues("A", "speed"));  // speed is int, not enum
    }

    // -----------------------------------------------------------------------
    // Display port — implicit dimension constraints
    // -----------------------------------------------------------------------

    @Test
    public void getDisplayWidth_returnsWidth() {
        assertEquals(5, store.getDisplayWidth("display"));
    }

    @Test
    public void getDisplayHeight_returnsHeight() {
        assertEquals(5, store.getDisplayHeight("display"));
    }

    @Test
    public void getDisplayDepth_returnsDepthString() {
        assertEquals("grayscale", store.getDisplayDepth("display"));
    }

    @Test
    public void getDisplayWidth_returnsMinusOneForNonDisplayPort() {
        assertEquals(-1, store.getDisplayWidth("A"));
    }

    // -----------------------------------------------------------------------
    // Speaker — builtin_sounds list
    // -----------------------------------------------------------------------

    @Test
    public void getBuiltinSounds_returnsListForSpeaker() {
        java.util.List<String> sounds = store.getBuiltinSounds("speaker");
        assertNotNull(sounds);
        assertTrue(sounds.contains("giggle"));
        assertTrue(sounds.contains("alert"));
        assertEquals(2, sounds.size());
    }

    @Test
    public void getBuiltinSounds_returnsNullForNonSpeaker() {
        assertNull(store.getBuiltinSounds("A"));
    }

    // -----------------------------------------------------------------------
    // Unloaded store
    // -----------------------------------------------------------------------

    @Test
    public void unloadedStore_hasPortReturnsFalse() {
        assertFalse(new CapabilityStore().hasPort("A"));
    }

    @Test
    public void unloadedStore_getDeviceTypeReturnsNull() {
        assertNull(new CapabilityStore().getDeviceType());
    }

    // -----------------------------------------------------------------------
    // getPortIds() — used by GetAvailablePorts() block
    // -----------------------------------------------------------------------

    @Test
    public void getPortIds_returnsAllDeclaredPortIds() {
        java.util.List<String> ids = store.getPortIds();
        assertNotNull(ids);
        assertTrue(ids.contains("A"));
        assertTrue(ids.contains("display"));
        assertTrue(ids.contains("status"));
        assertTrue(ids.contains("imu"));
        assertTrue(ids.contains("speaker"));
        assertEquals(5, ids.size());
    }

    @Test
    public void getPortIds_returnsEmptyListWhenUnloaded() {
        assertTrue(new CapabilityStore().getPortIds().isEmpty());
    }

    @Test
    public void getPortIds_returnsEmptyAfterClear() {
        store.clear();
        assertTrue(store.getPortIds().isEmpty());
    }

    // -----------------------------------------------------------------------
    // getEncodings() — used by GetSupportedEncodings() block
    // -----------------------------------------------------------------------

    @Test
    public void getEncodings_returnsDeclaredEncodings() {
        java.util.List<String> enc = store.getEncodings();
        assertNotNull(enc);
        assertTrue(enc.contains("json-utf8-newline"));
        assertEquals(1, enc.size());
    }

    @Test
    public void getEncodings_returnsEmptyListWhenUnloaded() {
        assertTrue(new CapabilityStore().getEncodings().isEmpty());
    }
}
