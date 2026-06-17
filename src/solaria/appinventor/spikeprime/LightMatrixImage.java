package solaria.appinventor.spikeprime;

import com.google.appinventor.components.common.OptionList;
import java.util.HashMap;
import java.util.Map;

/** Predefined image names for LegoSpikeLight.Image. */
public enum LightMatrixImage implements OptionList<String> {
  Heart("Heart"), HeartSmall("HeartSmall"), Happy("Happy"),
  Smile("Smile"), Sad("Sad"), Confused("Confused"),
  Angry("Angry"), Asleep("Asleep"), Surprised("Surprised"),
  Yes("Yes"), No("No"),
  ArrowNorth("ArrowNorth"), ArrowEast("ArrowEast"),
  ArrowSouth("ArrowSouth"), ArrowWest("ArrowWest");

  private final String value;
  LightMatrixImage(String v) { this.value = v; }

  @Override public String toUnderlyingValue() { return value; }

  private static final Map<String, LightMatrixImage> lookup = new HashMap<>();
  static { for (LightMatrixImage i : LightMatrixImage.values()) lookup.put(i.toUnderlyingValue(), i); }
  public static LightMatrixImage fromUnderlyingValue(String v) { return lookup.get(v); }
}
