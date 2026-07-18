package io.bedrockbridge.registry.generator;

import java.util.Objects;

/** The three protocol-748 StartGame item-list fields permitted in the local artifact. */
public record ObservedItem(String itemName, short itemId, boolean componentBased) {
  public ObservedItem {
    Objects.requireNonNull(itemName, "itemName");
    if (itemName.isBlank() || itemName.indexOf('\n') >= 0 || itemName.indexOf('\r') >= 0) {
      throw new IllegalArgumentException("Item name must be a non-blank single-line string");
    }
  }
}
