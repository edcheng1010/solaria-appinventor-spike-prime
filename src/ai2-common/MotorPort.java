package com.google.appinventor.components.common;

import java.util.HashMap;
import java.util.Map;

/** Motor port options A–F for the LegoSpikeMotor Port property. */
public enum MotorPort implements OptionList<String> {
  A("A"),
  B("B"),
  C("C"),
  D("D"),
  E("E"),
  F("F");

  private final String value;

  MotorPort(String value) {
    this.value = value;
  }

  @Override
  public String toUnderlyingValue() {
    return value;
  }

  private static final Map<String, MotorPort> lookup = new HashMap<>();

  static {
    for (MotorPort p : MotorPort.values()) {
      lookup.put(p.toUnderlyingValue(), p);
    }
  }

  public static MotorPort fromUnderlyingValue(String value) {
    return lookup.get(value);
  }
}
