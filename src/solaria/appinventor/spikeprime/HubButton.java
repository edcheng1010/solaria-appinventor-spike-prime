package solaria.appinventor.spikeprime;

import com.google.appinventor.components.common.OptionList;
import java.util.HashMap;
import java.util.Map;

/**
 * Hub buttons for IsHubButtonPressed.
 * Note: pressing the Center button stops the hub program at firmware level —
 * IsHubButtonPressed("Center") can read state, but physically pressing center ends the program.
 */
public enum HubButton implements OptionList<String> {
    Left("left"),
    Right("right"),
    Center("center");

    private final String value;
    HubButton(String v) { this.value = v; }

    @Override public String toUnderlyingValue() { return value; }

    private static final Map<String, HubButton> lookup = new HashMap<>();
    static { for (HubButton b : HubButton.values()) lookup.put(b.toUnderlyingValue(), b); }
    public static HubButton fromUnderlyingValue(String v) { return lookup.get(v); }
}
