package solaria.appinventor.spikeprime;

import org.json.JSONException;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

/**
 * Parses inbound SSP v0.6 JSON event frames from the bridge and dispatches
 * typed callbacks to a {@link Listener}.
 *
 * Input is newline-delimited UTF-8 JSON (json-utf8-newline encoding per SSP
 * v0.6 §3.1). Multiple frames may be present in a single {@link #parse(byte[])}
 * call — each newline-terminated line is processed independently.
 *
 * Unknown or unparseable frames are silently forwarded to
 * {@link Listener#onUnknown} without throwing.
 */
public class SSPParser {

    // -----------------------------------------------------------------------
    // Listener interface
    // -----------------------------------------------------------------------

    public interface Listener {
        /** SSP sensor event: {"event":"sensor", "port":..., "type":..., "value":...} */
        void onSensor(String port, String type, Object value);

        /** SSP system metric event: {"event":"system", "metric":..., "value":...} */
        void onSystem(String metric, Object value);

        /** SSP error event: {"event":"error", "code":..., "message":..., ["request_id":...]} */
        void onError(int code, String message, String requestId);

        /** SSP capability declaration: {"type":"capability", ...} */
        void onCapability(JSONObject capability);

        /** SSP pong event in response to system.ping. */
        void onPong();

        /** Any well-formed JSON event that does not match a known type. */
        void onUnknown(JSONObject raw);
    }

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    private final Listener listener;

    public SSPParser(Listener listener) {
        this.listener = listener;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Parses one or more newline-terminated JSON frames from {@code data}.
     * Malformed lines are silently skipped.
     *
     * @param data raw bytes received from the BLE bridge (may contain multiple frames)
     */
    public void parse(byte[] data) {
        if (data == null || data.length == 0) return;
        String text = new String(data, StandardCharsets.UTF_8);
        // Split on newline; each non-empty segment is one JSON frame
        String[] lines = text.split("\n", -1);
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            try {
                dispatch(new JSONObject(line));
            } catch (JSONException ignored) {
                // malformed frame — skip silently
            }
        }
    }

    // -----------------------------------------------------------------------
    // Private dispatch
    // -----------------------------------------------------------------------

    private void dispatch(JSONObject obj) {
        // Capability declaration uses "type" field instead of "event"
        if (obj.has("type") && "capability".equals(obj.optString("type"))) {
            listener.onCapability(obj);
            return;
        }

        String event = obj.optString("event", "");

        switch (event) {
            case "sensor":
                listener.onSensor(
                    obj.optString("port"),
                    obj.optString("type"),
                    sensorValue(obj)
                );
                break;

            case "system":
                listener.onSystem(
                    obj.optString("metric"),
                    systemValue(obj)
                );
                break;

            case "error":
                listener.onError(
                    obj.optInt("code", 0),
                    obj.optString("message"),
                    obj.has("request_id") ? obj.optString("request_id") : null
                );
                break;

            case "pong":
                listener.onPong();
                break;

            default:
                listener.onUnknown(obj);
                break;
        }
    }

    /** Extracts the "value" field preserving the most specific numeric type. */
    private static Object sensorValue(JSONObject obj) {
        return extractValue(obj, "value");
    }

    private static Object systemValue(JSONObject obj) {
        return extractValue(obj, "value");
    }

    private static Object extractValue(JSONObject obj, String key) {
        Object raw = obj.opt(key);
        if (raw instanceof Integer || raw instanceof Long
                || raw instanceof Double || raw instanceof Float
                || raw instanceof String || raw instanceof Boolean) {
            return raw;
        }
        return raw; // JSONObject / JSONArray / JSONObject.NULL — pass through
    }
}
