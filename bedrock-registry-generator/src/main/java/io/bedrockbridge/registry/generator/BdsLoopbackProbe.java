package io.bedrockbridge.registry.generator;

import io.bedrockbridge.bedrock.BedrockPlayState;
import io.bedrockbridge.bedrock.BedrockProtocol;
import io.bedrockbridge.bedrock.BedrockProtocolLimits;
import io.bedrockbridge.bedrock.codec.BedrockBatchCodec;
import io.bedrockbridge.bedrock.codec.BedrockCompressionCodec;
import io.bedrockbridge.bedrock.codec.BedrockDatagramCodec;
import io.bedrockbridge.bedrock.codec.BedrockPacketFrame;
import io.bedrockbridge.bedrock.codec.BedrockPacketFrameCodec;
import io.bedrockbridge.bedrock.codec.BedrockPacketRegistry;
import io.bedrockbridge.bedrock.codec.BedrockPacketValidator;
import io.bedrockbridge.bedrock.codec.BedrockPlayCodec;
import io.bedrockbridge.bedrock.codec.BedrockProtocol748PacketRegistry;
import io.bedrockbridge.bedrock.codec.CompressionAlgorithm;
import io.bedrockbridge.bedrock.codec.CompressionSettings;
import io.bedrockbridge.bedrock.packet.ConnectionRequest;
import io.bedrockbridge.bedrock.packet.ConnectionRequestAccepted;
import io.bedrockbridge.bedrock.packet.NewIncomingConnection;
import io.bedrockbridge.bedrock.packet.OpenConnectionReply1;
import io.bedrockbridge.bedrock.packet.OpenConnectionReply2;
import io.bedrockbridge.bedrock.packet.OpenConnectionRequest1;
import io.bedrockbridge.bedrock.packet.OpenConnectionRequest2;
import io.bedrockbridge.bedrock.packet.play.BedrockPlayPacket;
import io.bedrockbridge.bedrock.packet.play.LoginPacket;
import io.bedrockbridge.bedrock.packet.play.NetworkSettingsPacket;
import io.bedrockbridge.bedrock.packet.play.RequestNetworkSettingsPacket;
import io.bedrockbridge.bedrock.packet.play.ResourcePackClientResponsePacket;
import io.bedrockbridge.bedrock.packet.play.ResourcePackResponse;
import io.bedrockbridge.network.raknet.AckCodec;
import io.bedrockbridge.network.raknet.AckRange;
import io.bedrockbridge.network.raknet.MtuPolicy;
import io.bedrockbridge.network.raknet.RakNetFrame;
import io.bedrockbridge.network.raknet.RakNetFrameCodec;
import io.bedrockbridge.network.raknet.Reliability;
import io.bedrockbridge.network.raknet.SplitPacketAssembler;
import io.bedrockbridge.protocol.Packet;
import io.bedrockbridge.protocol.PacketDirection;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/** Minimal clean-room RakNet client that observes protocol-748 network settings on loopback. */
public final class BdsLoopbackProbe implements AutoCloseable {
  private static final int MTU = 1_492;
  private static final int GAME_PACKET = 0xFE;
  private static final int FRAME_DATAGRAM = 0x84;
  private static final int ACK = 0xC0;
  private static final int MAX_DATAGRAM = 65_507;
  private static final int MAX_FRAME_PAYLOAD = 1_440;

  private final InetSocketAddress server;
  private final long clientGuid;
  private final DatagramSocket socket;
  private final BedrockDatagramCodec handshakeCodec;
  private final RakNetFrameCodec frameCodec = new RakNetFrameCodec();
  private final AckCodec ackCodec = new AckCodec(64);
  private final SplitPacketAssembler splitAssembler =
      new SplitPacketAssembler(64, 4 * 1_024 * 1_024, Duration.ofSeconds(30));
  private final Deque<byte[]> connectedPayloads = new ArrayDeque<>();
  private final ProbeTrace trace = new ProbeTrace();
  private int datagramSequence;
  private int reliableIndex;
  private int orderIndex;

  private BdsLoopbackProbe(InetSocketAddress server) throws IOException {
    if (!server.getAddress().isLoopbackAddress()) {
      throw new IllegalArgumentException("BDS probe target must be loopback");
    }
    this.server = server;
    trace.state("CREATED");
    clientGuid = 0x4244524F434B3734L;
    socket = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    socket.setSoTimeout(10_000);
    handshakeCodec =
        new BedrockDatagramCodec(
            BedrockPacketRegistry.create(),
            new BedrockPacketValidator(new MtuPolicy(576, MTU, MTU)));
  }

  /** Connects only to the supplied loopback port and writes the approved three-field artifact. */
  public static void main(String[] args) throws Exception {
    if (args.length < 2 || args.length > 4) {
      throw new IllegalArgumentException(
          "Usage: <loopback-port> <external-item-artifact> [bds-stdout] [bds-stderr]");
    }
    int port = Integer.parseInt(args[0]);
    Path output = Path.of(args[1]).toAbsolutePath().normalize();
    Path stdout = args.length >= 3 ? Path.of(args[2]).toAbsolutePath().normalize() : null;
    Path stderr = args.length >= 4 ? Path.of(args[3]).toAbsolutePath().normalize() : null;
    try (var probe =
        new BdsLoopbackProbe(new InetSocketAddress(InetAddress.getLoopbackAddress(), port))) {
      try {
        Observation observation = probe.observeStartGame();
        List<ObservedItem> items = observation.itemRegistry(BedrockProtocolLimits.defaults());
        ItemRegistryArtifact.Summary summary = ItemRegistryArtifact.write(output, items);
        System.out.printf(
            "protocol=%d threshold=%d algorithm=%s throttle=%s itemCount=%d artifactBytes=%d artifactSha256=%s%n",
            BedrockProtocol.NETWORK_PROTOCOL_748,
            observation.settings().compressionThreshold(),
            observation.settings().compressionAlgorithm(),
            observation.settings().clientThrottleEnabled(),
            summary.itemCount(),
            summary.byteCount(),
            summary.sha256());
      } catch (Exception failure) {
        probe.printDiagnostics(failure);
        probe.printServerLogs(stdout, stderr);
        throw failure;
      }
    }
  }

  /** Performs the loopback login flow and keeps the observed frame in memory only. */
  public Observation observeStartGame() throws Exception {
    trace.state("NETWORK_SETTINGS");
    NetworkSettingsPacket settings = observeNetworkSettings();
    if (settings.compressionAlgorithm().wireValue() != 0) {
      throw new IOException("Protocol-748 probe supports only the observed ZLIB algorithm");
    }
    BedrockProtocolLimits limits = BedrockProtocolLimits.defaults();
    BedrockPlayCodec playCodec =
        new BedrockPlayCodec(
            BedrockProtocol.PLAY_VERSION_748,
            limits,
            BedrockProtocol748PacketRegistry.create(limits));
    BedrockCompressionCodec compression =
        new BedrockCompressionCodec(
            new CompressionSettings(
                CompressionAlgorithm.ZLIB,
                settings.compressionThreshold(),
                limits.maximumConnectedPayloadBytes(),
                limits.maximumDecompressedBatchBytes(),
                limits.maximumCompressionRatio()));

    OfflineLoginMaterial.Generated loginMaterial =
        OfflineLoginMaterial.generate(server.getPort(), Instant.now());
    OfflineLoginMaterial.verify(loginMaterial, Instant.now());
    trace.state("LOGIN_PAYLOAD_VERIFIED");
    byte[] login =
        playCodec.encode(
            new LoginPacket(
                BedrockProtocol.NETWORK_PROTOCOL_748, loginMaterial.connectionRequest()),
            BedrockPlayState.LOGIN,
            0,
            0);
    trace.state("LOGIN_SENT");
    sendCompressedGameBatch(login, limits, compression);

    for (int attempts = 0; attempts < 128; attempts++) {
      List<BedrockPacketFrame> frames = receiveCompressedBatch(limits, compression);
      for (BedrockPacketFrame frame : frames) {
        int packetId = frame.header().packetId();
        trace.packet("IN", packetId, frame.payloadLength());
        if (packetId == 6) {
          trace.state("RESOURCE_PACKS");
          sendPlayPacket(
              playCodec,
              new ResourcePackClientResponsePacket(
                  ResourcePackResponse.DOWNLOADING_FINISHED, List.of()),
              BedrockPlayState.RESOURCE_PACKS,
              limits,
              compression);
        } else if (packetId == 7) {
          sendPlayPacket(
              playCodec,
              new ResourcePackClientResponsePacket(
                  ResourcePackResponse.RESOURCE_PACK_STACK_FINISHED, List.of()),
              BedrockPlayState.RESOURCE_PACKS,
              limits,
              compression);
        } else if (packetId == 11) {
          trace.state("START_GAME_OBSERVED");
          return new Observation(settings, new BedrockPacketFrameCodec(limits).encode(frame));
        } else if (packetId == 5) {
          trace.disconnect("DISCONNECT_PACKET");
          throw new IOException("BDS disconnected the synthetic loopback client");
        } else if (packetId == 3) {
          trace.state("ENCRYPTION_REQUESTED");
          throw new IOException("BDS requested an encrypted online-mode handshake");
        }
      }
    }
    trace.timeout("StartGame after login/resource-pack flow");
    throw new SocketTimeoutException("BDS did not send StartGame");
  }

  private void printDiagnostics(Exception failure) {
    System.err.println("BDS_LOOPBACK_DIAGNOSTICS failure=" + failure.getClass().getSimpleName());
    System.err.println("BDS_LOOPBACK_DIAGNOSTICS message=" + safeMessage(failure));
    for (String event : trace.snapshot()) {
      System.err.println("BDS_LOOPBACK_DIAGNOSTICS " + event);
    }
  }

  private void printServerLogs(Path stdout, Path stderr) {
    printServerLog("stdout", stdout);
    printServerLog("stderr", stderr);
  }

  private static void printServerLog(String stream, Path path) {
    if (path == null) {
      return;
    }
    try {
      for (String line : BdsServerLogReader.relevant(path)) {
        System.err.println("BDS_LOOPBACK_DIAGNOSTICS BDS_" + stream + " " + line);
      }
    } catch (IOException failure) {
      System.err.println(
          "BDS_LOOPBACK_DIAGNOSTICS BDS_" + stream + "_READ_ERROR " + safeMessage(failure));
    }
  }

  private static String safeMessage(Exception failure) {
    String message = failure.getMessage();
    return message == null ? "<none>" : message.replaceAll("[\\r\\n]+", " ");
  }

  private void sendPlayPacket(
      BedrockPlayCodec playCodec,
      BedrockPlayPacket packet,
      BedrockPlayState state,
      BedrockProtocolLimits limits,
      BedrockCompressionCodec compression)
      throws IOException {
    sendCompressedGameBatch(playCodec.encode(packet, state, 0, 0), limits, compression);
  }

  private void sendCompressedGameBatch(
      byte[] packet, BedrockProtocolLimits limits, BedrockCompressionCodec compression)
      throws IOException {
    int packetId = new BedrockPacketFrameCodec(limits).decode(packet).header().packetId();
    trace.packet("OUT", packetId, packet.length);
    byte[] batch =
        new BedrockBatchCodec(limits)
            .encode(List.of(new BedrockPacketFrameCodec(limits).decode(packet)));
    byte[] compressed = compression.compress(batch);
    trace.note(
        "OUT_BATCH packetId="
            + packetId
            + " packetBytes="
            + packet.length
            + " batchBytes="
            + batch.length
            + " compressedBytes="
            + compressed.length);
    byte[] connected = new byte[compressed.length + 1];
    connected[0] = (byte) GAME_PACKET;
    System.arraycopy(compressed, 0, connected, 1, compressed.length);
    sendConnected(connected);
  }

  private List<BedrockPacketFrame> receiveCompressedBatch(
      BedrockProtocolLimits limits, BedrockCompressionCodec compression) throws IOException {
    byte[] connected = receiveGamePacket();
    byte[] compressed = Arrays.copyOfRange(connected, 1, connected.length);
    return new BedrockBatchCodec(limits).decode(compression.decompress(compressed));
  }

  private NetworkSettingsPacket observeNetworkSettings() throws IOException {
    OpenConnectionReply1 first =
        (OpenConnectionReply1)
            exchangeOffline(
                new OpenConnectionRequest1(BedrockProtocol.RAKNET_PROTOCOL_VERSION, MTU));
    OpenConnectionReply2 second =
        (OpenConnectionReply2)
            exchangeOffline(new OpenConnectionRequest2(server, first.mtu(), clientGuid));
    if (second.mtu() != first.mtu()) {
      throw new IOException("BDS changed the negotiated MTU between replies");
    }

    long requestTime = System.currentTimeMillis();
    sendConnected(encodeHandshake(new ConnectionRequest(clientGuid, requestTime, false)));
    ConnectionRequestAccepted accepted = receiveConnectionAccepted();
    sendConnected(
        encodeHandshake(
            new NewIncomingConnection(
                server,
                accepted.systemAddresses(),
                accepted.requestTime(),
                accepted.acceptedTime())));

    BedrockProtocolLimits limits = BedrockProtocolLimits.defaults();
    BedrockPlayCodec playCodec =
        new BedrockPlayCodec(
            BedrockProtocol.PLAY_VERSION_748,
            limits,
            BedrockProtocol748PacketRegistry.create(limits));
    byte[] request =
        playCodec.encode(
            new RequestNetworkSettingsPacket(BedrockProtocol.NETWORK_PROTOCOL_748),
            BedrockPlayState.NETWORK_SETTINGS,
            0,
            0);
    trace.packet(
        "OUT",
        new BedrockPacketFrameCodec(limits).decode(request).header().packetId(),
        request.length);
    sendConnected(gameBatch(request, limits));
    byte[] response = receiveGamePacket();
    List<BedrockPacketFrame> frames =
        new BedrockBatchCodec(limits).decode(Arrays.copyOfRange(response, 1, response.length));
    if (frames.size() != 1) {
      throw new IOException("Expected one NetworkSettings packet");
    }
    byte[] encoded = new BedrockPacketFrameCodec(limits).encode(frames.getFirst());
    BedrockPlayPacket decoded =
        playCodec
            .decode(encoded, BedrockPlayState.NETWORK_SETTINGS, PacketDirection.CLIENTBOUND)
            .packet();
    if (!(decoded instanceof NetworkSettingsPacket settings)) {
      throw new IOException("BDS did not return NetworkSettings");
    }
    trace.packet("IN", frames.getFirst().header().packetId(), encoded.length);
    trace.state("NETWORK_SETTINGS_RECEIVED");
    return settings;
  }

  private Packet exchangeOffline(Packet request) throws IOException {
    trace.note("OUT_RAKNET " + request.getClass().getSimpleName());
    sendRaw(encodeHandshake(request));
    byte[] reply = receiveRaw();
    Packet decoded = handshakeCodec.decode(ByteBuffer.wrap(reply), PacketDirection.CLIENTBOUND);
    trace.note("IN_RAKNET " + decoded.getClass().getSimpleName());
    return decoded;
  }

  private byte[] encodeHandshake(Packet packet) {
    ByteBuffer output = ByteBuffer.allocate(MTU);
    handshakeCodec.encode(packet, output);
    return Arrays.copyOf(output.array(), output.position());
  }

  private ConnectionRequestAccepted receiveConnectionAccepted() throws IOException {
    Instant deadline = Instant.now().plusSeconds(30);
    while (Instant.now().isBefore(deadline)) {
      byte[] payload = receiveConnected();
      if (payload == null) {
        continue;
      }
      Packet decoded = handshakeCodec.decode(ByteBuffer.wrap(payload), PacketDirection.CLIENTBOUND);
      trace.note("IN_RAKNET " + decoded.getClass().getSimpleName());
      if (decoded instanceof ConnectionRequestAccepted accepted) {
        trace.state("RAKNET_CONNECTED");
        return accepted;
      }
    }
    trace.timeout("ConnectionRequestAccepted");
    throw new SocketTimeoutException("BDS did not accept the connected RakNet request");
  }

  private byte[] receiveGamePacket() throws IOException {
    Instant deadline = Instant.now().plusSeconds(30);
    while (Instant.now().isBefore(deadline)) {
      byte[] payload = receiveConnected();
      if (payload != null && Byte.toUnsignedInt(payload[0]) == GAME_PACKET) {
        return payload;
      }
      if (payload != null) {
        if (Byte.toUnsignedInt(payload[0]) == 0 && payload.length == 9) {
          sendConnectedPong(payload);
          continue;
        }
        trace.note(
            "IN_RAKNET_CONTROL id="
                + Byte.toUnsignedInt(payload[0])
                + " payloadBytes="
                + payload.length);
      }
    }
    trace.timeout("game packet");
    throw new SocketTimeoutException("BDS did not return a game packet");
  }

  private void sendConnectedPong(byte[] ping) throws IOException {
    ByteBuffer pong = ByteBuffer.allocate(17);
    pong.put((byte) 0x03).put(ping, 1, Long.BYTES).putLong(System.currentTimeMillis());
    ByteBuffer datagram = ByteBuffer.allocate(MTU);
    datagram.put((byte) FRAME_DATAGRAM);
    RakNetFrameCodec.putTriad(datagram, datagramSequence++);
    frameCodec.encode(
        new RakNetFrame(Reliability.UNRELIABLE, 0, 0, 0, 0, null, ByteBuffer.wrap(pong.array())),
        datagram);
    sendRaw(Arrays.copyOf(datagram.array(), datagram.position()));
  }

  private void sendConnected(byte[] payload) throws IOException {
    int sharedOrderIndex = orderIndex++;
    int sharedReliableIndex = reliableIndex++;
    int splitId = sharedOrderIndex & 0xFFFF;
    int count = Math.ceilDiv(payload.length, MAX_FRAME_PAYLOAD);
    for (int index = 0, offset = 0; index < count; index++) {
      int length = Math.min(MAX_FRAME_PAYLOAD, payload.length - offset);
      RakNetFrame.SplitInfo split =
          count == 1 ? null : new RakNetFrame.SplitInfo(count, splitId, index);
      ByteBuffer datagram = ByteBuffer.allocate(MTU);
      datagram.put((byte) FRAME_DATAGRAM);
      RakNetFrameCodec.putTriad(datagram, datagramSequence++);
      frameCodec.encode(
          new RakNetFrame(
              Reliability.RELIABLE_ORDERED,
              sharedReliableIndex,
              0,
              sharedOrderIndex,
              0,
              split,
              ByteBuffer.wrap(payload, offset, length)),
          datagram);
      sendRaw(Arrays.copyOf(datagram.array(), datagram.position()));
      offset += length;
    }
  }

  private byte[] receiveConnected() throws IOException {
    if (!connectedPayloads.isEmpty()) {
      return connectedPayloads.removeFirst();
    }
    byte[] datagram = receiveRaw();
    int id = Byte.toUnsignedInt(datagram[0]);
    trace.note("IN_DATAGRAM id=" + id + " bytes=" + datagram.length);
    if (id == ACK || id == 0xA0) {
      return null;
    }
    if (id < 0x80 || id > 0x8F) {
      throw new IOException("Unexpected RakNet datagram type: " + id);
    }
    ByteBuffer input = ByteBuffer.wrap(datagram);
    input.get();
    int sequence = RakNetFrameCodec.getTriad(input);
    sendAck(sequence);
    while (input.hasRemaining()) {
      RakNetFrame frame = frameCodec.decode(input);
      splitAssembler
          .add(frame, Instant.now())
          .ifPresent(
              complete -> {
                ByteBuffer payload = complete.payload();
                byte[] result = new byte[payload.remaining()];
                payload.get(result);
                connectedPayloads.addLast(result);
              });
    }
    return connectedPayloads.isEmpty() ? null : connectedPayloads.removeFirst();
  }

  /** Transient protocol observation; frame bytes are never written by this class. */
  public static final class Observation {
    private final NetworkSettingsPacket settings;
    private final byte[] startGameFrame;

    public Observation(NetworkSettingsPacket settings, byte[] startGameFrame) {
      if (settings == null || startGameFrame == null) {
        throw new NullPointerException("observation fields");
      }
      this.settings = settings;
      this.startGameFrame = startGameFrame.clone();
    }

    public NetworkSettingsPacket settings() {
      return settings;
    }

    public byte[] startGameFrame() {
      return startGameFrame.clone();
    }

    /**
     * Parses only the three protocol-748 item-list fields from this transient frame.
     *
     * <p>The returned list is newly allocated and contains no frame bytes or other packet fields.
     */
    public List<ObservedItem> itemRegistry(BedrockProtocolLimits limits) {
      return List.copyOf(StartGameItemListExtractor.extract(startGameFrame, limits));
    }
  }

  private void sendAck(int sequence) throws IOException {
    ByteBuffer output = ByteBuffer.allocate(16);
    output.put((byte) ACK);
    ackCodec.encode(List.of(new AckRange(sequence, sequence)), output);
    sendRaw(Arrays.copyOf(output.array(), output.position()));
  }

  private static byte[] gameBatch(byte[] packet, BedrockProtocolLimits limits) {
    BedrockPacketFrame frame = new BedrockPacketFrameCodec(limits).decode(packet);
    byte[] batch = new BedrockBatchCodec(limits).encode(List.of(frame));
    byte[] connected = new byte[batch.length + 1];
    connected[0] = (byte) GAME_PACKET;
    System.arraycopy(batch, 0, connected, 1, batch.length);
    return connected;
  }

  private void sendRaw(byte[] bytes) throws IOException {
    socket.send(new DatagramPacket(bytes, bytes.length, server));
  }

  private static final class ProbeTrace {
    private final List<String> events = new ArrayList<>();

    void state(String state) {
      events.add("STATE " + state);
    }

    void packet(String direction, int packetId, int bytes) {
      events.add(direction + "_PACKET id=" + packetId + " bytes=" + bytes);
    }

    void note(String event) {
      events.add(event);
    }

    void timeout(String boundary) {
      events.add("TIMEOUT boundary=" + boundary);
    }

    void disconnect(String reason) {
      events.add("DISCONNECT reason=" + reason);
    }

    List<String> snapshot() {
      return List.copyOf(events);
    }
  }

  private byte[] receiveRaw() throws IOException {
    byte[] bytes = new byte[MAX_DATAGRAM];
    DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
    socket.receive(packet);
    if (!packet.getSocketAddress().equals(server)) {
      throw new IOException("Received datagram from a non-loopback BDS endpoint");
    }
    return Arrays.copyOf(bytes, packet.getLength());
  }

  @Override
  public void close() {
    socket.close();
  }
}
