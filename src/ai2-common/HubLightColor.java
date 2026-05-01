package com.google.appinventor.components.common;

import java.util.HashMap;
import java.util.Map;

/**
 * Color options for LegoSpikeLight.SetCenterButtonLight.
 * Values must match Python color module attribute names (getattr(color, value)).
 */
public enum HubLightColor implements OptionList<String> {
  Black("BLACK"),
  Red("RED"),
  Green("GREEN"),
  Yellow("YELLOW"),
  Blue("BLUE"),
  White("WHITE"),
  Cyan("CYAN"),
  Magenta("MAGENTA"),
  Orange("ORANGE"),
  Violet("VIOLET"),
  Azure("AZURE");

  private final String value;

  HubLightColor(String value) {
    this.value = value;
  }

  @Override
  public String toUnderlyingValue() {
    return value;
  }

  private static final Map<String, HubLightColor> lookup = new HashMap<>();

  static {
    for (HubLightColor c : HubLightColor.values()) {
      lookup.put(c.toUnderlyingValue(), c);
    }
  }

  public static HubLightColor fromUnderlyingValue(String value) {
    return lookup.get(value);
  }
}
