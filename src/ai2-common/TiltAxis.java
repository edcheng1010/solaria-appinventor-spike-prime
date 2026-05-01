package com.google.appinventor.components.common;

import java.util.HashMap;
import java.util.Map;

/**
 * Hub tilt axis options for LegoSpikeSensor.GetTiltAngle.
 * Values must match the axis keys expected by hub_controller.py (SEN:TLT:<axis>).
 */
public enum TiltAxis implements OptionList<String> {
  Pitch("PITCH"),
  Roll("ROLL"),
  Yaw("YAW");

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
