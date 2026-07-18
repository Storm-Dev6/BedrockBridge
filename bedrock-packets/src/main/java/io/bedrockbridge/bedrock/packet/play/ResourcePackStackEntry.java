package io.bedrockbridge.bedrock.packet.play;

import java.util.Objects;

/** Resource or add-on pack reference carried by ResourcePackStack. */
public record ResourcePackStackEntry(String id, String version, String subPackName) {
  /** Validates required text fields. */
  public ResourcePackStackEntry {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(version, "version");
    Objects.requireNonNull(subPackName, "subPackName");
  }
}
