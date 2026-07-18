package io.bedrockbridge.registry.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Reads only bounded, non-sensitive BDS lifecycle lines from external stdout/stderr logs. */
final class BdsServerLogReader {
  private static final int MAX_LINES = 200;
  private static final Pattern LONG_TOKEN = Pattern.compile("[A-Za-z0-9_+/=-]{80,}");
  private static final String[] KEYWORDS = {
    "server", "login", "disconnect", "network", "authentication", "encryption", "pack"
  };

  private BdsServerLogReader() {}

  static List<String> relevant(Path path) throws IOException {
    RepositoryBoundary.requireOutsideGitWorkTree(path);
    if (!Files.isRegularFile(path)) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    try (Stream<String> lines = Files.lines(path)) {
      lines
          .filter(BdsServerLogReader::isRelevant)
          .limit(MAX_LINES)
          .map(BdsServerLogReader::sanitize)
          .forEach(result::add);
    }
    return List.copyOf(result);
  }

  private static boolean isRelevant(String line) {
    String lower = line.toLowerCase(Locale.ROOT);
    for (String keyword : KEYWORDS) {
      if (lower.contains(keyword)) {
        return true;
      }
    }
    return false;
  }

  private static String sanitize(String line) {
    return LONG_TOKEN.matcher(line.replaceAll("[\\r\\n]+", " ")).replaceAll("<redacted>");
  }
}
