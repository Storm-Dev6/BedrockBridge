package io.bedrockbridge.registry.generator;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record BdsSource(String version, URI downloadUri) {
  private static final Set<String> ALLOWED_EXACT_HOSTS = Set.of("aka.ms", "minecraft.net");
  private static final Set<String> ALLOWED_HOST_SUFFIXES =
      Set.of(".minecraft.net", ".mojang.com", ".microsoft.com");

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
    boolean allowed = ALLOWED_EXACT_HOSTS.contains(host);
    for (String suffix : ALLOWED_HOST_SUFFIXES) {
      allowed |= host.endsWith(suffix);
    }
    if (!allowed) {
      throw new IllegalArgumentException(
          "The distribution source must be an official Microsoft, Mojang, or Minecraft host");
    }
  }
}
