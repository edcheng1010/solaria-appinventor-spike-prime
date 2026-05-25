package io.github.appinventor.legospikeprime;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Caches the SSP v0.6 capability declaration received from the bridge on
 * connection. Provides typed query methods for ports, features, constraints,
 * display dimensions, and speaker sounds.
 *
 * Thread-safety: {@link #load(JSONObject)} must be called from a single thread
 * at connection time. Query methods are read-only and safe to call from any thread
 * after loading.
 */
public class CapabilityStore {

    // -----------------------------------------------------------------------
    // Internal representation
    // -----------------------------------------------------------------------

    private static class PortInfo {
        String type;
        Set<String> features = new HashSet<>();
        Map<String, JSONObject> constraints = new HashMap<>();
        // display-specific
        int width = -1, height = -1;
        String depth = null;
        // speaker-specific
        List<String> builtinSounds = null;
    }

    private String deviceType;
    private String sspVersion;
    private boolean supportsBatch;
    private List<String> systemMetrics = Collections.emptyList();
    private Map<String, PortInfo> ports = new HashMap<>();
    private boolean loaded   = false;
    private boolean received = false; // signals waitForCapability()

    // -----------------------------------------------------------------------
    // Load / clear / wait
    // -----------------------------------------------------------------------

    public synchronized void clear() {
        ports.clear();
        deviceType = null;
        sspVersion = null;
        supportsBatch = false;
        systemMetrics = Collections.emptyList();
        loaded   = false;
        received = false;
        notifyAll(); // wake any thread stuck in waitForCapability after a clear
    }

    /**
     * Blocks until a capability declaration arrives or timeoutMs elapses.
     * Must NOT be called on the main/UI thread.
     */
    public synchronized boolean waitForCapability(long timeoutMs) {
        if (received) return true;
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!received) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) break;
            try { wait(remaining); } catch (InterruptedException e) { break; }
        }
        return received;
    }

    public synchronized void load(JSONObject capability) {
        ports.clear();
        deviceType   = capability.optString("device", null);
        sspVersion   = capability.optString("ssp_version", null);
        supportsBatch = capability.optBoolean("supports_batch", false);

        JSONArray metrics = capability.optJSONArray("system_metrics");
        if (metrics != null) {
            List<String> list = new ArrayList<>();
            for (int i = 0; i < metrics.length(); i++) list.add(metrics.optString(i));
            systemMetrics = Collections.unmodifiableList(list);
        }

        JSONArray portArray = capability.optJSONArray("ports");
        if (portArray != null) {
            for (int i = 0; i < portArray.length(); i++) {
                JSONObject p = portArray.optJSONObject(i);
                if (p == null) continue;
                String id = p.optString("id");
                if (id == null || id.isEmpty()) continue;

                PortInfo info = new PortInfo();
                info.type = p.optString("type", null);

                JSONArray features = p.optJSONArray("features");
                if (features != null) {
                    for (int j = 0; j < features.length(); j++) {
                        info.features.add(features.optString(j));
                    }
                }

                JSONObject constraints = p.optJSONObject("constraints");
                if (constraints != null) {
                    Iterator<String> ckeys = constraints.keys();
                    while (ckeys.hasNext()) {
                        String key = ckeys.next();
                        JSONObject constraint = constraints.optJSONObject(key);
                        if (constraint != null) info.constraints.put(key, constraint);
                    }
                }

                // Display dimensions
                if (p.has("width"))  info.width  = p.optInt("width",  -1);
                if (p.has("height")) info.height = p.optInt("height", -1);
                if (p.has("depth"))  info.depth  = p.optString("depth", null);

                // Speaker built-in sounds
                JSONArray sounds = p.optJSONArray("builtin_sounds");
                if (sounds != null) {
                    List<String> list = new ArrayList<>();
                    for (int j = 0; j < sounds.length(); j++) list.add(sounds.optString(j));
                    info.builtinSounds = Collections.unmodifiableList(list);
                }

                ports.put(id, info);
            }
        }
        loaded   = true;
        received = true;
        notifyAll(); // wake waitForCapability()
    }

    // -----------------------------------------------------------------------
    // Top-level queries
    // -----------------------------------------------------------------------

    public String getDeviceType()    { return deviceType; }
    public String getSspVersion()    { return sspVersion; }
    public boolean supportsBatch()   { return supportsBatch; }
    public List<String> getSystemMetrics() { return systemMetrics; }

    // -----------------------------------------------------------------------
    // Port queries
    // -----------------------------------------------------------------------

    public boolean hasPort(String portId) {
        return ports.containsKey(portId);
    }

    public String getPortType(String portId) {
        PortInfo p = ports.get(portId);
        return p != null ? p.type : null;
    }

    // -----------------------------------------------------------------------
    // Feature queries
    // -----------------------------------------------------------------------

    public boolean hasFeature(String portId, String feature) {
        PortInfo p = ports.get(portId);
        return p != null && p.features.contains(feature);
    }

    // -----------------------------------------------------------------------
    // Constraint queries
    // -----------------------------------------------------------------------

    private JSONObject constraint(String portId, String feature) {
        PortInfo p = ports.get(portId);
        return p != null ? p.constraints.get(feature) : null;
    }

    /** Returns the constraint type string ("int", "enum", "array", etc.), or null. */
    public String getConstraintType(String portId, String feature) {
        JSONObject c = constraint(portId, feature);
        return c != null ? c.optString("type", null) : null;
    }

    /** Returns the int constraint min, or Integer.MIN_VALUE if absent. */
    public int getConstraintMin(String portId, String feature) {
        JSONObject c = constraint(portId, feature);
        return c != null ? c.optInt("min", Integer.MIN_VALUE) : Integer.MIN_VALUE;
    }

    /** Returns the int constraint max, or Integer.MAX_VALUE if absent. */
    public int getConstraintMax(String portId, String feature) {
        JSONObject c = constraint(portId, feature);
        return c != null ? c.optInt("max", Integer.MAX_VALUE) : Integer.MAX_VALUE;
    }

    /** Returns true if the constraint declares wraps=true. */
    public boolean getConstraintWraps(String portId, String feature) {
        JSONObject c = constraint(portId, feature);
        return c != null && c.optBoolean("wraps", false);
    }

    /**
     * Returns the enum values list for an "enum" constraint, or null if the
     * constraint is absent or not of type "enum".
     */
    public List<String> getConstraintEnumValues(String portId, String feature) {
        JSONObject c = constraint(portId, feature);
        if (c == null || !"enum".equals(c.optString("type"))) return null;
        JSONArray arr = c.optJSONArray("values");
        if (arr == null) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) result.add(arr.optString(i));
        return Collections.unmodifiableList(result);
    }

    // -----------------------------------------------------------------------
    // Display-specific queries
    // -----------------------------------------------------------------------

    /** Returns the display width, or -1 if port is not a display port. */
    public int getDisplayWidth(String portId) {
        PortInfo p = ports.get(portId);
        return (p != null && "display".equals(p.type)) ? p.width : -1;
    }

    /** Returns the display height, or -1 if port is not a display port. */
    public int getDisplayHeight(String portId) {
        PortInfo p = ports.get(portId);
        return (p != null && "display".equals(p.type)) ? p.height : -1;
    }

    /** Returns the display depth string, or null if port is not a display port. */
    public String getDisplayDepth(String portId) {
        PortInfo p = ports.get(portId);
        return (p != null && "display".equals(p.type)) ? p.depth : null;
    }

    // -----------------------------------------------------------------------
    // Speaker-specific queries
    // -----------------------------------------------------------------------

    /** Returns the built-in sound names for a speaker port, or null. */
    public List<String> getBuiltinSounds(String portId) {
        PortInfo p = ports.get(portId);
        return (p != null && "speaker".equals(p.type)) ? p.builtinSounds : null;
    }
}
