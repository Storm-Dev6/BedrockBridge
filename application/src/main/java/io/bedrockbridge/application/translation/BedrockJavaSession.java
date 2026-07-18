package io.bedrockbridge.application.translation;

import io.bedrockbridge.application.javawire.JavaUpstreamDisconnect;
import io.bedrockbridge.application.javawire.JavaWireException;
import io.bedrockbridge.application.javawire.JavaWorldState;
import io.bedrockbridge.bedrock.BedrockPlayState;
import io.bedrockbridge.bedrock.BedrockProtocol;
import io.bedrockbridge.bedrock.BedrockProtocolLimits;
import io.bedrockbridge.bedrock.BedrockValidationException;
import io.bedrockbridge.bedrock.auth.AuthenticatedLogin;
import io.bedrockbridge.bedrock.auth.BedrockConnectionRequestDecoder;
import io.bedrockbridge.bedrock.auth.BedrockIdentity;
import io.bedrockbridge.bedrock.codec.BedrockPacketFrame;
import io.bedrockbridge.bedrock.codec.BedrockPacketFrameCodec;
import io.bedrockbridge.bedrock.codec.BedrockPlayCodec;
import io.bedrockbridge.bedrock.codec.BedrockProtocol748PacketRegistry;
import io.bedrockbridge.bedrock.crypto.HandshakeJwtSigner;
import io.bedrockbridge.bedrock.login.AuthenticationChallenge;
import io.bedrockbridge.bedrock.login.BedrockAuthenticationSession;
import io.bedrockbridge.bedrock.login.BedrockPlayStateMachine;
import io.bedrockbridge.bedrock.packet.play.BedrockPlayPacket;
import io.bedrockbridge.bedrock.packet.play.ClientToServerHandshakePacket;
import io.bedrockbridge.bedrock.packet.play.DisconnectPacket;
import io.bedrockbridge.bedrock.packet.play.LoginPacket;
import io.bedrockbridge.bedrock.packet.play.NetworkCompressionAlgorithm;
import io.bedrockbridge.bedrock.packet.play.NetworkSettingsPacket;
import io.bedrockbridge.bedrock.packet.play.RequestNetworkSettingsPacket;
import io.bedrockbridge.bedrock.packet.play.ResourcePackClientResponsePacket;
import io.bedrockbridge.bedrock.packet.play.ResourcePackStackPacket;
import io.bedrockbridge.bedrock.packet.play.ServerToClientHandshakePacket;
import io.bedrockbridge.protocol.PacketDirection;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Coordinates the Bedrock control flow with a Java world-ready upstream session. */
public final class BedrockJavaSession implements AutoCloseable {
  private final BedrockProtocolLimits limits;
  private final BedrockPlayStateMachine playState = new BedrockPlayStateMachine();
  private final BedrockConnectionRequestDecoder loginDecoder;
  private final BedrockAuthenticationSession authentication;
  private final JavaConnector connector;
  private final StartGameFrameProvider startGameProvider;
  private final BedrockPacketFrameCodec frameCodec;
  private JavaSessionGateway upstream;
  private AuthenticatedLogin authenticated;
  private AuthenticationChallenge challenge;

  /** Creates a fail-closed session; a missing StartGame provider is an explicit external block. */
  public BedrockJavaSession(
      BedrockProtocolLimits limits,
      BedrockAuthenticationSession authentication,
      JavaConnector connector,
      StartGameFrameProvider startGameProvider) {
    this.limits = Objects.requireNonNull(limits, "limits");
    this.loginDecoder = new BedrockConnectionRequestDecoder(limits.maximumLoginBytes());
    this.authentication = Objects.requireNonNull(authentication, "authentication");
    this.connector = Objects.requireNonNull(connector, "connector");
    this.startGameProvider = startGameProvider;
    frameCodec = new BedrockPacketFrameCodec(limits);
  }

  /** Creates a session with the standard protocol-748 limits and a fresh online auth session. */
  public static BedrockJavaSession create(
      BedrockProtocolLimits limits,
      io.bedrockbridge.bedrock.auth.BedrockChainVerifier verifier,
      JavaConnector connector,
      StartGameFrameProvider startGameProvider) {
    return new BedrockJavaSession(
        limits,
        new BedrockAuthenticationSession(verifier, new SecureRandom(), new HandshakeJwtSigner()),
        connector,
        startGameProvider);
  }

  /** Accepts one typed serverbound control packet and returns bounded clientbound output. */
  public synchronized BedrockSessionOutput receive(BedrockPlayPacket packet) {
    Objects.requireNonNull(packet, "packet");
    try {
      if (packet instanceof RequestNetworkSettingsPacket request) {
        playState.receive(request);
        return output(
            new NetworkSettingsPacket(512, NetworkCompressionAlgorithm.ZLIB, false, 0, 0.0f));
      }
      if (packet instanceof LoginPacket login) {
        return receiveLogin(login);
      }
      if (packet instanceof ClientToServerHandshakePacket) {
        playState.receive(packet);
        authentication.confirmClientHandshake();
        List<BedrockPlayPacket> packets = new ArrayList<>();
        packets.addAll(upstream.resourcePackFlowStart());
        packets.add(
            new ResourcePackStackPacket(
                false,
                List.of(),
                List.of(),
                BedrockProtocol.PLAY_VERSION_748.name(),
                List.of(),
                false,
                false));
        return output(packets);
      }
      if (packet instanceof ResourcePackClientResponsePacket response) {
        playState.receive(response);
        if (playState.state() == BedrockPlayState.STARTING_PLAY) {
          return sendStartGame();
        }
        return output();
      }
      throw new BedrockValidationException("Unsupported Bedrock login-flow packet");
    } catch (RuntimeException | IOException | JavaWireException failure) {
      return reject(failure);
    }
  }

  /** Decodes one framed serverbound packet using the current protocol state. */
  public synchronized BedrockSessionOutput receiveFrame(byte[] encodedFrame) {
    Objects.requireNonNull(encodedFrame, "encodedFrame");
    try {
      BedrockPlayCodec codec =
          new BedrockPlayCodec(
              BedrockProtocol.PLAY_VERSION_748,
              limits,
              BedrockProtocol748PacketRegistry.create(limits));
      return receive(
          codec.decode(encodedFrame, playState.state(), PacketDirection.SERVERBOUND).packet());
    } catch (RuntimeException failure) {
      return reject(failure);
    }
  }

  /** Returns the current state-machine state. */
  public synchronized BedrockPlayState state() {
    return playState.state();
  }

  /** Returns the verified Bedrock identity after Login has been accepted. */
  public synchronized BedrockIdentity identity() {
    if (authenticated == null) {
      throw new BedrockValidationException("Bedrock identity is not authenticated");
    }
    return authenticated.identity();
  }

  /** Returns whether the post-handshake Bedrock cipher is active for connected traffic. */
  public synchronized boolean encryptionActive() {
    return authentication.state()
        == io.bedrockbridge.bedrock.login.AuthenticationState.AUTHENTICATED;
  }

  /** Decrypts one connected payload after the client handshake, or copies plaintext setup data. */
  public synchronized byte[] decryptConnected(byte[] payload) {
    Objects.requireNonNull(payload, "payload");
    return encryptionActive() ? authentication.decrypt(payload) : payload.clone();
  }

  /** Encrypts one connected payload after the client handshake, or copies plaintext setup data. */
  public synchronized byte[] encryptConnected(byte[] payload) {
    Objects.requireNonNull(payload, "payload");
    return encryptionActive() ? authentication.encrypt(payload) : payload.clone();
  }

  /** Returns the Java world state after the bounded Java Play Login boundary. */
  public synchronized JavaWorldState worldState() {
    if (upstream == null) {
      throw new BedrockValidationException("Java upstream is not connected");
    }
    return upstream.worldState();
  }

  /** Pumps one translated Java PLAY packet after the Bedrock StartGame boundary. */
  public synchronized BedrockSessionOutput pumpJavaOnce() {
    if (playState.state() != BedrockPlayState.PLAY_READY) {
      return reject(
          new BedrockValidationException(
              "Java PLAY pumping requires the Bedrock session to be PLAY_READY"));
    }
    try {
      List<BedrockPlayPacket> packets = upstream.pumpPlayOnce();
      if (packets.stream().anyMatch(DisconnectPacket.class::isInstance)) {
        playState.beginDisconnect();
      }
      return output(packets);
    } catch (RuntimeException | IOException | JavaWireException failure) {
      return reject(failure);
    }
  }

  @Override
  public synchronized void close() {
    authentication.close();
    if (upstream != null) {
      try {
        upstream.close();
      } catch (IOException ignored) {
        // Session cleanup remains deterministic even if the TCP close reports an error.
      }
      upstream = null;
    }
  }

  private BedrockSessionOutput receiveLogin(LoginPacket login)
      throws IOException, JavaWireException {
    playState.receive(login);
    authenticated = authenticationResult(login);
    String username = authenticated.identity().displayName();
    if (username.isBlank() || username.length() > 16) {
      throw new BedrockValidationException(
          "Bedrock display name cannot be used as a Java username");
    }
    upstream = Objects.requireNonNull(connector.connect(username), "Java connector returned null");
    upstream.awaitWorldReady();
    List<BedrockPlayPacket> packets = new ArrayList<>(upstream.loginPackets());
    packets.add(new ServerToClientHandshakePacket(challenge.handshakeJwt()));
    return output(packets);
  }

  private AuthenticatedLogin authenticationResult(LoginPacket login) {
    challenge = authentication.authenticate(loginDecoder.decode(login.connectionRequest()));
    return new AuthenticatedLogin(challenge.identity(), java.util.Map.of());
  }

  private BedrockSessionOutput sendStartGame() throws IOException {
    if (startGameProvider == null) {
      throw new BedrockValidationException("BLOCKED_EXTERNAL_OFFICIAL_ARTIFACT");
    }
    byte[] frame = startGameProvider.build(identity(), worldState());
    if (frame == null || frame.length == 0 || frame.length > limits.maximumPacketBytes()) {
      throw new BedrockValidationException("StartGame provider returned an invalid frame");
    }
    BedrockPacketFrame decoded = frameCodec.decode(frame);
    if (decoded.header().packetId() != 11 || decoded.payloadLength() == 0) {
      throw new BedrockValidationException("StartGame provider returned a non-StartGame packet");
    }
    playState.startGameSent();
    return new BedrockSessionOutput(List.of(), frame);
  }

  private BedrockSessionOutput reject(Exception failure) {
    String message;
    if (failure.getMessage() != null && failure.getMessage().startsWith("BLOCKED_EXTERNAL")) {
      message = "BLOCKED_EXTERNAL_OFFICIAL_ARTIFACT";
    } else if (failure instanceof JavaUpstreamDisconnect disconnect) {
      message = disconnect.reasonJson();
    } else {
      message = "Bedrock session rejected";
    }
    if (playState.state() != BedrockPlayState.DISCONNECTING
        && playState.state() != BedrockPlayState.DISCONNECTED) {
      playState.beginDisconnect();
    }
    return output(new DisconnectPacket(0, false, message, ""));
  }

  private static BedrockSessionOutput output(BedrockPlayPacket... packets) {
    return new BedrockSessionOutput(List.of(packets), null);
  }

  private static BedrockSessionOutput output(List<BedrockPlayPacket> packets) {
    return new BedrockSessionOutput(packets, null);
  }

  private static BedrockSessionOutput output() {
    return new BedrockSessionOutput(List.of(), null);
  }

  @FunctionalInterface
  public interface JavaConnector {
    JavaSessionGateway connect(String username) throws IOException, JavaWireException;
  }

  @FunctionalInterface
  public interface StartGameFrameProvider {
    byte[] build(BedrockIdentity identity, JavaWorldState worldState) throws IOException;
  }
}
