package com.google.appinventor.components.common;

import java.util.HashMap;
import java.util.Map;

/** Color options for LegoSpikeLight.SetCenterButtonLight. */
public enum HubLightColor implements OptionList<String> {
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
  Azure("azure");

  private final String value;

  HubLightColor(String value) { this.value = value; }

  @Override public String toUnderlyingValue() { return value; }

  private static final Map<String, HubLightColor> lookup = new HashMap<>();
  static { for (HubLightColor c : HubLightColor.values()) lookup.put(c.toUnderlyingValue(), c); }
  public static HubLightColor fromUnderlyingValue(String value) { return lookup.get(value); }
}
