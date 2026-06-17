package solaria.appinventor.spikeprime;

import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

/**
 * Fluent builder for SSP v0.6 JSON command messages.
 *
 * Serialises to UTF-8 JSON terminated by a newline character, matching the
 * json-utf8-newline transport encoding defined in SSP v0.6 §3.1.
 *
 * Usage:
 *   byte[] bytes = new SSPMessage("motor.run")
 *       .withPort("A")
 *       .withParam("speed", 75)
 *       .withRequestId("req-1")
 *       .serialise();
 */
public class SSPMessage {

    private final JSONObject json;

    public SSPMessage(String cmd) {
        json = new JSONObject();
        json.put("cmd", cmd);
    }

    public SSPMessage withPort(String port) {
        json.put("port", port);
        return this;
    }

    public SSPMessage withRequestId(String requestId) {
        json.put("request_id", requestId);
        return this;
    }

    public SSPMessage withParam(String key, int value) {
        json.put(key, value);
        return this;
    }

    public SSPMessage withParam(String key, String value) {
        json.put(key, value);
        return this;
    }

    public SSPMessage withParam(String key, boolean value) {
        json.put(key, value);
        return this;
    }

    public SSPMessage withParam(String key, double value) {
        json.put(key, value);
        return this;
    }

    /** Returns the command category string (e.g. "motor.run"). */
    public String getCmd() {
        return json.getString("cmd");
    }

    /**
     * Serialises to UTF-8 JSON + newline byte array ready for transmission
     * via the SSP json-utf8-newline transport encoding (SSP v0.6 §3.1).
     */
    public byte[] serialise() {
        String text = json.toString() + "\n";
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
