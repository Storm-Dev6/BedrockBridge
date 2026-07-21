package io.bedrockbridge.network.session;

import io.bedrockbridge.common.TaskScheduler;
import io.bedrockbridge.network.core.Datagram;
import io.bedrockbridge.network.core.UdpTransport;
import io.bedrockbridge.network.raknet.AckCodec;
import io.bedrockbridge.network.raknet.RakNetDatagramFlags;
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

/** Lock-minimal endpoint-to-session router for up to a configured number of UDP peers. */
public final class SessionManager implements ConnectionManager {
  private static final int ACK = 0xC0;
  private static final int NACK = 0xA0;
  private final UdpTransport transport;
  private final SessionFactory sessionFactory;
  private final Clock clock;
  private final int maximumSessions;
  private final AckCodec ackCodec = new AckCodec(512);
  private final ConcurrentHashMap<InetSocketAddress, RakNetSession> sessions =
      new ConcurrentHashMap<>();
  private final TickScheduler tickScheduler;
  private final AtomicBoolean closed = new AtomicBoolean();

  /** Starts the transport and periodic maintenance immediately. */
  public SessionManager(
      UdpTransport transport,
      SessionFactory sessionFactory,
      Clock clock,
      int maximumSessions,
      TaskScheduler scheduler,
      Duration tickInterval) {
    this.transport = Objects.requireNonNull(transport, "transport");
    this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory");
    this.clock = Objects.requireNonNull(clock, "clock");
    if (maximumSessions < 1 || maximumSessions > 1_000_000) {
      throw new IllegalArgumentException("maximumSessions must be between 1 and 1000000");
    }
    this.maximumSessions = maximumSessions;
    transport.start(this::receive);
    tickScheduler = new TickScheduler(scheduler, tickInterval, this::tick);
  }

  private void receive(Datagram datagram) {
    ByteBuffer payload = datagram.payload();
    if (!payload.hasRemaining() || closed.get()) {
      return;
    }
    int type = Byte.toUnsignedInt(payload.get());
    RakNetSession session = sessions.get(datagram.remoteAddress());
    if (session == null && RakNetDatagramFlags.isData(type) && sessions.size() < maximumSessions) {
      session =
          sessions.computeIfAbsent(
              datagram.remoteAddress(),
              address -> sessionFactory.create(address, datagram.receivedAt()));
    }
    if (session == null) {
      return;
    }
    try {
      if (RakNetDatagramFlags.isData(type)) {
        session.receiveData(payload, datagram.receivedAt());
      } else {
        switch (type) {
          case ACK -> session.acknowledge(ackCodec.decode(payload), datagram.receivedAt());
          case NACK -> session.negativeAcknowledge(ackCodec.decode(payload), datagram.receivedAt());
          default -> session.disconnect(DisconnectReason.PROTOCOL_ERROR);
        }
      }
    } catch (IllegalArgumentException failure) {
      session.disconnect(DisconnectReason.PROTOCOL_ERROR);
    }
    if (session.state() == SessionState.DISCONNECTED) {
      sessions.remove(datagram.remoteAddress(), session);
    }
  }

  private void tick() {
    Instant now = Instant.now(clock);
    for (var entry : sessions.entrySet()) {
      RakNetSession session = entry.getValue();
      session.tick(now);
      if (session.state() == SessionState.DISCONNECTED) {
        sessions.remove(entry.getKey(), session);
      }
    }
  }

  @Override
  public Optional<RakNetSession> find(InetSocketAddress remoteAddress) {
    return Optional.ofNullable(
        sessions.get(Objects.requireNonNull(remoteAddress, "remoteAddress")));
  }

  @Override
  public Collection<RakNetSession> sessions() {
    return List.copyOf(sessions.values());
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    tickScheduler.close();
    sessions.values().forEach(session -> session.disconnect(DisconnectReason.SERVER_SHUTDOWN));
    sessions.clear();
    transport.close();
  }
}
