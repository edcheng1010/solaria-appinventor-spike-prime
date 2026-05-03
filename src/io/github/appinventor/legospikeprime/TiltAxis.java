package io.github.appinventor.legospikeprime;

import com.google.appinventor.components.common.OptionList;
import java.util.HashMap;
import java.util.Map;

/** Hub tilt axis options for LegoSpikeSensors.Axis and TiltAngleRead comparisons. */
public enum TiltAxis implements OptionList<String> {
  Pitch("Pitch"), Roll("Roll"), Yaw("Yaw");

  private final String value;
  TiltAxis(String v) { this.value = v; }

  @Override public String toUnderlyingValue() { return value; }

  private static final Map<String, TiltAxis> lookup = new HashMap<>();
  static { for (TiltAxis a : TiltAxis.values()) lookup.put(a.toUnderlyingValue(), a); }
  public static TiltAxis fromUnderlyingValue(String v) { return lookup.get(v); }
}
