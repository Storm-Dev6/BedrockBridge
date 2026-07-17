package io.bedrockbridge.registry.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RepositoryBoundary {
  private RepositoryBoundary() {}

  public static void requireOutsideGitWorkTree(Path path) throws IOException {
    Path cursor = path.toAbsolutePath().normalize();
    if (!Files.exists(cursor)) {
      cursor = cursor.getParent();
    }
    while (cursor != null) {
      if (Files.exists(cursor.resolve(".git"))) {
        throw new IOException(
            "BDS inputs and provenance manifests must remain outside a Git work tree");
      }
      cursor = cursor.getParent();
    }
  }
}
