package com.google.appinventor.components.common;

import java.util.HashMap;
import java.util.Map;

/**
 * Hub tilt axis options for LegoSpikeSensors.GetTiltAngle.
 * Underlying values are title-case strings matching the TiltAngleRead event parameter.
 */
public enum TiltAxis implements OptionList<String> {
  Pitch("Pitch"),
  Roll("Roll"),
  Yaw("Yaw");

  private final String value;

  TiltAxis(String value) {
    this.value = value;
  }

  @Override
  public String toUnderlyingValue() {
    return value;
  }

  private static final Map<String, TiltAxis> lookup = new HashMap<>();

  static {
    for (TiltAxis a : TiltAxis.values()) {
      lookup.put(a.toUnderlyingValue(), a);
    }
  }

  public static TiltAxis fromUnderlyingValue(String value) {
    return lookup.get(value);
  }
}
