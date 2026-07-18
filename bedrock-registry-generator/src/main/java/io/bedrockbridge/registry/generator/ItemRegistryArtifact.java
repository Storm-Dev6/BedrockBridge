package io.bedrockbridge.registry.generator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Writes and validates a local-only newline-delimited three-field item artifact. */
public final class ItemRegistryArtifact {
  private static final int MAX_ITEMS = 65_536;

  private ItemRegistryArtifact() {}

  /** Validates, deterministically orders, and writes an artifact outside the repository. */
  public static Summary write(Path output, List<ObservedItem> observed) throws IOException {
    RepositoryBoundary.requireOutsideGitWorkTree(output);
    List<ObservedItem> items = validate(observed);
    byte[] bytes = render(items);
    Path normalized = output.toAbsolutePath().normalize();
    Path parent = normalized.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.write(normalized, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    return new Summary(
        items.size(), bytes.length, sha256(bytes), items.getFirst(), items.getLast());
  }

  /** Renders validated observations without touching the filesystem. */
  public static byte[] render(List<ObservedItem> observed) {
    List<ObservedItem> items = validate(observed);
    StringBuilder content = new StringBuilder(items.size() * 64);
    for (ObservedItem item : items) {
      content
          .append("{\"itemName\":\"")
          .append(escape(item.itemName()))
          .append("\",\"itemId\":")
          .append(item.itemId())
          .append(",\"componentBased\":")
          .append(item.componentBased())
          .append("}\n");
    }
    return content.toString().getBytes(StandardCharsets.UTF_8);
  }

  /** Validates and returns a stable item ordering without writing data. */
  public static List<ObservedItem> validate(List<ObservedItem> observed) {
    if (observed == null || observed.isEmpty() || observed.size() > MAX_ITEMS) {
      throw new IllegalArgumentException("Item registry count is outside the permitted range");
    }
    Set<String> names = new HashSet<>();
    Set<Short> ids = new HashSet<>();
    for (ObservedItem item : observed) {
      if (!names.add(item.itemName())) {
        throw new IllegalArgumentException("Duplicate item name: " + item.itemName());
      }
      if (!ids.add(item.itemId())) {
        throw new IllegalArgumentException("Duplicate item ID: " + item.itemId());
      }
    }
    List<ObservedItem> sorted = new ArrayList<>(observed);
    sorted.sort(
        Comparator.comparingInt((ObservedItem item) -> item.itemId())
            .thenComparing(ObservedItem::itemName));
    return List.copyOf(sorted);
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new AssertionError("Java 21 must provide SHA-256", exception);
    }
  }

  /** Summary of a generated artifact, without retaining its contents. */
  public record Summary(
      int itemCount, int byteCount, String sha256, ObservedItem first, ObservedItem last) {}
}
