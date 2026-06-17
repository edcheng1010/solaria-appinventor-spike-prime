package solaria.appinventor.spikeprime;

import com.google.appinventor.components.common.OptionList;
import java.util.HashMap;
import java.util.Map;

/**
 * Which face of the hub is 'up' — used by SetHubOrientation.
 * Matches LEGO SPIKE "Set Hub Sensor Orientation" block face names.
 * Physical layout: Top=white/display, Bottom=yellow, Front=USB slot,
 * Back=microphone, Left side=ports A/C/E, Right side=ports B/D/F.
 */
public enum HubFace implements OptionList<String> {
  Top("Top"),
  Front("Front"),
  RightSide("Right side"),
  Bottom("Bottom"),
  Back("Back"),
  LeftSide("Left side");

  private final String value;
  HubFace(String v) { this.value = v; }

  @Override public String toUnderlyingValue() { return value; }

  private static final Map<String, HubFace> lookup = new HashMap<>();
  static { for (HubFace f : HubFace.values()) lookup.put(f.toUnderlyingValue(), f); }
  public static HubFace fromUnderlyingValue(String v) { return lookup.get(v); }
}
