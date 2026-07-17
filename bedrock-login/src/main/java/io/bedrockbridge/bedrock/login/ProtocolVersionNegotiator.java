package io.bedrockbridge.bedrock.login;

import io.bedrockbridge.bedrock.BedrockProtocol;
import io.bedrockbridge.bedrock.BedrockValidationException;

/** Fail-closed negotiation for the supported Bedrock RakNet transport version. */
public final class ProtocolVersionNegotiator {
  /** Returns the accepted version or throws for any unknown value. */
  public int negotiate(int requestedVersion) {
    if (requestedVersion != BedrockProtocol.RAKNET_PROTOCOL_VERSION) {
      throw new BedrockValidationException(
          "Unsupported RakNet protocol version: " + requestedVersion);
    }
    return requestedVersion;
  }
}
