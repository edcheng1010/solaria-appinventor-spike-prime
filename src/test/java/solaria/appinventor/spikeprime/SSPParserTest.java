package solaria.appinventor.spikeprime;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * TDD tests for SSPParser — inbound SSP v0.6 JSON event dispatcher.
 *
 * All tests written BEFORE the implementation class exists.
 *
 * SSPParser receives newline-delimited UTF-8 JSON from the bridge and
 * dispatches typed callbacks per SSP v0.6 §3.1.2 event formats.
 */
public class SSPParserTest {

    // -----------------------------------------------------------------------
    // Test helpers — capture-list listener
    // -----------------------------------------------------------------------

    /** Simple recording listener for test assertions. */
    static class RecordingListener implements SSPParser.Listener {
        String lastSensorPort, lastSensorType;
        Object lastSensorValue;
        String lastSystemMetric;
        Object lastSystemValue;
        int lastErrorCode;
        String lastErrorMessage, lastErrorRequestId;
        JSONObject lastCapability;
        int pongCount;
        int unknownCount;

        @Override public void onSensor(String port, String type, Object value) {
            lastSensorPort = port; lastSensorType = type; lastSensorValue = value;
        }
        @Override public void onSystem(String metric, Object value) {
            lastSystemMetric = metric; lastSystemValue = value;
        }
        @Override public void onError(int code, String message, String requestId) {
            lastErrorCode = code; lastErrorMessage = message; lastErrorRequestId = requestId;
        }
        @Override public void onCapability(JSONObject capability) {
            lastCapability = capability;
        }
        @Override public void onPong() {
            pongCount++;
        }
        @Override public void onUnknown(JSONObject raw) {
            unknownCount++;
        }
    }

    private static byte[] frame(String json) {
        return (json + "\n").getBytes(StandardCharsets.UTF_8);
    }

    // -----------------------------------------------------------------------
    // sensor events
    // -----------------------------------------------------------------------

    @Test
    public void parse_sensorColorEvent_dispatchesOnSensor() {
        RecordingListener listener = new RecordingListener();
        SSPParser parser = new SSPParser(listener);
        parser.parse(frame(
            "{\"event\":\"sensor\",\"port\":\"C\",\"type\":\"color\",\"value\":\"red\"}"
        ));
        assertEquals("C", listener.lastSensorPort);
        assertEquals("color", listener.lastSensorType);
        assertEquals("red", listener.lastSensorValue);
    }

    @Test
    public void parse_sensorDistanceEvent_dispatchesOnSensor() {
        RecordingListener listener = new RecordingListener();
        SSPParser parser = new SSPParser(listener);
        parser.parse(frame(
            "{\"event\":\"sensor\",\"port\":\"D\",\"type\":\"distance\",\"value\":23.5}"
        ));
        assertEquals("D", listener.lastSensorPort);
        assertEquals("distance", listener.lastSensorType);
        assertEquals(23.5, ((Number) listener.lastSensorValue).doubleValue(), 0.001);
    }

    @Test
    public void parse_gestureEvent_dispatchesOnSensorAsGestureType() {
        RecordingListener listener = new RecordingListener();
        SSPParser parser = new SSPParser(listener);
        parser.parse(frame(
            "{\"event\":\"sensor\",\"port\":\"imu\",\"type\":\"gesture\",\"value\":\"shake\"}"
        ));
        assertEquals("imu", listener.lastSensorPort);
        assertEquals("gesture", listener.lastSensorType);
        assertEquals("shake", listener.lastSensorValue);
    }

    // -----------------------------------------------------------------------
    // system events
    // -----------------------------------------------------------------------

    @Test
    public void parse_systemBatteryEvent_dispatchesOnSystem() {
        RecordingListener listener = new RecordingListener();
        SSPParser parser = new SSPParser(listener);
        parser.parse(frame(
            "{\"event\":\"system\",\"metric\":\"battery\",\"value\":87}"
        ));
        assertEquals("battery", listener.lastSystemMetric);
        assertEquals(87, ((Number) listener.lastSystemValue).intValue());
    }

    @Test
    public void parse_systemButtonEvent_dispatchesOnSystem() {
        RecordingListener listener = new RecordingListener();
        SSPParser parser = new SSPParser(listener);
        parser.parse(frame(
            "{\"event\":\"system\",\"metric\":\"button.left\",\"value\":\"pressed\"}"
        ));
        assertEquals("button.left", listener.lastSystemMetric);
        assertEquals("pressed", listener.lastSystemValue);
    }

    // -----------------------------------------------------------------------
    // error events
    // -----------------------------------------------------------------------

    @Test
    public void parse_errorEvent_dispatchesOnError() {
        RecordingListener listener = new RecordingListener();
        SSPParser parser = new SSPParser(listener);
        parser.parse(frame(
            "{\"event\":\"error\",\"code\":101,\"message\":\"Port not connected\"}"
        ));
        assertEquals(101, listener.lastErrorCode);
        assertEquals("Port not connected", listener.lastErrorMessage);
        assertNull("request_id absent when not in event", listener.lastErrorRequestId);
    }

    @Test
    public void parse_errorEventWithRequestId_echoesRequestId() {
        RecordingListener listener = new RecordingListener();
        SSPParser parser = new SSPParser(listener);
        parser.parse(frame(
            "{\"event\":\"error\",\"code\":301,\"message\":\"Stall\",\"request_id\":\"req-7\"}"
        ));
        assertEquals("req-7", listener.lastErrorRequestId);
    }

    // -----------------------------------------------------------------------
    // capability declaration
    // -----------------------------------------------------------------------

    @Test
    public void parse_capabilityDeclaration_dispatchesOnCapability() {
        RecordingListener listener = new RecordingListener();
        SSPParser parser = new SSPParser(listener);
        parser.parse(frame(
            "{\"type\":\"capability\",\"device\":\"spike-prime\",\"ssp_version\":\"0.6\",\"ports\":[]}"
        ));
        assertNotNull(listener.lastCapability);
        assertEquals("spike-prime", listener.lastCapability.getString("device"));
        assertEquals("0.6", listener.lastCapability.getString("ssp_version"));
    }

    // -----------------------------------------------------------------------
    // pong
    // -----------------------------------------------------------------------

    @Test
    public void parse_pongEvent_dispatchesOnPong() {
        RecordingListener listener = new RecordingListener();
        SSPParser parser = new SSPParser(listener);
        parser.parse(frame("{\"event\":\"pong\"}"));
        assertEquals(1, listener.pongCount);
    }

    // -----------------------------------------------------------------------
    // robustness
    // -----------------------------------------------------------------------

    @Test
    public void parse_malformedJson_doesNotThrow() {
        RecordingListener listener = new RecordingListener();
        SSPParser parser = new SSPParser(listener);
        // Must not throw any exception
        parser.parse("not json at all\n".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void parse_emptyFrame_doesNotThrow() {
        RecordingListener listener = new RecordingListener();
        SSPParser parser = new SSPParser(listener);
        parser.parse("\n".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void parse_unknownEvent_dispatchesOnUnknown() {
        RecordingListener listener = new RecordingListener();
        SSPParser parser = new SSPParser(listener);
        parser.parse(frame("{\"event\":\"future_event\",\"data\":42}"));
        assertEquals(1, listener.unknownCount);
    }

    @Test
    public void parse_multipleFramesInOneBuffer_dispatchesAll() {
        RecordingListener listener = new RecordingListener();
        SSPParser parser = new SSPParser(listener);
        String two = "{\"event\":\"pong\"}\n{\"event\":\"pong\"}\n";
        parser.parse(two.getBytes(StandardCharsets.UTF_8));
        assertEquals(2, listener.pongCount);
    }
}
