package io.bedrockbridge.registry.generator;

import io.bedrockbridge.bedrock.BedrockPacketIds;
import io.bedrockbridge.bedrock.BedrockPlayState;
import io.bedrockbridge.bedrock.BedrockProtocol;
import io.bedrockbridge.bedrock.BedrockProtocolLimits;
import io.bedrockbridge.bedrock.auth.BedrockChainVerifier;
import io.bedrockbridge.bedrock.auth.BedrockConnectionRequestDecoder;
import io.bedrockbridge.bedrock.codec.BedrockBatchCodec;
import io.bedrockbridge.bedrock.codec.BedrockCompressionCodec;
import io.bedrockbridge.bedrock.codec.BedrockPacketFrame;
import io.bedrockbridge.bedrock.codec.BedrockPacketFrameCodec;
import io.bedrockbridge.bedrock.codec.BedrockPlayCodec;
import io.bedrockbridge.bedrock.codec.BedrockProtocol748PacketRegistry;
import io.bedrockbridge.bedrock.codec.CompressionAlgorithm;
import io.bedrockbridge.bedrock.codec.CompressionSettings;
import io.bedrockbridge.bedrock.packet.play.LoginPacket;
import io.bedrockbridge.bedrock.packet.play.NetworkSettingsPacket;
import io.bedrockbridge.bedrock.packet.play.ServerToClientHandshakePacket;
import io.bedrockbridge.network.raknet.RakNetFrame;
import io.bedrockbridge.network.raknet.RakNetFrameCodec;
import io.bedrockbridge.network.raknet.SplitPacketAssembler;
import io.bedrockbridge.protocol.PacketDirection;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bounded raw RakNet relay with a fail-closed authenticated observation boundary.
 *
 * <p>The relay forwards datagrams without rewriting credentials or game payloads. It validates the
 * real client's Login chain before forwarding it, records only packet IDs/state transitions, and
 * extracts the three approved StartGame item fields only when the frame is available in cleartext.
 * Once BDS starts encrypted connected traffic the relay cannot decrypt it without either endpoint's
 * private key, so it stops with an explicit boundary instead of guessing or writing an artifact.
 */
public final class LiveBedrockObserver implements AutoCloseable {
  private static final int MAX_DATAGRAM_BYTES = 65_507;
  private static final int RAKNET_FRAME_DATAGRAM_MAX = 0x8F;
  private static final int GAME_PACKET = 0xFE;
  private static final int MAX_TRACE_EVENTS = 256;

  private final InetSocketAddress listenAddress;
  private final InetSocketAddress bdsAddress;
  private final BedrockChainVerifier chainVerifier;
  private final PathTarget artifact;
  private final Duration timeout;
  private final Clock clock;
  private final BedrockProtocolLimits limits = BedrockProtocolLimits.defaults();
  private final BedrockPacketFrameCodec frameCodec = new BedrockPacketFrameCodec(limits);
  private final BedrockBatchCodec batchCodec = new BedrockBatchCodec(limits);
  private final RakNetFrameCodec rakNetCodec = new RakNetFrameCodec();
  private final SplitPacketAssembler clientSplits =
      new SplitPacketAssembler(64, 4 * 1024 * 1024, Duration.ofSeconds(30));
  private final SplitPacketAssembler serverSplits =
      new SplitPacketAssembler(64, 4 * 1024 * 1024, Duration.ofSeconds(30));
  private final BedrockPlayCodec playCodec =
      new BedrockPlayCodec(
          BedrockProtocol.PLAY_VERSION_748,
          limits,
          BedrockProtocol748PacketRegistry.create(limits));
  private final BedrockConnectionRequestDecoder loginDecoder =
      new BedrockConnectionRequestDecoder(limits.maximumLoginBytes());
  private final List<String> trace = new ArrayList<>();
  private final AtomicBoolean closed = new AtomicBoolean();
  private DatagramSocket socket;
  private InetSocketAddress clientAddress;
  private boolean loginValidated;
  private boolean serverHandshakeSeen;
  private boolean clientHandshakeSeen;
  private BedrockCompressionCodec compression;
  private boolean compressionNegotiated;
  private String state = "WAITING_FOR_CLIENT";
  private Instant lastActivity;

  /**
   * Creates a relay; all sensitive inputs stay in memory and the artifact target must be external.
   */
  public LiveBedrockObserver(
      InetSocketAddress listenAddress,
      InetSocketAddress bdsAddress,
      BedrockChainVerifier chainVerifier,
      java.nio.file.Path artifactPath,
      Duration timeout,
      Clock clock)
      throws IOException {
    this.listenAddress = requireAddress(listenAddress, "listenAddress");
    this.bdsAddress = requireAddress(bdsAddress, "bdsAddress");
    this.chainVerifier = Objects.requireNonNull(chainVerifier, "chainVerifier");
    this.artifact = new PathTarget(Objects.requireNonNull(artifactPath, "artifactPath"));
    RepositoryBoundary.requireOutsideGitWorkTree(this.artifact.path());
    this.timeout = Objects.requireNonNull(timeout, "timeout");
    if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofHours(1)) > 0) {
      throw new IllegalArgumentException("timeout must be between one millisecond and one hour");
    }
    this.clock = Objects.requireNonNull(clock, "clock");
    this.compression = noneCompression();
  }

  /** Runs one bounded observation and returns only non-sensitive summary information. */
  public synchronized Result run() throws IOException {
    if (socket != null) {
      throw new IllegalStateException("Observer has already run");
    }
    socket = new DatagramSocket(listenAddress);
    socket.setSoTimeout(250);
    lastActivity = Instant.now(clock);
    state("LISTENING");
    try {
      while (!closed.get()) {
        if (Duration.between(lastActivity, Instant.now(clock)).compareTo(timeout) >= 0) {
          throw boundary("TIMEOUT_BEFORE_START_GAME");
        }
        DatagramPacket datagram =
            new DatagramPacket(new byte[MAX_DATAGRAM_BYTES], MAX_DATAGRAM_BYTES);
        try {
          socket.receive(datagram);
        } catch (java.net.SocketTimeoutException ignored) {
          continue;
        }
        lastActivity = Instant.now(clock);
        InetSocketAddress source = (InetSocketAddress) datagram.getSocketAddress();
        byte[] bytes = Arrays.copyOf(datagram.getData(), datagram.getLength());
        if (source.equals(bdsAddress)) {
          inspectServer(bytes);
          if (clientAddress != null) {
            send(clientAddress, bytes);
          }
        } else {
          if (clientAddress == null) {
            clientAddress = source;
            state("CLIENT_ADMITTED");
          } else if (!clientAddress.equals(source)) {
            addTrace("IGNORED_SECOND_CLIENT");
            continue;
          }
          inspectClient(bytes);
          send(bdsAddress, bytes);
        }
      }
      throw boundary("CLOSED_BEFORE_START_GAME");
    } catch (ArtifactWritten written) {
      return new Result(state, trace, written.summary);
    } finally {
      close();
    }
  }

  /** Returns a bounded trace containing no payload, token, key, or identity claim. */
  public synchronized List<String> trace() {
    return List.copyOf(trace);
  }

  @Override
  public synchronized void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    if (socket != null) {
      socket.close();
      socket = null;
    }
  }

  /**
   * Extracts and writes a StartGame artifact only after both authentication boundaries are proven.
   */
  static ItemRegistryArtifact.Summary extractAuthenticatedStartGame(
      byte[] encodedFrame,
      boolean loginValidated,
      boolean serverHandshakeSeen,
      PathTarget artifact,
      BedrockProtocolLimits limits)
      throws IOException {
    if (!loginValidated || !serverHandshakeSeen) {
      throw boundary("START_GAME_AUTHENTICATION_BOUNDARY_NOT_PROVEN");
    }
    List<ObservedItem> items = StartGameItemListExtractor.extract(encodedFrame, limits);
    return ItemRegistryArtifact.write(artifact.path(), items);
  }

  private void inspectClient(byte[] datagram) throws IOException {
    Optional<PacketFrames> frames = reassemble(datagram, clientSplits);
    if (frames.isEmpty()) {
      return;
    }
    for (byte[] payload : frames.get().payloads()) {
      inspectGamePayload(PacketDirection.SERVERBOUND, payload);
    }
  }

  private void inspectServer(byte[] datagram) throws IOException {
    Optional<PacketFrames> frames = reassemble(datagram, serverSplits);
    if (frames.isEmpty()) {
      return;
    }
    for (byte[] payload : frames.get().payloads()) {
      inspectGamePayload(PacketDirection.CLIENTBOUND, payload);
    }
  }

  private Optional<PacketFrames> reassemble(byte[] datagram, SplitPacketAssembler assembler) {
    if (datagram.length < 4) {
      return Optional.empty();
    }
    int datagramId = Byte.toUnsignedInt(datagram[0]);
    if (datagramId < 0x80 || datagramId > RAKNET_FRAME_DATAGRAM_MAX) {
      return Optional.empty();
    }
    ByteBuffer input = ByteBuffer.wrap(datagram);
    input.get();
    RakNetFrameCodec.getTriad(input);
    List<byte[]> payloads = new ArrayList<>();
    while (input.hasRemaining()) {
      RakNetFrame frame = rakNetCodec.decode(input);
      assembler
          .add(frame, Instant.now(clock))
          .ifPresent(
              complete -> {
                byte[] payload = new byte[complete.payload().remaining()];
                complete.payload().duplicate().get(payload);
                payloads.add(payload);
              });
    }
    return payloads.isEmpty() ? Optional.empty() : Optional.of(new PacketFrames(payloads));
  }

  private void inspectGamePayload(PacketDirection direction, byte[] payload) throws IOException {
    if (payload.length == 0) {
      return;
    }
    if (clientHandshakeSeen || (serverHandshakeSeen && direction == PacketDirection.CLIENTBOUND)) {
      throw boundary("ENCRYPTED_GAME_PAYLOAD_UNAVAILABLE_NO_ENDPOINT_PRIVATE_KEY");
    }
    if (Byte.toUnsignedInt(payload[0]) != GAME_PACKET) {
      addTrace("RAKNET_PAYLOAD id=" + Byte.toUnsignedInt(payload[0]));
      return;
    }
    byte[] batch = Arrays.copyOfRange(payload, 1, payload.length);
    List<BedrockPacketFrame> packetFrames = decodeBatch(batch);
    for (BedrockPacketFrame frame : packetFrames) {
      int packetId = frame.header().packetId();
      addTrace(
          (direction == PacketDirection.SERVERBOUND ? "OUT" : "IN") + "_PACKET id=" + packetId);
      inspectPacket(direction, frame);
    }
  }

  private List<BedrockPacketFrame> decodeBatch(byte[] encoded) {
    try {
      return batchCodec.decode(encoded);
    } catch (RuntimeException rawFailure) {
      if (!compressionNegotiated) {
        throw rawFailure;
      }
      return batchCodec.decode(compression.decompress(encoded));
    }
  }

  private void inspectPacket(PacketDirection direction, BedrockPacketFrame frame)
      throws IOException {
    int id = frame.header().packetId();
    if (direction == PacketDirection.SERVERBOUND
        && id == BedrockPacketIds.REQUEST_NETWORK_SETTINGS) {
      playCodec.decode(frameCodec.encode(frame), BedrockPlayState.NETWORK_SETTINGS, direction);
      state("NETWORK_SETTINGS_REQUESTED");
      return;
    }
    if (direction == PacketDirection.CLIENTBOUND && id == BedrockPacketIds.NETWORK_SETTINGS) {
      NetworkSettingsPacket settings =
          (NetworkSettingsPacket)
              playCodec
                  .decode(frameCodec.encode(frame), BedrockPlayState.NETWORK_SETTINGS, direction)
                  .packet();
      if (settings.compressionAlgorithm()
          != io.bedrockbridge.bedrock.packet.play.NetworkCompressionAlgorithm.ZLIB) {
        throw boundary("UNSUPPORTED_NETWORK_COMPRESSION");
      }
      compression =
          new BedrockCompressionCodec(
              new CompressionSettings(
                  CompressionAlgorithm.ZLIB,
                  settings.compressionThreshold(),
                  limits.maximumConnectedPayloadBytes(),
                  limits.maximumDecompressedBatchBytes(),
                  limits.maximumCompressionRatio()));
      compressionNegotiated = true;
      state("NETWORK_SETTINGS_RECEIVED");
      return;
    }
    if (direction == PacketDirection.SERVERBOUND && id == BedrockPacketIds.LOGIN) {
      LoginPacket login =
          (LoginPacket)
              playCodec
                  .decode(frameCodec.encode(frame), BedrockPlayState.LOGIN, direction)
                  .packet();
      chainVerifier.verify(loginDecoder.decode(login.connectionRequest()));
      loginValidated = true;
      state("LOGIN_CHAIN_VALIDATED");
      return;
    }
    if (direction == PacketDirection.CLIENTBOUND
        && id == BedrockPacketIds.SERVER_TO_CLIENT_HANDSHAKE) {
      ServerToClientHandshakePacket handshake =
          (ServerToClientHandshakePacket)
              playCodec
                  .decode(frameCodec.encode(frame), BedrockPlayState.AUTHENTICATING, direction)
                  .packet();
      if (handshake.handshakeJwt().isBlank()) {
        throw boundary("EMPTY_SERVER_HANDSHAKE");
      }
      serverHandshakeSeen = true;
      state("SERVER_HANDSHAKE_OBSERVED");
      return;
    }
    if (direction == PacketDirection.SERVERBOUND
        && id == BedrockPacketIds.CLIENT_TO_SERVER_HANDSHAKE) {
      playCodec.decode(frameCodec.encode(frame), BedrockPlayState.AUTHENTICATING, direction);
      clientHandshakeSeen = true;
      state("CLIENT_HANDSHAKE_OBSERVED");
      return;
    }
    if (direction == PacketDirection.CLIENTBOUND && id == BedrockPacketIds.START_GAME) {
      if (clientHandshakeSeen) {
        throw boundary("START_GAME_WAS_ENCRYPTED");
      }
      ItemRegistryArtifact.Summary summary =
          extractAuthenticatedStartGame(
              frameCodec.encode(frame), loginValidated, serverHandshakeSeen, artifact, limits);
      state("START_GAME_ARTIFACT_WRITTEN");
      throw new ArtifactWritten(summary);
    }
  }

  private void send(InetSocketAddress destination, byte[] bytes) throws IOException {
    socket.send(new DatagramPacket(bytes, bytes.length, destination));
  }

  private void state(String value) {
    state = value;
    addTrace("STATE " + value);
  }

  private void addTrace(String value) {
    if (trace.size() < MAX_TRACE_EVENTS) {
      trace.add(value);
    }
  }

  private static CompressionSettings noneSettings() {
    BedrockProtocolLimits limits = BedrockProtocolLimits.defaults();
    return new CompressionSettings(
        CompressionAlgorithm.NONE,
        Integer.MAX_VALUE,
        limits.maximumConnectedPayloadBytes(),
        limits.maximumDecompressedBatchBytes(),
        limits.maximumCompressionRatio());
  }

  private static BedrockCompressionCodec noneCompression() {
    return new BedrockCompressionCodec(noneSettings());
  }

  private static InetSocketAddress requireAddress(InetSocketAddress address, String name) {
    if (address == null || address.getPort() < 1 || address.getPort() > 65_535) {
      throw new IllegalArgumentException(name + " must contain a valid port");
    }
    return address;
  }

  private static ObserverBoundaryException boundary(String reason) {
    return new ObserverBoundaryException(reason);
  }

  /** Summary with no identity, token, key, or packet payload. */
  public record Result(
      String terminalState, List<String> trace, ItemRegistryArtifact.Summary artifact) {
    public Result {
      Objects.requireNonNull(terminalState, "terminalState");
      trace = List.copyOf(trace);
    }
  }

  static final class PathTarget {
    private final java.nio.file.Path path;

    PathTarget(java.nio.file.Path path) {
      this.path = path.toAbsolutePath().normalize();
    }

    java.nio.file.Path path() {
      return path;
    }
  }

  private record PacketFrames(List<byte[]> payloads) {}

  /** Explicit boundary for a packet that cannot be safely observed. */
  public static final class ObserverBoundaryException extends IOException {
    private static final long serialVersionUID = 1L;

    public ObserverBoundaryException(String reason) {
      super(Objects.requireNonNull(reason, "reason"));
    }
  }

  private static final class ArtifactWritten extends IOException {
    private static final long serialVersionUID = 1L;
    private final transient ItemRegistryArtifact.Summary summary;

    private ArtifactWritten(ItemRegistryArtifact.Summary summary) {
      super("START_GAME_ARTIFACT_WRITTEN");
      this.summary = summary;
    }
  }
}
