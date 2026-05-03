package io.github.appinventor.legospikeprime;

import com.google.appinventor.components.common.OptionList;
import java.util.HashMap;
import java.util.Map;

/** Color constants for comparing against ColorRead event values. */
public enum SensorColor implements OptionList<String> {
  Black("Black"), Red("Red"), Green("Green"), Yellow("Yellow"),
  Blue("Blue"), White("White"), Cyan("Cyan"), Magenta("Magenta"),
  Orange("Orange"), Violet("Violet"), Azure("Azure"), None("None");

  private final String value;
  SensorColor(String v) { this.value = v; }

  @Override public String toUnderlyingValue() { return value; }

  private static final Map<String, SensorColor> lookup = new HashMap<>();
  static { for (SensorColor c : SensorColor.values()) lookup.put(c.toUnderlyingValue(), c); }
  public static SensorColor fromUnderlyingValue(String v) { return lookup.get(v); }
}
