package solaria.appinventor.spikeprime;

import com.google.appinventor.components.common.OptionList;
import java.util.HashMap;
import java.util.Map;

/** Tilt direction for IsTilted — matches LEGO SPIKE isTilted block directions. */
public enum TiltDirection implements OptionList<String> {
    Forward("Forward"),
    Backward("Backward"),
    Left("Left"),
    Right("Right"),
    AnyDirection("Any");

    private final String value;
    TiltDirection(String v) { this.value = v; }

    @Override public String toUnderlyingValue() { return value; }

    private static final Map<String, TiltDirection> lookup = new HashMap<>();
    static { for (TiltDirection d : TiltDirection.values()) lookup.put(d.toUnderlyingValue(), d); }
    public static TiltDirection fromUnderlyingValue(String v) { return lookup.get(v); }
}
