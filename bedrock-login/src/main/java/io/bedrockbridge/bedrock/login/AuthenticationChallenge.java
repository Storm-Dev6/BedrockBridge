package io.bedrockbridge.bedrock.login;

import io.bedrockbridge.bedrock.auth.BedrockIdentity;
import java.util.Objects;

/** Verified identity plus server handshake JWT to return to the Bedrock client. */
public record AuthenticationChallenge(BedrockIdentity identity, String handshakeJwt) {
  /** Validates the challenge output. */
  public AuthenticationChallenge {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(handshakeJwt, "handshakeJwt");
  }
}
