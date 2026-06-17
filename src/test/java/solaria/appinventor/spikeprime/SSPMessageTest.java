package solaria.appinventor.spikeprime;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;

/**
 * TDD tests for SSPMessage — the SSP v0.6 JSON command builder.
 *
 * All tests written BEFORE the implementation class exists.
 */
public class SSPMessageTest {

    // -----------------------------------------------------------------------
    // serialise() — produces UTF-8 JSON terminated by newline
    // -----------------------------------------------------------------------

    @Test
    public void serialise_producesJsonTerminatedByNewline() {
        byte[] bytes = new SSPMessage("motor.run").serialise();
        String text = new String(bytes, StandardCharsets.UTF_8);
        assertTrue("must end with newline", text.endsWith("\n"));
    }

    @Test
    public void serialise_isValidJson() {
        byte[] bytes = new SSPMessage("motor.run").serialise();
        String text = new String(bytes, StandardCharsets.UTF_8).trim();
        // Should not throw
        new JSONObject(text);
    }

    @Test
    public void serialise_containsCmdField() {
        byte[] bytes = new SSPMessage("motor.run").serialise();
        JSONObject obj = new JSONObject(new String(bytes, StandardCharsets.UTF_8).trim());
        assertEquals("motor.run", obj.getString("cmd"));
    }

    // -----------------------------------------------------------------------
    // withPort() — adds "port" field
    // -----------------------------------------------------------------------

    @Test
    public void withPort_addsPortField() {
        byte[] bytes = new SSPMessage("motor.run").withPort("A").serialise();
        JSONObject obj = new JSONObject(new String(bytes, StandardCharsets.UTF_8).trim());
        assertEquals("A", obj.getString("port"));
    }

    @Test
    public void withPort_returnsThisForChaining() {
        SSPMessage msg = new SSPMessage("motor.run");
        assertSame(msg, msg.withPort("A"));
    }

    // -----------------------------------------------------------------------
    // withRequestId() — adds optional "request_id" field
    // -----------------------------------------------------------------------

    @Test
    public void withRequestId_addsRequestIdField() {
        byte[] bytes = new SSPMessage("motor.run").withRequestId("req-1").serialise();
        JSONObject obj = new JSONObject(new String(bytes, StandardCharsets.UTF_8).trim());
        assertEquals("req-1", obj.getString("request_id"));
    }

    @Test
    public void withoutRequestId_fieldIsAbsent() {
        byte[] bytes = new SSPMessage("motor.run").serialise();
        JSONObject obj = new JSONObject(new String(bytes, StandardCharsets.UTF_8).trim());
        assertFalse("request_id must be absent when not set", obj.has("request_id"));
    }

    // -----------------------------------------------------------------------
    // withParam() — adds arbitrary typed fields
    // -----------------------------------------------------------------------

    @Test
    public void withParam_intValue_addsField() {
        byte[] bytes = new SSPMessage("motor.run").withParam("speed", 75).serialise();
        JSONObject obj = new JSONObject(new String(bytes, StandardCharsets.UTF_8).trim());
        assertEquals(75, obj.getInt("speed"));
    }

    @Test
    public void withParam_stringValue_addsField() {
        byte[] bytes = new SSPMessage("led.set").withParam("color", "red").serialise();
        JSONObject obj = new JSONObject(new String(bytes, StandardCharsets.UTF_8).trim());
        assertEquals("red", obj.getString("color"));
    }

    @Test
    public void withParam_booleanValue_addsField() {
        byte[] bytes = new SSPMessage("motor.stop").withParam("brake", true).serialise();
        JSONObject obj = new JSONObject(new String(bytes, StandardCharsets.UTF_8).trim());
        assertTrue(obj.getBoolean("brake"));
    }

    @Test
    public void withParam_returnsThisForChaining() {
        SSPMessage msg = new SSPMessage("motor.run");
        assertSame(msg, msg.withParam("speed", 75));
    }

    // -----------------------------------------------------------------------
    // Full command roundtrip — motor.run with all fields
    // -----------------------------------------------------------------------

    @Test
    public void motorRunCommand_allFieldsPresent() {
        byte[] bytes = new SSPMessage("motor.run")
                .withPort("A")
                .withParam("speed", 75)
                .withRequestId("req-1")
                .serialise();
        JSONObject obj = new JSONObject(new String(bytes, StandardCharsets.UTF_8).trim());
        assertEquals("motor.run", obj.getString("cmd"));
        assertEquals("A",         obj.getString("port"));
        assertEquals(75,          obj.getInt("speed"));
        assertEquals("req-1",     obj.getString("request_id"));
    }

    @Test
    public void sensorSubscribeCommand_allFieldsPresent() {
        byte[] bytes = new SSPMessage("sensor.subscribe")
                .withPort("C")
                .withParam("mode", "on_change")
                .withParam("interval", 100)
                .withParam("min_change", 5)
                .serialise();
        JSONObject obj = new JSONObject(new String(bytes, StandardCharsets.UTF_8).trim());
        assertEquals("sensor.subscribe", obj.getString("cmd"));
        assertEquals("C",               obj.getString("port"));
        assertEquals("on_change",        obj.getString("mode"));
        assertEquals(100,                obj.getInt("interval"));
        assertEquals(5,                  obj.getInt("min_change"));
    }

    // -----------------------------------------------------------------------
    // getCmd() — exposes the command string for validation
    // -----------------------------------------------------------------------

    @Test
    public void getCmd_returnsCommandString() {
        assertEquals("motor.run", new SSPMessage("motor.run").getCmd());
    }
}
