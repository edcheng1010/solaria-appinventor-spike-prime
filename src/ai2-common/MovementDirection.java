package com.google.appinventor.components.common;

import java.util.HashMap;
import java.util.Map;

/** Drivebase movement direction for LegoSpikeMovement.StartMoving. */
public enum MovementDirection implements OptionList<String> {
  Forward("Forward"),
  Backward("Backward");

  private final String value;

  MovementDirection(String value) {
    this.value = value;
  }

  @Override
  public String toUnderlyingValue() {
    return value;
  }

  private static final Map<String, MovementDirection> lookup = new HashMap<>();

  static {
    for (MovementDirection d : MovementDirection.values()) {
      lookup.put(d.toUnderlyingValue(), d);
    }
  }

  public static MovementDirection fromUnderlyingValue(String value) {
    return lookup.get(value);
  }
}
