package io.bedrockbridge.registry.generator;

import java.util.List;

/** Versioned, validated external item registry loaded from a user-owned artifact. */
public record ExternalItemRegistry(
    String protocolVersion, List<ObservedItem> items, long byteCount, String sha256) {
  public ExternalItemRegistry {
    if (protocolVersion == null || protocolVersion.isBlank()) {
      throw new IllegalArgumentException("protocolVersion must not be blank");
    }
    items = List.copyOf(ItemRegistryArtifact.validate(items));
    if (byteCount < 1 || sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("Invalid external registry digest metadata");
    }
  }
}
