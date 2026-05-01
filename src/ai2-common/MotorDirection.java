package com.google.appinventor.components.common;

import java.util.HashMap;
import java.util.Map;

/** Motor direction options for the LegoSpikeMotor Direction property. */
public enum MotorDirection implements OptionList<String> {
  Clockwise("clockwise"),
  Counterclockwise("counterclockwise");

  private final String value;

  MotorDirection(String value) {
    this.value = value;
  }

  @Override
  public String toUnderlyingValue() {
    return value;
  }

  private static final Map<String, MotorDirection> lookup = new HashMap<>();

  static {
    for (MotorDirection d : MotorDirection.values()) {
      lookup.put(d.toUnderlyingValue(), d);
    }
  }

  public static MotorDirection fromUnderlyingValue(String value) {
    return lookup.get(value);
  }
}
