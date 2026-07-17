package io.bedrockbridge.registry.generator;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record BdsProvenanceManifest(
    BdsSource source, String distributionSha256, long distributionSize, List<FileDigest> files) {
  public static final String SCHEMA = "io.bedrockbridge.bds-provenance/v1";
  public static final String DISCLAIMER =
      "NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.";

  public BdsProvenanceManifest {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(distributionSha256, "distributionSha256");
    files =
        List.copyOf(Objects.requireNonNull(files, "files")).stream()
            .sorted(Comparator.comparing(FileDigest::path))
            .toList();
    if (!distributionSha256.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("Distribution SHA-256 must be lower-case hexadecimal");
    }
    if (distributionSize < 0) {
      throw new IllegalArgumentException("Distribution size must not be negative");
    }
    for (int index = 1; index < files.size(); index++) {
      if (files.get(index - 1).path().equals(files.get(index).path())) {
        throw new IllegalArgumentException("Duplicate distribution file path");
      }
    }
  }
}
