package io.bedrockbridge.bedrock.login;

import io.bedrockbridge.bedrock.BedrockProtocolLimits;
import io.bedrockbridge.bedrock.BedrockValidationException;
import io.bedrockbridge.bedrock.auth.AuthenticatedLogin;
import io.bedrockbridge.bedrock.auth.BedrockChainVerifier;
import io.bedrockbridge.bedrock.auth.BedrockConnectionRequestDecoder;
import io.bedrockbridge.bedrock.auth.BedrockLoginPayload;
import io.bedrockbridge.bedrock.packet.play.LoginPacket;
import java.util.Objects;

/** Server-side Login decoder and policy gate; no passwords or Microsoft tokens are handled. */
public final class BedrockServerLoginAuthenticator {
  private final BedrockAuthMode mode;
  private final BedrockChainVerifier verifier;
  private final BedrockConnectionRequestDecoder decoder;

  public BedrockServerLoginAuthenticator(
      BedrockAuthMode mode, BedrockChainVerifier verifier, BedrockProtocolLimits limits) {
    this.mode = Objects.requireNonNull(mode, "mode");
    this.verifier = Objects.requireNonNull(verifier, "verifier");
    BedrockProtocolLimits checked = Objects.requireNonNull(limits, "limits");
    decoder = new BedrockConnectionRequestDecoder(checked.maximumLoginBytes());
  }

  /** Decodes and validates one serverbound Login packet under the configured policy. */
  public AuthenticatedLogin authenticate(LoginPacket packet) {
    Objects.requireNonNull(packet, "packet");
    BedrockLoginPayload payload = decoder.decode(packet.connectionRequest());
    return switch (mode) {
      case ONLINE -> verifier.verify(payload);
      case OFFLINE_DENY ->
          throw new BedrockValidationException("Offline Bedrock authentication is disabled");
      case OFFLINE_ALLOW_SELF_SIGNED -> verifier.verifyOfflineSelfSigned(payload);
    };
  }

  public BedrockAuthMode mode() {
    return mode;
  }
}
