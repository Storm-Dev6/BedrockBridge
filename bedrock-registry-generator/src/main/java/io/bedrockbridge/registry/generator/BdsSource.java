package io.bedrockbridge.registry.generator;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record BdsSource(String version, URI downloadUri) {
  private static final Set<String> ALLOWED_EXACT_HOSTS =
      Set.of("minecraft.net", "www.minecraft.net", "aka.ms");

  public BdsSource {
    Objects.requireNonNull(version, "version");
    Objects.requireNonNull(downloadUri, "downloadUri");
    if (!(version.equals("1.21.40") || version.startsWith("1.21.40."))) {
      throw new IllegalArgumentException("Only the Bedrock 1.21.40 release line is accepted");
    }
    if (!"https".equalsIgnoreCase(downloadUri.getScheme())) {
      throw new IllegalArgumentException("The distribution source must use HTTPS");
    }
    String host =
        Objects.requireNonNull(downloadUri.getHost(), "download URI host").toLowerCase(Locale.ROOT);
    if (!ALLOWED_EXACT_HOSTS.contains(host)) {
      throw new IllegalArgumentException(
          "The distribution source must be an official Microsoft, Mojang, or Minecraft host");
    }
    String path = downloadUri.getPath().toLowerCase(Locale.ROOT);
    String normalizedVersion = version.replace('.', '_');
    if (!path.contains("bedrock-server-" + version.toLowerCase(Locale.ROOT) + ".zip")
        && !path.contains("bedrock-server-" + normalizedVersion + ".zip")) {
      throw new IllegalArgumentException(
          "The source path does not identify the requested BDS version");
    }
  }
}
