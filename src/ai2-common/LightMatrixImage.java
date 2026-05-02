package com.google.appinventor.components.common;

import java.util.HashMap;
import java.util.Map;

/** Predefined image names for LegoSpikeLight.TurnOnLightMatrix. */
public enum LightMatrixImage implements OptionList<String> {
  Heart("heart"),
  HeartSmall("heartsmall"),
  Happy("happy"),
  Smile("smile"),
  Sad("sad"),
  Confused("confused"),
  Angry("angry"),
  Asleep("asleep"),
  Surprised("surprised"),
  Yes("yes"),
  No("no"),
  ArrowNorth("arrownorth"),
  ArrowEast("arroweast"),
  ArrowSouth("arrowsouth"),
  ArrowWest("arrowwest");

  private final String value;

  LightMatrixImage(String value) { this.value = value; }

  @Override public String toUnderlyingValue() { return value; }

  private static final Map<String, LightMatrixImage> lookup = new HashMap<>();
  static { for (LightMatrixImage img : LightMatrixImage.values()) lookup.put(img.toUnderlyingValue(), img); }
  public static LightMatrixImage fromUnderlyingValue(String value) { return lookup.get(value); }
}
