package io.github.appinventor.legospikeprime;

import com.google.appinventor.components.common.OptionList;
import java.util.HashMap;
import java.util.Map;

/**
 * Color options for the SPIKE Prime center button light (indices 0-10, plus Off=0).
 * Violet (2) and Cyan (5) lack color module constants in some firmware versions;
 * the hub handler falls back to their integer index automatically.
 */
public enum CenterButtonColor implements OptionList<String> {
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
  White("White"),
  Off("Off");

  private final String value;
  CenterButtonColor(String v) { this.value = v; }

  @Override public String toUnderlyingValue() { return value; }

  private static final Map<String, CenterButtonColor> lookup = new HashMap<>();
  static { for (CenterButtonColor c : CenterButtonColor.values()) lookup.put(c.toUnderlyingValue(), c); }
  public static CenterButtonColor fromUnderlyingValue(String v) { return lookup.get(v); }
}
