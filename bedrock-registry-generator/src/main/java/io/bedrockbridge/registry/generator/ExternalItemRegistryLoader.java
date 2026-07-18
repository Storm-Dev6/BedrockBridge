package io.bedrockbridge.registry.generator;

import java.io.IOException;
import java.nio.file.Path;

/** Loads a version-pinned item registry from outside the repository. */
public interface ExternalItemRegistryLoader {
  /** Reads and validates a registry without accepting unknown fields or duplicate entries. */
  ExternalItemRegistry load(Path artifact, String expectedProtocolVersion, String expectedSha256)
      throws IOException;
}
