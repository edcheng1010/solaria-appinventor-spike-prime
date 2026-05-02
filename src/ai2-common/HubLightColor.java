package com.google.appinventor.components.common;

import java.util.HashMap;
import java.util.Map;

/**
 * Color options for LegoSpikeLight.SetCenterButtonLight.
 * Underlying values are title-case strings. The hub Python uppercases them
 * before the getattr(color, ...) lookup so either case is accepted.
 */
public enum HubLightColor implements OptionList<String> {
  Black("Black"),
  Red("Red"),
  Green("Green"),
  Yellow("Yellow"),
  Blue("Blue"),
  White("White"),
  Cyan("Cyan"),
  Magenta("Magenta"),
  Orange("Orange"),
  Violet("Violet"),
  Azure("Azure");

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
