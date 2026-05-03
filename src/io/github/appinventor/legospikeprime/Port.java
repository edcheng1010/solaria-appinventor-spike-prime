package io.github.appinventor.legospikeprime;

import com.google.appinventor.components.common.OptionList;
import java.util.HashMap;
import java.util.Map;

/** Hub port options A–F. */
public enum Port implements OptionList<String> {
  A("A"), B("B"), C("C"), D("D"), E("E"), F("F");

  private final String value;
  Port(String v) { this.value = v; }

  @Override public String toUnderlyingValue() { return value; }

  private static final Map<String, Port> lookup = new HashMap<>();
  static { for (Port p : Port.values()) lookup.put(p.toUnderlyingValue(), p); }
  public static Port fromUnderlyingValue(String v) { return lookup.get(v); }
}
