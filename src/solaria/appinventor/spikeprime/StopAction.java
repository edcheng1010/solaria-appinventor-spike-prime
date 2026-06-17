package solaria.appinventor.spikeprime;

import com.google.appinventor.components.common.OptionList;
import java.util.HashMap;
import java.util.Map;

/**
 * Stop action applied when stopping a motor or drivebase.
 * Underlying values match the SSP v0.8 stop_action field.
 */
public enum StopAction implements OptionList<String> {
  Brake("brake"),
  Coast("coast"),
  Hold("hold");

  private final String value;
  StopAction(String v) { this.value = v; }

  @Override public String toUnderlyingValue() { return value; }

  private static final Map<String, StopAction> lookup = new HashMap<>();
  static {
    for (StopAction s : StopAction.values()) lookup.put(s.toUnderlyingValue(), s);
    for (StopAction s : StopAction.values()) lookup.put(s.name(), s);
  }
  public static StopAction fromUnderlyingValue(String v) { return lookup.get(v); }
}
