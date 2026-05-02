package com.google.appinventor.components.common;

import java.util.HashMap;
import java.util.Map;

/** Color constants for comparing against the color parameter in ColorRead events. */
public enum SensorColor implements OptionList<String> {
  Black("black"),
  Red("red"),
  Green("green"),
  Yellow("yellow"),
  Blue("blue"),
  White("white"),
  Cyan("cyan"),
  Magenta("magenta"),
  Orange("orange"),
  Violet("violet"),
  Azure("azure"),
  None("none");

  private final String value;

  SensorColor(String value) { this.value = value; }

  @Override public String toUnderlyingValue() { return value; }

  private static final Map<String, SensorColor> lookup = new HashMap<>();
  static { for (SensorColor c : SensorColor.values()) lookup.put(c.toUnderlyingValue(), c); }
  public static SensorColor fromUnderlyingValue(String value) { return lookup.get(value); }
}
