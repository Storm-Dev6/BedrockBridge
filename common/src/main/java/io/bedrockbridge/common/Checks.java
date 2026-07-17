package io.bedrockbridge.common;

import java.util.Objects;

/** Side-effect-free argument validation shared by infrastructure modules. */
public final class Checks {
  private Checks() {}

  /** Returns a non-null, non-blank value. */
  public static String notBlank(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }

  /** Returns a value inside the inclusive range. */
  public static int inRange(int value, int minimum, int maximum, String name) {
    if (value < minimum || value > maximum) {
      throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
    }
    return value;
  }
}
