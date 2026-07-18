package io.bedrockbridge.registry.generator;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Offline validator for a user-owned external protocol registry artifact. */
public final class RegistryCheckCli {
  private RegistryCheckCli() {}

  public static void main(String[] args) throws Exception {
    Map<String, String> options = parseOptions(args);
    ExternalItemRegistry registry =
        new VersionedExternalItemRegistryLoader()
            .load(
                Path.of(required(options, "--artifact")),
                required(options, "--protocol"),
                required(options, "--sha256"));
    System.out.println(
        "registry-valid protocol="
            + registry.protocolVersion()
            + " items="
            + registry.items().size()
            + " bytes="
            + registry.byteCount()
            + " sha256="
            + registry.sha256());
  }

  private static Map<String, String> parseOptions(String[] args) {
    if (args.length != 6) {
      throw new IllegalArgumentException(
          "Usage: --artifact <external.ndjson> --protocol <748> --sha256 <64-lowercase-hex>");
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
