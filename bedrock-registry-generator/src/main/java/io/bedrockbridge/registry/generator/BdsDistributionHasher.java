package io.bedrockbridge.registry.generator;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class BdsDistributionHasher {
  private static final int BUFFER_SIZE = 16 * 1024;
  private static final int MAX_FILES = 100_000;
  private static final long MAX_TOTAL_BYTES = 8L * 1024 * 1024 * 1024;

  public BdsProvenanceManifest hash(Path input, BdsSource source) throws IOException {
    Path normalized = input.toAbsolutePath().normalize();
    if (Files.isDirectory(normalized)) {
      return hashDirectory(normalized, source);
    }
    if (!Files.isRegularFile(normalized)) {
      throw new IOException("BDS input is neither a regular ZIP file nor a directory");
    }
    return hashZip(normalized, source);
  }

  private static BdsProvenanceManifest hashDirectory(Path root, BdsSource source)
      throws IOException {
    List<Path> paths;
    try (var stream = Files.walk(root)) {
      paths = stream.filter(path -> !path.equals(root)).sorted().toList();
    }
    List<FileDigest> files = new ArrayList<>();
    MessageDigest distributionDigest = digest();
    long totalSize = 0;
    for (Path path : paths) {
      if (Files.isSymbolicLink(path)) {
        throw new IOException("Symbolic links are not accepted in a BDS distribution");
      }
      BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
      if (attributes.isDirectory()) {
        continue;
      }
      if (!attributes.isRegularFile()) {
        throw new IOException("Non-regular distribution entry is not accepted");
      }
      totalSize = checkedTotal(totalSize, attributes.size());
      String relative = portablePath(root.relativize(path));
      FileDigest file = hashFile(path, relative, attributes.size());
      files.add(file);
      updateDistributionDigest(distributionDigest, file);
      requireFileCount(files.size());
    }
    return new BdsProvenanceManifest(
        source, HexFormat.of().formatHex(distributionDigest.digest()), totalSize, files);
  }

  private static BdsProvenanceManifest hashZip(Path archive, BdsSource source) throws IOException {
    List<FileDigest> files = new ArrayList<>();
    long totalSize = 0;
    try (InputStream fileInput = new BufferedInputStream(Files.newInputStream(archive));
        ZipInputStream zip = new ZipInputStream(fileInput)) {
      for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
        if (entry.isDirectory()) {
          continue;
        }
        String path = safeZipPath(entry.getName());
        MessageDigest fileDigest = digest();
        long size = copyToDigest(zip, fileDigest, MAX_TOTAL_BYTES - totalSize);
        totalSize = checkedTotal(totalSize, size);
        files.add(new FileDigest(path, size, HexFormat.of().formatHex(fileDigest.digest())));
        requireFileCount(files.size());
      }
    }
    files.sort(Comparator.comparing(FileDigest::path));
    MessageDigest archiveDigest = digest();
    try (InputStream input = new BufferedInputStream(Files.newInputStream(archive))) {
      copyToDigest(input, archiveDigest, MAX_TOTAL_BYTES);
    }
    return new BdsProvenanceManifest(
        source, HexFormat.of().formatHex(archiveDigest.digest()), Files.size(archive), files);
  }

  private static FileDigest hashFile(Path file, String relative, long size) throws IOException {
    MessageDigest fileDigest = digest();
    try (InputStream input = new BufferedInputStream(Files.newInputStream(file))) {
      long read = copyToDigest(input, fileDigest, MAX_TOTAL_BYTES);
      if (read != size) {
        throw new IOException("Distribution file changed while it was being hashed");
      }
    }
    return new FileDigest(relative, size, HexFormat.of().formatHex(fileDigest.digest()));
  }

  private static long copyToDigest(InputStream input, MessageDigest digest, long budget)
      throws IOException {
    byte[] buffer = new byte[BUFFER_SIZE];
    long total = 0;
    for (int read = input.read(buffer); read >= 0; read = input.read(buffer)) {
      if (read == 0) {
        continue;
      }
      total = Math.addExact(total, read);
      if (total > budget) {
        throw new IOException("BDS distribution exceeds the hashing byte budget");
      }
      digest.update(buffer, 0, read);
    }
    return total;
  }

  private static void updateDistributionDigest(MessageDigest digest, FileDigest file) {
    digest.update(file.path().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    digest.update((byte) 0);
    digest.update(Long.toString(file.size()).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    digest.update((byte) 0);
    digest.update(file.sha256().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    digest.update((byte) '\n');
  }

  private static long checkedTotal(long current, long addition) throws IOException {
    long result;
    try {
      result = Math.addExact(current, addition);
    } catch (ArithmeticException exception) {
      throw new IOException("BDS distribution size overflow", exception);
    }
    if (result > MAX_TOTAL_BYTES) {
      throw new IOException("BDS distribution exceeds the hashing byte budget");
    }
    return result;
  }

  private static void requireFileCount(int count) throws IOException {
    if (count > MAX_FILES) {
      throw new IOException("BDS distribution contains too many files");
    }
  }

  private static String safeZipPath(String input) throws IOException {
    String portable = input.replace('\\', '/');
    Path path = Path.of(portable).normalize();
    if (portable.isBlank()
        || portable.startsWith("/")
        || path.isAbsolute()
        || path.startsWith("..")) {
      throw new IOException("Unsafe ZIP entry path");
    }
    return portablePath(path);
  }

  private static String portablePath(Path path) {
    return path.toString().replace('\\', '/');
  }

  private static MessageDigest digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new AssertionError("Java 21 must provide SHA-256", exception);
    }
  }
}
