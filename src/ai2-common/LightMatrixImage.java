package com.google.appinventor.components.common;

import java.util.HashMap;
import java.util.Map;

/**
 * Predefined image names for LegoSpikeLight.TurnOnLightMatrix.
 * Values match the IMAGES dict in hub_controller.py.
 */
public enum LightMatrixImage implements OptionList<String> {
  Heart("HEART"),
  HeartSmall("HEART_SMALL"),
  Happy("HAPPY"),
  Smile("SMILE"),
  Sad("SAD"),
  Confused("CONFUSED"),
  Angry("ANGRY"),
  Asleep("ASLEEP"),
  Surprised("SURPRISED"),
  Yes("YES"),
  No("NO"),
  ArrowNorth("ARROW_N"),
  ArrowEast("ARROW_E"),
  ArrowSouth("ARROW_S"),
  ArrowWest("ARROW_W");

  private final String value;

  LightMatrixImage(String value) {
    this.value = value;
  }

  @Override
  public String toUnderlyingValue() {
    return value;
  }

  private static final Map<String, LightMatrixImage> lookup = new HashMap<>();

  static {
    for (LightMatrixImage img : LightMatrixImage.values()) {
      lookup.put(img.toUnderlyingValue(), img);
    }
  }

  public static LightMatrixImage fromUnderlyingValue(String value) {
    return lookup.get(value);
  }
}
