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
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

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
  private int datagramSequence;
  private int reliableIndex;
  private int orderIndex;

  private BdsLoopbackProbe(InetSocketAddress server) throws IOException {
    if (!server.getAddress().isLoopbackAddress()) {
      throw new IllegalArgumentException("BDS probe target must be loopback");
    }
    this.server = server;
    clientGuid = 0x4244524F434B3734L;
    socket = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    socket.setSoTimeout(10_000);
    handshakeCodec =
        new BedrockDatagramCodec(
            BedrockPacketRegistry.create(),
            new BedrockPacketValidator(new MtuPolicy(576, MTU, MTU)));
  }

  /** Connects only to the supplied loopback port and reports observation metadata. */
  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      throw new IllegalArgumentException("Usage: <loopback-port>");
    }
    int port = Integer.parseInt(args[0]);
    try (var probe =
        new BdsLoopbackProbe(new InetSocketAddress(InetAddress.getLoopbackAddress(), port))) {
      Observation observation = probe.observeStartGame();
      System.out.printf(
          "protocol=%d threshold=%d algorithm=%s throttle=%s startGameBytes=%d%n",
          BedrockProtocol.NETWORK_PROTOCOL_748,
          observation.settings().compressionThreshold(),
          observation.settings().compressionAlgorithm(),
          observation.settings().clientThrottleEnabled(),
          observation.startGameFrame().length);
    }
  }

  /** Performs the loopback login flow and keeps the observed frame in memory only. */
  public Observation observeStartGame() throws Exception {
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

    byte[] login =
        playCodec.encode(
            new LoginPacket(
                BedrockProtocol.NETWORK_PROTOCOL_748,
                createSyntheticConnectionRequest(server.getPort())),
            BedrockPlayState.LOGIN,
            0,
            0);
    sendCompressedGameBatch(login, limits, compression);

    for (int attempts = 0; attempts < 128; attempts++) {
      List<BedrockPacketFrame> frames = receiveCompressedBatch(limits, compression);
      for (BedrockPacketFrame frame : frames) {
        int packetId = frame.header().packetId();
        System.out.printf(
            "clientboundPacket=%d payloadBytes=%d%n", packetId, frame.payloadLength());
        if (packetId == 6) {
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
          return new Observation(settings, new BedrockPacketFrameCodec(limits).encode(frame));
        } else if (packetId == 5) {
          throw new IOException("BDS disconnected the synthetic loopback client");
        } else if (packetId == 3) {
          throw new IOException("BDS requested an encrypted online-mode handshake");
        }
      }
    }
    throw new SocketTimeoutException("BDS did not send StartGame");
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
    byte[] batch =
        new BedrockBatchCodec(limits)
            .encode(List.of(new BedrockPacketFrameCodec(limits).decode(packet)));
    byte[] compressed = compression.compress(batch);
    System.out.printf(
        "serverboundPacketBytes=%d batchBytes=%d compressedBytes=%d%n",
        packet.length, batch.length, compressed.length);
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
    return settings;
  }

  private Packet exchangeOffline(Packet request) throws IOException {
    sendRaw(encodeHandshake(request));
    byte[] reply = receiveRaw();
    return handshakeCodec.decode(ByteBuffer.wrap(reply), PacketDirection.CLIENTBOUND);
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
      if (decoded instanceof ConnectionRequestAccepted accepted) {
        return accepted;
      }
    }
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
        System.out.printf(
            "connectedControl=%d payloadBytes=%d%n",
            Byte.toUnsignedInt(payload[0]), payload.length);
      }
    }
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

  private static byte[] createSyntheticConnectionRequest(int port) throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp384r1"));
    KeyPair identity = generator.generateKeyPair();
    String publicKey = Base64.getEncoder().encodeToString(identity.getPublic().getEncoded());
    long now = Instant.now().getEpochSecond();
    String uuid = UUID.randomUUID().toString();
    String chainPayload =
        "{\"certificateAuthority\":true,\"exp\":"
            + (now + 3_600)
            + ",\"extraData\":{\"displayName\":\"BedrockBridgeProbe\",\"identity\":\""
            + uuid
            + "\",\"XUID\":\"\"},\"identityPublicKey\":\""
            + publicKey
            + "\",\"nbf\":"
            + (now - 60)
            + "}";
    String chainToken = signJwt(identity, publicKey, chainPayload);
    String chainJson = "{\"chain\":[\"" + chainToken + "\"]}";

    byte[] skin = new byte[64 * 64 * 4];
    for (int index = 3; index < skin.length; index += 4) {
      skin[index] = (byte) 0xFF;
    }
    String skinData = Base64.getEncoder().encodeToString(skin);
    String resourcePatch =
        Base64.getEncoder()
            .encodeToString(
                "{\"geometry\":{\"default\":\"geometry.humanoid.custom\"}}"
                    .getBytes(StandardCharsets.UTF_8));
    String emptyObject = Base64.getEncoder().encodeToString("{}".getBytes(StandardCharsets.UTF_8));
    String clientPayload =
        "{\"AnimatedImageData\":[],\"ArmSize\":\"wide\",\"CapeData\":\"\","
            + "\"CapeId\":\"\",\"CapeImageHeight\":0,\"CapeImageWidth\":0,"
            + "\"CapeOnClassicSkin\":false,\"ClientRandomId\":748,"
            + "\"CompatibleWithClientSideChunkGen\":false,\"CurrentInputMode\":1,"
            + "\"DefaultInputMode\":1,\"DeviceId\":\""
            + uuid
            + "\",\"DeviceModel\":\"BedrockBridge\",\"DeviceOS\":8,"
            + "\"GameVersion\":\"1.21.40\",\"GuiScale\":0,\"IsEditorMode\":false,"
            + "\"LanguageCode\":\"en_US\",\"OverrideSkin\":false,"
            + "\"PersonaPieces\":[],\"PersonaSkin\":false,\"PieceTintColors\":[],"
            + "\"PlatformOfflineId\":\"\",\"PlatformOnlineId\":\"\","
            + "\"PlatformUserId\":\"\",\"PlayFabId\":\"\",\"PremiumSkin\":false,"
            + "\"SelfSignedId\":\""
            + uuid
            + "\",\"ServerAddress\":\"127.0.0.1:"
            + port
            + "\",\"SkinAnimationData\":\"\",\"SkinColor\":\"#0\","
            + "\"SkinData\":\""
            + skinData
            + "\",\"SkinGeometryData\":\""
            + emptyObject
            + "\",\"SkinGeometryDataEngineVersion\":\"1.21.40\","
            + "\"SkinId\":\"BedrockBridgeSynthetic\",\"SkinImageHeight\":64,"
            + "\"SkinImageWidth\":64,\"SkinResourcePatch\":\""
            + resourcePatch
            + "\",\"ThirdPartyName\":\"BedrockBridgeProbe\","
            + "\"ThirdPartyNameOnly\":false,\"TrustedSkin\":true,\"UIProfile\":0}";
    String clientToken = signJwt(identity, publicKey, clientPayload);

    byte[] chain = chainJson.getBytes(StandardCharsets.UTF_8);
    byte[] client = clientToken.getBytes(StandardCharsets.UTF_8);
    return ByteBuffer.allocate(8 + chain.length + client.length)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putInt(chain.length)
        .put(chain)
        .putInt(client.length)
        .put(client)
        .array();
  }

  private static String signJwt(KeyPair keyPair, String publicKey, String payload)
      throws Exception {
    Base64.Encoder url = Base64.getUrlEncoder().withoutPadding();
    String header = "{\"alg\":\"ES384\",\"x5u\":\"" + publicKey + "\"}";
    String signingInput =
        url.encodeToString(header.getBytes(StandardCharsets.UTF_8))
            + "."
            + url.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    Signature signer = Signature.getInstance("SHA384withECDSAinP1363Format");
    signer.initSign(keyPair.getPrivate());
    signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
    return signingInput + "." + url.encodeToString(signer.sign());
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
