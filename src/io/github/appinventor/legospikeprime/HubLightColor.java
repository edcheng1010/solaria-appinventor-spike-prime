package io.github.appinventor.legospikeprime;

import com.google.appinventor.components.common.OptionList;
import java.util.HashMap;
import java.util.Map;

/**
 * All 11 center-button LED colors for SPIKE Prime 3.x (indices 0-10).
 * Violet (2) and Cyan (5) lack color module constants in some firmware
 * versions; the hub handler falls back to their integer index automatically.
 */
public enum HubLightColor implements OptionList<String> {
  Black("Black"),
  Magenta("Magenta"),
  Violet("Violet"),
  Blue("Blue"),
  Azure("Azure"),
  Cyan("Cyan"),
  Green("Green"),
  Yellow("Yellow"),
  Orange("Orange"),
  Red("Red"),
  White("White");

  private final String value;
  HubLightColor(String v) { this.value = v; }

  @Override public String toUnderlyingValue() { return value; }

  private static final Map<String, HubLightColor> lookup = new HashMap<>();
  static { for (HubLightColor c : HubLightColor.values()) lookup.put(c.toUnderlyingValue(), c); }
  public static HubLightColor fromUnderlyingValue(String v) { return lookup.get(v); }
}
