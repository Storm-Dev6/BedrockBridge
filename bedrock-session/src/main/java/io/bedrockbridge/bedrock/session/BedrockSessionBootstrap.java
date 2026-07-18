package io.bedrockbridge.bedrock.session;

import io.bedrockbridge.bedrock.codec.BedrockDatagramCodec;
import io.bedrockbridge.bedrock.codec.BedrockPacketRegistry;
import io.bedrockbridge.bedrock.codec.BedrockPacketValidator;
import io.bedrockbridge.bedrock.login.BedrockLoginState;
import io.bedrockbridge.bedrock.login.BedrockLoginStateMachine;
import io.bedrockbridge.bedrock.login.ProtocolVersionNegotiator;
import io.bedrockbridge.common.TaskScheduler;
import io.bedrockbridge.network.core.Datagram;
import io.bedrockbridge.network.core.UdpTransport;
import io.bedrockbridge.network.raknet.MtuPolicy;
import io.bedrockbridge.network.session.TickScheduler;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Routes offline and connected handshake payloads into bounded Bedrock sessions. */
public final class BedrockSessionBootstrap implements AutoCloseable {
  private final UdpTransport transport;
  private final BedrockDatagramCodec codec;
  private final Clock clock;
  private final long serverGuid;
  private final int maximumSessions;
  private final Duration timeout;
  private final ConnectedFrameHandlerFactory connectedHandlerFactory;
  private final ConcurrentHashMap<InetSocketAddress, BedrockSession> sessions =
      new ConcurrentHashMap<>();
  private final TickScheduler ticks;
  private final AtomicBoolean closed = new AtomicBoolean();

  /** Starts UDP handshake admission and timeout maintenance. */
  public BedrockSessionBootstrap(
      UdpTransport transport,
      TaskScheduler scheduler,
      Clock clock,
      long serverGuid,
      int maximumSessions,
      Duration timeout,
      Duration tickInterval,
      MtuPolicy mtuPolicy) {
    this(
        transport,
        scheduler,
        clock,
        serverGuid,
        maximumSessions,
        timeout,
        tickInterval,
        mtuPolicy,
        (ConnectedFrameHandlerFactory) null);
  }

  /** Starts UDP admission with an optional handler for reassembled connected DATA payloads. */
  public BedrockSessionBootstrap(
      UdpTransport transport,
      TaskScheduler scheduler,
      Clock clock,
      long serverGuid,
      int maximumSessions,
      Duration timeout,
      Duration tickInterval,
      MtuPolicy mtuPolicy,
      ConnectedFrameHandler connectedHandler) {
    this(
        transport,
        scheduler,
        clock,
        serverGuid,
        maximumSessions,
        timeout,
        tickInterval,
        mtuPolicy,
        connectedHandler == null ? null : ignored -> connectedHandler);
  }

  /** Starts UDP admission with one handler factory per remote endpoint. */
  public BedrockSessionBootstrap(
      UdpTransport transport,
      TaskScheduler scheduler,
      Clock clock,
      long serverGuid,
      int maximumSessions,
      Duration timeout,
      Duration tickInterval,
      MtuPolicy mtuPolicy,
      ConnectedFrameHandlerFactory connectedHandlerFactory) {
    this.transport = Objects.requireNonNull(transport, "transport");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.serverGuid = serverGuid;
    if (serverGuid == 0 || maximumSessions < 1) {
      throw new IllegalArgumentException("serverGuid and maximumSessions must be positive");
    }
    this.maximumSessions = maximumSessions;
    this.timeout = Objects.requireNonNull(timeout, "timeout");
    this.connectedHandlerFactory = connectedHandlerFactory;
    codec =
        new BedrockDatagramCodec(
            BedrockPacketRegistry.create(), new BedrockPacketValidator(mtuPolicy));
    transport.start(this::receiveOffline);
    ticks = new TickScheduler(scheduler, tickInterval, this::tick);
  }

  private void receiveOffline(Datagram datagram) {
    if (closed.get()) {
      return;
    }
    BedrockSession session = sessions.get(datagram.remoteAddress());
    if (session == null) {
      if (sessions.size() >= maximumSessions) {
        return;
      }
      session =
          sessions.computeIfAbsent(
              datagram.remoteAddress(), address -> createSession(address, datagram.receivedAt()));
    }
    try {
      session.receive(datagram.payload(), datagram.receivedAt());
    } catch (IllegalArgumentException failure) {
      session.disconnect();
    }
    removeDisconnected(datagram.remoteAddress(), session);
  }

  /** Accepts a reassembled connected RakNet frame payload for an admitted endpoint. */
  public void receiveConnected(
      InetSocketAddress remoteAddress, ByteBuffer payload, Instant receivedAt) {
    BedrockSession session = sessions.get(remoteAddress);
    if (session == null) {
      return;
    }
    try {
      session.receive(payload, receivedAt);
    } catch (IllegalArgumentException failure) {
      session.disconnect();
    }
    removeDisconnected(remoteAddress, session);
  }

  /** Finds an active session by endpoint. */
  public Optional<BedrockSession> find(InetSocketAddress remoteAddress) {
    return Optional.ofNullable(sessions.get(remoteAddress));
  }

  /** Returns an immutable weakly consistent session snapshot. */
  public Collection<BedrockSession> sessions() {
    return List.copyOf(sessions.values());
  }

  private BedrockSession createSession(InetSocketAddress address, Instant now) {
    return new BedrockSession(
        address,
        codec,
        new BedrockLoginStateMachine(serverGuid, address, new ProtocolVersionNegotiator()),
        timeout,
        payload -> transport.send(address, payload),
        transport,
        connectedHandlerFactory == null ? null : connectedHandlerFactory.create(address),
        now);
  }

  private void tick() {
    Instant now = Instant.now(clock);
    for (var entry : sessions.entrySet()) {
      entry.getValue().tick(now);
      removeDisconnected(entry.getKey(), entry.getValue());
    }
  }

  private void removeDisconnected(InetSocketAddress address, BedrockSession session) {
    if (session.state() == BedrockLoginState.DISCONNECTED) {
      sessions.remove(address, session);
    }
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    ticks.close();
    sessions.values().forEach(BedrockSession::disconnect);
    sessions.clear();
    transport.close();
  }
}
