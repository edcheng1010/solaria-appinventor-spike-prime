package solaria.appinventor.spikeprime;

import com.google.appinventor.components.common.OptionList;
import java.util.HashMap;
import java.util.Map;

/**
 * Duration unit options for RunMotorForDuration, MoveForDuration,
 * and MoveWithSteeringForDuration.
 *
 * Underlying values match the SSP v0.8 §6.1 `duration_unit` field:
 * "ms", "degrees", "rotations".
 */
public enum DurationUnit implements OptionList<String> {
  Milliseconds("ms"),
  Degrees("degrees"),
  Rotations("rotations");

  private final String value;
  DurationUnit(String v) { this.value = v; }

  @Override public String toUnderlyingValue() { return value; }

  private static final Map<String, DurationUnit> lookup = new HashMap<>();
  static {
    for (DurationUnit u : DurationUnit.values()) lookup.put(u.toUnderlyingValue(), u);
    // Also accept the dropdown labels in case App Inventor passes them through
    for (DurationUnit u : DurationUnit.values()) lookup.put(u.name(), u);
  }
  public static DurationUnit fromUnderlyingValue(String v) { return lookup.get(v); }
}
