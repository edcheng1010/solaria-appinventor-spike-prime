package solaria.appinventor.spikeprime;

import com.google.appinventor.components.common.OptionList;
import java.util.HashMap;
import java.util.Map;

/** Drivebase movement direction for LegoSpikeMovement.Direction. */
public enum MovementDirection implements OptionList<String> {
  Forward("Forward"),
  Backward("Backward");

  private final String value;
  MovementDirection(String v) { this.value = v; }

  @Override public String toUnderlyingValue() { return value; }

  private static final Map<String, MovementDirection> lookup = new HashMap<>();
  static { for (MovementDirection d : MovementDirection.values()) lookup.put(d.toUnderlyingValue(), d); }
  public static MovementDirection fromUnderlyingValue(String v) { return lookup.get(v); }
}
