package io.github.appinventor.legospikeprime;

import com.google.appinventor.components.common.OptionList;
import java.util.HashMap;
import java.util.Map;

/** Motor direction options for LegoSpikeMotors.Direction. */
public enum MotorDirection implements OptionList<String> {
  Clockwise("Clockwise"),
  Counterclockwise("Counterclockwise");

  private final String value;
  MotorDirection(String v) { this.value = v; }

  @Override public String toUnderlyingValue() { return value; }

  private static final Map<String, MotorDirection> lookup = new HashMap<>();
  static { for (MotorDirection d : MotorDirection.values()) lookup.put(d.toUnderlyingValue(), d); }
  public static MotorDirection fromUnderlyingValue(String v) { return lookup.get(v); }
}
