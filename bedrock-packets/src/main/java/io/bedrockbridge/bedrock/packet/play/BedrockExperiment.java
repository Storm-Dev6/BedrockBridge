package io.bedrockbridge.bedrock.packet.play;

import java.util.Objects;

/** One protocol-748 experiment entry as documented by Mojang. */
public record BedrockExperiment(
    String toggleName, boolean enabled, String alwaysOnName, boolean alwaysOnEnabled) {
  /** Validates both documented experiment names. */
  public BedrockExperiment {
    Objects.requireNonNull(toggleName, "toggleName");
    Objects.requireNonNull(alwaysOnName, "alwaysOnName");
  }
}
