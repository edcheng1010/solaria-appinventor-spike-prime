package io.github.appinventor.legospikeprime;

import org.json.JSONException;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Pre-send constraint checker for SSP v0.6 messages.
 *
 * Validates an {@link SSPMessage} against the constraints declared in the
 * {@link CapabilityStore}. On violation, fires the {@link ErrorCallback} with
 * a 2xx error code (command error range per SSP v0.6 §7) and returns false.
 * On a valid message, returns true without firing.
 *
 * Rate-limiting: the same (port, param) combination fires at most one error
 * per second to avoid flooding App Inventor event handlers in a tight loop.
 */
public class Validator {

    // -----------------------------------------------------------------------
    // Error codes (SSP v0.6 §7 command-error range: 200–299)
    // -----------------------------------------------------------------------

    /** Unknown or undeclared port. */
    public static final int ERR_UNKNOWN_PORT = 201;
    /** Parameter value violates an int min/max constraint. */
    public static final int ERR_INT_OUT_OF_RANGE = 202;
    /** Parameter value not in the declared enum values list. */
    public static final int ERR_ENUM_INVALID = 203;

    // -----------------------------------------------------------------------
    // Callback interface
    // -----------------------------------------------------------------------

    public interface ErrorCallback {
        void onValidationError(int code, String message);
    }

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    private final CapabilityStore store;
    private final ErrorCallback callback;
    // Rate-limit: key → last error timestamp in ms
    private final Map<String, Long> lastErrorTime = new HashMap<>();
    private static final long RATE_LIMIT_MS = 1000L;

    public Validator(CapabilityStore store, ErrorCallback callback) {
        this.store    = store;
        this.callback = callback;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Validates the message and returns true if it may be sent.
     *
     * @param message the SSP message to validate
     * @return true if valid, false if a constraint was violated
     */
    public boolean validate(SSPMessage message) {
        // Deserialise to inspect params
        JSONObject obj;
        try {
            String json = new String(message.serialise(), StandardCharsets.UTF_8).trim();
            obj = new JSONObject(json);
        } catch (JSONException e) {
            return false; // shouldn't happen with SSPMessage
        }

        String portId = obj.optString("port", null);

        // Commands without a port (system.ping, batch, etc.) skip port/param checks
        if (portId == null || portId.isEmpty()) return true;

        // Check port is declared
        if (!store.hasPort(portId)) {
            fireRateLimited(portId + ":__port__", ERR_UNKNOWN_PORT,
                "Unknown port: " + portId);
            return false;
        }

        // Check each param that has a declared constraint
        boolean valid = true;
        Iterator<String> iter = obj.keys();
        while (iter.hasNext()) {
            String key = iter.next();
            if (key.equals("cmd") || key.equals("port") || key.equals("request_id")) continue;

            String constraintType = store.getConstraintType(portId, key);
            if (constraintType == null) continue; // no constraint declared — skip

            Object value = obj.opt(key);

            switch (constraintType) {
                case "int": {
                    int iv = toInt(value);
                    int min = store.getConstraintMin(portId, key);
                    int max = store.getConstraintMax(portId, key);
                    if (iv < min || iv > max) {
                        fireRateLimited(portId + ":" + key, ERR_INT_OUT_OF_RANGE,
                            portId + "." + key + "=" + iv
                                + " out of range [" + min + ", " + max + "]");
                        valid = false;
                    }
                    break;
                }
                case "enum": {
                    String sv = String.valueOf(value);
                    List<String> allowed = store.getConstraintEnumValues(portId, key);
                    if (allowed != null && !allowed.contains(sv)) {
                        fireRateLimited(portId + ":" + key, ERR_ENUM_INVALID,
                            portId + "." + key + "=\"" + sv + "\" not in " + allowed);
                        valid = false;
                    }
                    break;
                }
                default:
                    // array, string, bool — no client-side range check currently
                    break;
            }
        }
        return valid;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void fireRateLimited(String key, int code, String message) {
        long now = System.currentTimeMillis();
        Long last = lastErrorTime.get(key);
        if (last != null && (now - last) < RATE_LIMIT_MS) return;
        lastErrorTime.put(key, now);
        callback.onValidationError(code, message);
    }

    private static int toInt(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        try { return Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException e) { return 0; }
    }
}
