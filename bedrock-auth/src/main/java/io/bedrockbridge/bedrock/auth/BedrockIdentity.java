package io.bedrockbridge.bedrock.auth;

import java.security.PublicKey;
import java.util.Objects;
import java.util.UUID;

/** Minimal immutable identity resulting from a verified Bedrock login chain. */
public record BedrockIdentity(
    UUID identity, String displayName, String xuid, String titleId, PublicKey identityKey) {
  /** Validates all authorization-relevant identity fields. */
  public BedrockIdentity {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(displayName, "displayName");
    Objects.requireNonNull(xuid, "xuid");
    Objects.requireNonNull(titleId, "titleId");
    Objects.requireNonNull(identityKey, "identityKey");
  }
}
