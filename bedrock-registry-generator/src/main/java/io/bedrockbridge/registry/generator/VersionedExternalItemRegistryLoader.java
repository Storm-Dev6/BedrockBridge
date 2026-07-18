package io.bedrockbridge.registry.generator;

import io.bedrockbridge.common.ConfigurationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict NDJSON loader for the three-field external protocol registry. */
public final class VersionedExternalItemRegistryLoader implements ExternalItemRegistryLoader {
  private static final Pattern LINE =
      Pattern.compile(
          "^\\{\\\"itemName\\\":\\\"((?:\\\\.|[^\\\"\\\\])*)\\\",\\\"itemId\\\":(-?\\d+),\\\"componentBased\\\":(true|false)\\}$");

  @Override
  public ExternalItemRegistry load(
      Path artifact, String expectedProtocolVersion, String expectedSha256) throws IOException {
    RepositoryBoundary.requireOutsideGitWorkTree(artifact);
    if (artifact == null || !Files.isRegularFile(artifact)) {
      throw new ConfigurationException("External registry artifact is missing: " + artifact);
    }
    if (expectedProtocolVersion == null || expectedProtocolVersion.isBlank()) {
      throw new ConfigurationException("External registry protocol version is required");
    }
    if (expectedSha256 == null || !expectedSha256.matches("[0-9a-f]{64}")) {
      throw new ConfigurationException(
          "External registry SHA-256 must be 64 lowercase hex characters");
    }
    byte[] bytes = Files.readAllBytes(artifact);
    return parse(bytes, expectedProtocolVersion, expectedSha256);
  }

  /** Parses an already isolated artifact snapshot for deterministic tests and callers. */
  public ExternalItemRegistry parse(
      byte[] bytes, String expectedProtocolVersion, String expectedSha256) throws IOException {
    if (bytes == null) {
      throw new ConfigurationException("External registry bytes are missing");
    }
    if (expectedProtocolVersion == null || expectedProtocolVersion.isBlank()) {
      throw new ConfigurationException("External registry protocol version is required");
    }
    if (expectedSha256 == null || !expectedSha256.matches("[0-9a-f]{64}")) {
      throw new ConfigurationException(
          "External registry SHA-256 must be 64 lowercase hex characters");
    }
    String actualSha256 = sha256(bytes);
    if (!actualSha256.equals(expectedSha256)) {
      throw new ConfigurationException("External registry SHA-256 mismatch");
    }
    List<ObservedItem> items = new ArrayList<>();
    try (BufferedReader reader =
        new BufferedReader(new java.io.StringReader(new String(bytes, StandardCharsets.UTF_8)))) {
      String line;
      int lineNumber = 0;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        if (line.isBlank()) {
          throw new ConfigurationException(
              "External registry contains a blank line at " + lineNumber);
        }
        Matcher matcher = LINE.matcher(line);
        if (!matcher.matches()) {
          if (!line.contains("itemName")
              || !line.contains("itemId")
              || !line.contains("componentBased")) {
            throw new ConfigurationException(
                "External registry missing required fields at line " + lineNumber);
          }
          throw new ConfigurationException(
              "External registry schema violation at line " + lineNumber);
        }
        int id;
        try {
          id = Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException failure) {
          throw new ConfigurationException(
              "External registry item ID is not an integer at line " + lineNumber);
        }
        if (id < Short.MIN_VALUE || id > Short.MAX_VALUE) {
          throw new ConfigurationException(
              "External registry item ID is outside signed short range");
        }
        items.add(
            new ObservedItem(
                unescape(matcher.group(1), lineNumber),
                (short) id,
                Boolean.parseBoolean(matcher.group(3))));
      }
    }
    if (items.isEmpty()) {
      throw new ConfigurationException("External registry artifact is empty");
    }
    return new ExternalItemRegistry(expectedProtocolVersion, items, bytes.length, actualSha256);
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }

  private static String unescape(String value, int lineNumber) throws ConfigurationException {
    StringBuilder result = new StringBuilder(value.length());
    boolean escaped = false;
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (!escaped) {
        if (character == '\\') {
          escaped = true;
        } else {
          result.append(character);
        }
        continue;
      }
      escaped = false;
      switch (character) {
        case '"' -> result.append('"');
        case '\\' -> result.append('\\');
        case '/' -> result.append('/');
        case 'b' -> result.append('\b');
        case 'f' -> result.append('\f');
        case 'n' -> result.append('\n');
        case 'r' -> result.append('\r');
        case 't' -> result.append('\t');
        default ->
            throw new ConfigurationException("Unsupported item name escape at line " + lineNumber);
      }
    }
    if (escaped) {
      throw new ConfigurationException("Truncated item name escape at line " + lineNumber);
    }
    return result.toString();
  }
}
