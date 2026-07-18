package io.bedrockbridge.application.translation;

import io.bedrockbridge.bedrock.BedrockProtocolLimits;
import io.bedrockbridge.bedrock.auth.BedrockChainVerifier;
import io.bedrockbridge.bedrock.crypto.HandshakeJwtSigner;
import io.bedrockbridge.bedrock.login.BedrockAuthMode;
import io.bedrockbridge.bedrock.login.BedrockAuthenticationSession;
import io.bedrockbridge.bedrock.session.ConnectedFrameHandler;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Objects;

/** Creates one complete Bedrock-to-Java play pipeline per admitted UDP endpoint. */
public final class BedrockConnectedSessionFactory {
  private final BedrockProtocolLimits limits;
  private final BedrockChainVerifier verifier;
  private final BedrockAuthMode authMode;
  private final UpstreamConnector upstreamConnector;
  private final BedrockJavaSession.StartGameFrameProvider startGameProvider;

  /** Creates a factory with explicit authentication, upstream, and external StartGame policy. */
  public BedrockConnectedSessionFactory(
      BedrockProtocolLimits limits,
      BedrockChainVerifier verifier,
      BedrockAuthMode authMode,
      UpstreamConnector upstreamConnector,
      BedrockJavaSession.StartGameFrameProvider startGameProvider) {
    this.limits = Objects.requireNonNull(limits, "limits");
    this.verifier = Objects.requireNonNull(verifier, "verifier");
    this.authMode = Objects.requireNonNull(authMode, "authMode");
    this.upstreamConnector = Objects.requireNonNull(upstreamConnector, "upstreamConnector");
    this.startGameProvider = startGameProvider;
  }

  /** Creates a handler whose Java upstream mapping is selected by the Bedrock listener port. */
  public ConnectedFrameHandler createForListener(int listenerPort) {
    BedrockAuthenticationSession authentication =
        new BedrockAuthenticationSession(
            verifier, new SecureRandom(), new HandshakeJwtSigner(), authMode);
    BedrockJavaSession session =
        new BedrockJavaSession(
            limits,
            authentication,
            username -> upstreamConnector.connect(listenerPort, username),
            startGameProvider);
    return new BedrockConnectedPlayAdapter(session);
  }

  /** Opens the statically mapped Java upstream for one Bedrock listener and username. */
  @FunctionalInterface
  public interface UpstreamConnector {
    JavaSessionGateway connect(int listenerPort, String username)
        throws IOException, io.bedrockbridge.application.javawire.JavaWireException;
  }
}
