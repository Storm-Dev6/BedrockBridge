package io.bedrockbridge.bedrock.session;

import io.bedrockbridge.bedrock.codec.BedrockDatagramCodec;
import io.bedrockbridge.bedrock.login.BedrockLoginState;
import io.bedrockbridge.bedrock.login.BedrockLoginStateMachine;
import io.bedrockbridge.network.core.UdpTransport;
import io.bedrockbridge.network.raknet.OrderingChannels;
import io.bedrockbridge.network.raknet.PacketQueue;
import io.bedrockbridge.network.raknet.RakNetFrame;
import io.bedrockbridge.network.raknet.ReceiveWindow;
import io.bedrockbridge.network.raknet.RecoveryQueue;
import io.bedrockbridge.network.raknet.Reliability;
import io.bedrockbridge.network.raknet.RttEstimator;
import io.bedrockbridge.network.raknet.SplitPacketAssembler;
import io.bedrockbridge.network.session.RakNetSession;
import io.bedrockbridge.network.session.SessionId;
import io.bedrockbridge.protocol.Packet;
import io.bedrockbridge.protocol.PacketDirection;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;

/** Owns one Bedrock transport handshake, timeout, keepalive response, and outbound encoding. */
public final class BedrockSession {
  private static final int MAX_CONNECTED_FRAME_PAYLOAD = 1_440;
  private final InetSocketAddress remoteAddress;
  private final BedrockDatagramCodec codec;
  private final BedrockLoginStateMachine login;
  private final Duration timeout;
  private final Consumer<ByteBuffer> sender;
  private final UdpTransport transport;
  private final ConnectedFrameHandler connectedHandler;
  private RakNetSession connected;
  private int nextReliableIndex;
  private int nextOrderIndex;
  private Instant lastActivity;

  /** Creates a session with bounded inactivity timeout and an ownership-transferring sender. */
  public BedrockSession(
      InetSocketAddress remoteAddress,
      BedrockDatagramCodec codec,
      BedrockLoginStateMachine login,
      Duration timeout,
      Consumer<ByteBuffer> sender,
      Instant now) {
    this(remoteAddress, codec, login, timeout, sender, null, null, now);
  }

  /** Creates a session with connected RakNet DATA dispatch and outbound frame support. */
  public BedrockSession(
      InetSocketAddress remoteAddress,
      BedrockDatagramCodec codec,
      BedrockLoginStateMachine login,
      Duration timeout,
      Consumer<ByteBuffer> sender,
      UdpTransport transport,
      ConnectedFrameHandler connectedHandler,
      Instant now) {
    this.remoteAddress = Objects.requireNonNull(remoteAddress, "remoteAddress");
    this.codec = Objects.requireNonNull(codec, "codec");
    this.login = Objects.requireNonNull(login, "login");
    this.timeout = Objects.requireNonNull(timeout, "timeout");
    this.sender = Objects.requireNonNull(sender, "sender");
    if ((transport == null) != (connectedHandler == null)) {
      throw new IllegalArgumentException(
          "transport and connectedHandler must be supplied together");
    }
    this.transport = transport;
    this.connectedHandler = connectedHandler;
    lastActivity = Objects.requireNonNull(now, "now");
    if (timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("timeout must be positive");
    }
  }

  /** Decodes, validates, applies, and replies to one serverbound handshake packet. */
  public synchronized void receive(ByteBuffer payload, Instant now) {
    if (state() == BedrockLoginState.DISCONNECTED) {
      return;
    }
    ByteBuffer input = Objects.requireNonNull(payload, "payload").slice();
    if (login.state() == BedrockLoginState.CONNECTED && connected != null) {
      try {
        connected.receiveDatagram(input, now);
        connected.tick(now);
      } catch (RuntimeException failure) {
        connected.disconnect(io.bedrockbridge.network.session.DisconnectReason.PROTOCOL_ERROR);
        login.disconnect();
      }
      lastActivity = now;
      return;
    }
    Packet packet = codec.decode(input, PacketDirection.SERVERBOUND);
    lastActivity = now;
    login.handle(packet, now).ifPresent(this::send);
    if (login.state() == BedrockLoginState.CONNECTED
        && connected == null
        && connectedHandler != null) {
      connected = createConnectedSession(now);
    }
  }

  /** Disconnects sessions whose peer has not produced transport activity in time. */
  public synchronized void tick(Instant now) {
    if (state() != BedrockLoginState.DISCONNECTED
        && Duration.between(lastActivity, now).compareTo(timeout) >= 0) {
      login.disconnect();
      if (connected != null) {
        connected.disconnect(io.bedrockbridge.network.session.DisconnectReason.TIMEOUT);
      }
      closeHandler();
      return;
    }
    if (connected != null) {
      connected.tick(now);
      if (connected.state() == io.bedrockbridge.network.session.SessionState.DISCONNECTED) {
        login.disconnect();
        closeHandler();
      }
    }
  }

  /** Immediately closes this handshake session. */
  public synchronized void disconnect() {
    login.disconnect();
    if (connected != null) {
      connected.disconnect(io.bedrockbridge.network.session.DisconnectReason.SERVER_SHUTDOWN);
    }
    closeHandler();
  }

  /** Returns the peer endpoint. */
  public InetSocketAddress remoteAddress() {
    return remoteAddress;
  }

  /** Returns the current login state. */
  public BedrockLoginState state() {
    return login.state();
  }

  private void send(Packet packet) {
    ByteBuffer output = ByteBuffer.allocate(2048);
    codec.encode(packet, output);
    output.flip();
    sender.accept(output.asReadOnlyBuffer());
  }

  private RakNetSession createConnectedSession(Instant now) {
    return new RakNetSession(
        SessionId.create(),
        remoteAddress,
        transport,
        timeout,
        Duration.ofSeconds(5),
        new RecoveryQueue(
            1_024, 5, new RttEstimator(Duration.ofMillis(100), Duration.ofSeconds(5))),
        new ReceiveWindow(1_024, 0),
        new SplitPacketAssembler(64, 4 * 1024 * 1024, Duration.ofSeconds(10)),
        new OrderingChannels(256),
        new PacketQueue(1_024),
        frame -> connectedHandler.handle(frame.payload(), this::sendConnected),
        now);
  }

  private void sendConnected(ByteBuffer payload) {
    ByteBuffer copy = Objects.requireNonNull(payload, "payload").slice();
    int total = copy.remaining();
    if (total == 0) {
      throw new IllegalArgumentException("Connected payload must not be empty");
    }
    int orderIndex = nextOrderIndex;
    int reliableIndex = nextReliableIndex;
    int splitId = orderIndex & 0xFFFF;
    int count = Math.ceilDiv(total, MAX_CONNECTED_FRAME_PAYLOAD);
    for (int index = 0; index < count; index++) {
      int length = Math.min(MAX_CONNECTED_FRAME_PAYLOAD, copy.remaining());
      ByteBuffer fragment = copy.slice(copy.position(), length);
      RakNetFrame.SplitInfo split =
          count == 1 ? null : new RakNetFrame.SplitInfo(count, splitId, index);
      RakNetFrame frame =
          new RakNetFrame(
              Reliability.RELIABLE_ORDERED, reliableIndex, 0, orderIndex, 0, split, fragment);
      if (!connected.send(PacketQueue.Priority.GAMEPLAY, frame)) {
        throw new IllegalStateException("Connected RakNet outbound queue is full");
      }
      copy.position(copy.position() + length);
    }
    nextReliableIndex = io.bedrockbridge.network.core.UnsignedTriad.increment(nextReliableIndex);
    nextOrderIndex = io.bedrockbridge.network.core.UnsignedTriad.increment(nextOrderIndex);
    connected.tick(lastActivity);
  }

  private void closeHandler() {
    if (connectedHandler != null) {
      connectedHandler.close();
    }
  }
}
