package io.bedrockbridge.registry.generator;

import java.util.Objects;

public record FileDigest(String path, long size, String sha256) {
  public FileDigest {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(sha256, "sha256");
    if (path.isBlank() || path.startsWith("/") || path.contains("..")) {
      throw new IllegalArgumentException("File path must be a safe relative path");
    }
    if (size < 0) {
      throw new IllegalArgumentException("File size must not be negative");
    }
    if (!sha256.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("SHA-256 must be lower-case hexadecimal");
    }
  }
}
