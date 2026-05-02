package com.google.appinventor.components.common;

import java.util.HashMap;
import java.util.Map;

/** Hub tilt axis options for LegoSpikeSensors.GetTiltAngle. */
public enum TiltAxis implements OptionList<String> {
  Pitch("pitch"),
  Roll("roll"),
  Yaw("yaw");

  private final String value;

  TiltAxis(String value) { this.value = value; }

  @Override public String toUnderlyingValue() { return value; }

  private static final Map<String, TiltAxis> lookup = new HashMap<>();
  static { for (TiltAxis a : TiltAxis.values()) lookup.put(a.toUnderlyingValue(), a); }
  public static TiltAxis fromUnderlyingValue(String value) { return lookup.get(value); }
}
