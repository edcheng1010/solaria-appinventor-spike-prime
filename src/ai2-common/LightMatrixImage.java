package com.google.appinventor.components.common;

import java.util.HashMap;
import java.util.Map;

/**
 * Predefined image names for LegoSpikeLight.TurnOnLightMatrix.
 * Underlying values match the enum display names (camelCase).
 * hub_controller.py uses value.upper() before the IMAGES dict lookup so
 * both old uppercase-underscore and new camelCase-uppercase forms are accepted.
 */
public enum LightMatrixImage implements OptionList<String> {
  Heart("Heart"),
  HeartSmall("HeartSmall"),
  Happy("Happy"),
  Smile("Smile"),
  Sad("Sad"),
  Confused("Confused"),
  Angry("Angry"),
  Asleep("Asleep"),
  Surprised("Surprised"),
  Yes("Yes"),
  No("No"),
  ArrowNorth("ArrowNorth"),
  ArrowEast("ArrowEast"),
  ArrowSouth("ArrowSouth"),
  ArrowWest("ArrowWest");

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
