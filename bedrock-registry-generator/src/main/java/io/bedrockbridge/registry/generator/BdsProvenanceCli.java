package io.bedrockbridge.registry.generator;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class BdsProvenanceCli {
  private BdsProvenanceCli() {}

  public static void main(String[] args) throws Exception {
    if (args.length > 0 && "--artifact".equals(args[0])) {
      try {
        RegistryCheckCli.main(args);
      } catch (Exception failure) {
        if (failure instanceof IOException ioFailure) {
          throw ioFailure;
        }
        throw failure;
      }
      return;
    }
    Map<String, String> options = parseOptions(args);
    Path input = Path.of(required(options, "--input"));
    Path output = Path.of(required(options, "--output"));
    RepositoryBoundary.requireOutsideGitWorkTree(input);
    RepositoryBoundary.requireOutsideGitWorkTree(output);

    BdsSource source =
        new BdsSource(
            required(options, "--version"), URI.create(required(options, "--source-url")));
    BdsProvenanceManifest manifest = new BdsDistributionHasher().hash(input, source, Instant.now());
    Path parent = output.toAbsolutePath().normalize().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.writeString(
        output,
        ProvenanceJson.write(manifest),
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE);
  }

  private static Map<String, String> parseOptions(String[] args) {
    if (args.length != 8) {
      throw new IllegalArgumentException(
          "Usage: --input <zip-or-directory> --output <manifest.json> "
              + "--version <1.21.40[.patch]> --source-url <official-https-url>");
    }
    Map<String, String> options = new HashMap<>();
    for (int index = 0; index < args.length; index += 2) {
      String name = args[index];
      if (!name.startsWith("--") || options.put(name, args[index + 1]) != null) {
        throw new IllegalArgumentException("Invalid or duplicate command-line option");
      }
    }
    return Map.copyOf(options);
  }

  private static String required(Map<String, String> options, String name) {
    String value = options.get(name);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing required option: " + name);
    }
    return value;
  }
}
