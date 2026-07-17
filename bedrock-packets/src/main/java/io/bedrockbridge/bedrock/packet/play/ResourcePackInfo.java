package io.bedrockbridge.bedrock.packet.play;

import java.util.Objects;

/** One protocol-748 ResourcePacksInfo entry. */
public record ResourcePackInfo(
    String id,
    String version,
    long size,
    String contentKey,
    String subPackName,
    String contentIdentity,
    boolean hasScripts,
    boolean addonPack,
    boolean rayTracingCapable,
    String cdnUrl) {
  /** Validates required text and the unsigned size subset supported by Java. */
  public ResourcePackInfo {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(version, "version");
    Objects.requireNonNull(contentKey, "contentKey");
    Objects.requireNonNull(subPackName, "subPackName");
    Objects.requireNonNull(contentIdentity, "contentIdentity");
    Objects.requireNonNull(cdnUrl, "cdnUrl");
    if (size < 0) {
      throw new IllegalArgumentException("Resource pack size must be nonnegative");
    }
  }
}
