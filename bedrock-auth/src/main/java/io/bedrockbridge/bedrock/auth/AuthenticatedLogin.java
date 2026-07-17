package io.bedrockbridge.bedrock.auth;

import java.util.Map;
import java.util.Objects;

/** Verified Bedrock identity and signed client capability claims. */
public record AuthenticatedLogin(BedrockIdentity identity, Map<String, Object> clientData) {
  /** Defensively freezes authentication output. */
  public AuthenticatedLogin {
    Objects.requireNonNull(identity, "identity");
    clientData = Map.copyOf(clientData);
  }
}
