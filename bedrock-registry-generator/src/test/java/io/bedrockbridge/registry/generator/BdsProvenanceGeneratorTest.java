package io.bedrockbridge.registry.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BdsProvenanceGeneratorTest {
  private static final Instant INSPECTED_AT = Instant.parse("2026-07-17T19:30:00Z");
  private static final BdsSource SOURCE =
      new BdsSource(
          "1.21.40.03",
          URI.create(
              "https://www.minecraft.net/bedrockdedicatedserver/bin-win/bedrock-server-1.21.40.03.zip"));

  @TempDir Path temporaryDirectory;

  @Test
  void hashesSyntheticDirectoryDeterministically() throws IOException {
    Path root = Files.createDirectory(temporaryDirectory.resolve("synthetic-bds"));
    Files.writeString(root.resolve("server.bin"), "synthetic-server", StandardCharsets.UTF_8);
    Path data = Files.createDirectory(root.resolve("data"));
    Files.write(data.resolve("registry.bin"), new byte[] {0, 1, 2, 3});

    BdsProvenanceManifest first = new BdsDistributionHasher().hash(root, SOURCE, INSPECTED_AT);
    BdsProvenanceManifest second = new BdsDistributionHasher().hash(root, SOURCE, INSPECTED_AT);

    assertEquals(first, second);
    assertEquals(2, first.files().size());
    assertEquals("data/registry.bin", first.files().get(0).path());
    assertEquals(20, first.distributionSize());
    assertTrue(ProvenanceJson.write(first).contains(BdsProvenanceManifest.DISCLAIMER));
    assertTrue(ProvenanceJson.write(first).contains("\"inspectedAt\": \"2026-07-17T19:30:00Z\""));
  }

  @Test
  void hashesSyntheticZipWithoutExtractingIt() throws IOException {
    Path archive = temporaryDirectory.resolve("synthetic.zip");
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
      zip.putNextEntry(new ZipEntry("data/items.bin"));
      zip.write(new byte[] {4, 5, 6});
      zip.closeEntry();
    }

    BdsProvenanceManifest manifest =
        new BdsDistributionHasher().hash(archive, SOURCE, INSPECTED_AT);

    assertEquals(1, manifest.files().size());
    assertEquals("data/items.bin", manifest.files().get(0).path());
    assertEquals(3, manifest.files().get(0).size());
    assertEquals(sha256(Files.readAllBytes(archive)), manifest.distributionSha256());
  }

  @Test
  void rejectsZipTraversalAndUnofficialSources() throws IOException {
    Path archive = temporaryDirectory.resolve("traversal.zip");
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
      zip.putNextEntry(new ZipEntry("../outside.bin"));
      zip.write(1);
      zip.closeEntry();
    }

    assertThrows(
        IOException.class, () -> new BdsDistributionHasher().hash(archive, SOURCE, INSPECTED_AT));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BdsSource(
                "1.21.40.03", URI.create("https://example.invalid/bedrock-server-1.21.40.03.zip")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BdsSource(
                "1.21.41",
                URI.create(
                    "https://www.minecraft.net/bedrockdedicatedserver/bin-win/bedrock-server-1.21.41.zip")));
  }

  @Test
  void rejectsRepositoryPaths() throws IOException {
    Path repository = Files.createDirectory(temporaryDirectory.resolve("repository"));
    Files.createDirectory(repository.resolve(".git"));
    Path input = Files.write(repository.resolve("bds.zip"), new byte[] {1});

    assertThrows(IOException.class, () -> RepositoryBoundary.requireOutsideGitWorkTree(input));
  }

  private static String sha256(byte[] input) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
    } catch (NoSuchAlgorithmException exception) {
      throw new AssertionError(exception);
    }
  }
}
