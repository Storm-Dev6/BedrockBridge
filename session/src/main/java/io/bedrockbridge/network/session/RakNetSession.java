package io.bedrockbridge.network.session;

import io.bedrockbridge.network.core.UdpTransport;
import io.bedrockbridge.network.core.UnsignedTriad;
import io.bedrockbridge.network.raknet.AckCodec;
import io.bedrockbridge.network.raknet.AckRange;
import io.bedrockbridge.network.raknet.OrderingChannels;
import io.bedrockbridge.network.raknet.PacketQueue;
import io.bedrockbridge.network.raknet.RakNetFrame;
import io.bedrockbridge.network.raknet.RakNetFrameCodec;
import io.bedrockbridge.network.raknet.ReceiveWindow;
import io.bedrockbridge.network.raknet.RecoveryQueue;
import io.bedrockbridge.network.raknet.SplitPacketAssembler;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Session-confined RakNet reliability, ordering, keepalive, and timeout engine. */
public final class RakNetSession {
  private static final byte DATA = (byte) 0x80;
  private static final byte ACK = (byte) 0xC0;
  private static final byte NACK = (byte) 0xA0;
  private static final byte KEEP_ALIVE = 0x00;
  private static final byte DISCONNECT = 0x01;
  private final SessionId id;
  private final InetSocketAddress remoteAddress;
  private final UdpTransport transport;
  private final Duration timeout;
  private final Duration keepAliveInterval;
  private final RecoveryQueue recovery;
  private final ReceiveWindow datagramWindow;
  private final SplitPacketAssembler fragments;
  private final OrderingChannels ordering;
  private final PacketQueue outbound;
  private final RakNetFrameCodec frameCodec = new RakNetFrameCodec();
  private final AckCodec ackCodec = new AckCodec(512);
  private final Consumer<RakNetFrame> inboundHandler;
  private final AtomicReference<SessionState> state =
      new AtomicReference<>(SessionState.CONNECTING);
  private final List<Integer> pendingAcknowledgements = new ArrayList<>();
  private final List<Integer> pendingNegativeAcknowledgements = new ArrayList<>();
  private Instant lastReceived;
  private Instant lastSent;
  private int nextDatagramSequence;
  private DisconnectReason disconnectReason;

  /** Creates a bounded session owned by one manager tick thread. */
  public RakNetSession(
      SessionId id,
      InetSocketAddress remoteAddress,
      UdpTransport transport,
      Duration timeout,
      Duration keepAliveInterval,
      RecoveryQueue recovery,
      ReceiveWindow datagramWindow,
      SplitPacketAssembler fragments,
      OrderingChannels ordering,
      PacketQueue outbound,
      Consumer<RakNetFrame> inboundHandler,
      Instant now) {
    this.id = Objects.requireNonNull(id, "id");
    this.remoteAddress = Objects.requireNonNull(remoteAddress, "remoteAddress");
    this.transport = Objects.requireNonNull(transport, "transport");
    this.timeout = positive(timeout, "timeout");
    this.keepAliveInterval = positive(keepAliveInterval, "keepAliveInterval");
    this.recovery = Objects.requireNonNull(recovery, "recovery");
    this.datagramWindow = Objects.requireNonNull(datagramWindow, "datagramWindow");
    this.fragments = Objects.requireNonNull(fragments, "fragments");
    this.ordering = Objects.requireNonNull(ordering, "ordering");
    this.outbound = Objects.requireNonNull(outbound, "outbound");
    this.inboundHandler = Objects.requireNonNull(inboundHandler, "inboundHandler");
    lastReceived = Objects.requireNonNull(now, "now");
    lastSent = now;
    state.set(SessionState.CONNECTED);
  }

  /** Accepts one data datagram payload after its outer type byte. */
  public synchronized void receiveData(ByteBuffer input, Instant now) {
    if (state.get() != SessionState.CONNECTED) {
      return;
    }
    try {
      int sequence = RakNetFrameCodec.getTriad(input);
      pendingNegativeAcknowledgements.addAll(datagramWindow.missingBefore(sequence));
      ReceiveWindow.Result result = datagramWindow.accept(sequence);
      if (result != ReceiveWindow.Result.ACCEPTED) {
        return;
      }
      pendingAcknowledgements.add(sequence);
      lastReceived = now;
      while (input.hasRemaining()) {
        RakNetFrame fragment = frameCodec.decode(input);
        fragments
            .add(fragment, now)
            .ifPresent(frame -> ordering.admit(frame).forEach(inboundHandler));
      }
    } catch (IllegalArgumentException | IllegalStateException failure) {
      disconnect(DisconnectReason.PROTOCOL_ERROR);
    }
  }

  /** Applies ACK ranges to the reliable recovery queue. */
  public synchronized void acknowledge(List<AckRange> ranges, Instant now) {
    for (AckRange range : ranges) {
      for (int sequence : List.copyOf(recoverySequences(range))) {
        recovery.acknowledge(sequence, now);
      }
    }
    lastReceived = now;
  }

  /** Applies NACK ranges and retransmits known datagrams immediately. */
  public synchronized void negativeAcknowledge(List<AckRange> ranges, Instant now) {
    for (AckRange range : ranges) {
      for (int sequence : recoverySequences(range)) {
        recovery
            .nack(sequence, now)
            .ifPresent(item -> transport.send(remoteAddress, item.payload()));
      }
    }
    lastReceived = now;
  }

  /** Enqueues one outbound frame according to priority and bounded backpressure. */
  public synchronized boolean send(PacketQueue.Priority priority, RakNetFrame frame) {
    return state.get() == SessionState.CONNECTED && outbound.offer(priority, frame);
  }

  /** Performs retransmission, keepalive, timeout, fragment expiry, and queue flushing. */
  public synchronized void tick(Instant now) {
    if (state.get() != SessionState.CONNECTED) {
      return;
    }
    if (Duration.between(lastReceived, now).compareTo(timeout) >= 0) {
      disconnect(DisconnectReason.TIMEOUT);
      return;
    }
    fragments.expire(now);
    recovery.due(now).forEach(item -> transport.send(remoteAddress, item.payload()));
    if (recovery.consumeRetryExhausted()) {
      disconnect(DisconnectReason.RETRY_EXHAUSTED);
      return;
    }
    if (Duration.between(lastSent, now).compareTo(keepAliveInterval) >= 0) {
      enqueueControl(KEEP_ALIVE);
    }
    flush(now);
    flushAcknowledgements();
    pendingAcknowledgements.clear();
    pendingNegativeAcknowledgements.clear();
  }

  /** Closes this session once and attempts a final disconnect notification. */
  public synchronized void disconnect(DisconnectReason reason) {
    Objects.requireNonNull(reason, "reason");
    if (!state.compareAndSet(SessionState.CONNECTED, SessionState.DISCONNECTING)) {
      return;
    }
    disconnectReason = reason;
    enqueueControl(DISCONNECT);
    flush(Instant.now());
    state.set(SessionState.DISCONNECTED);
  }

  /** Returns the immutable identifier. */
  public SessionId id() {
    return id;
  }

  /** Returns the peer endpoint. */
  public InetSocketAddress remoteAddress() {
    return remoteAddress;
  }

  /** Returns the current lifecycle state. */
  public SessionState state() {
    return state.get();
  }

  /** Returns the termination reason after disconnect. */
  public DisconnectReason disconnectReason() {
    return disconnectReason;
  }

  private void enqueueControl(byte type) {
    outbound.offer(
        PacketQueue.Priority.CONTROL,
        new RakNetFrame(
            io.bedrockbridge.network.raknet.Reliability.RELIABLE,
            0,
            0,
            0,
            0,
            null,
            ByteBuffer.wrap(new byte[] {type})));
  }

  private void flush(Instant now) {
    RakNetFrame frame;
    while ((frame = outbound.poll()) != null) {
      ByteBuffer encoded = ByteBuffer.allocate(1500);
      encoded.put(DATA);
      int sequence = nextDatagramSequence;
      nextDatagramSequence = UnsignedTriad.increment(nextDatagramSequence);
      RakNetFrameCodec.putTriad(encoded, sequence);
      frameCodec.encode(frame, encoded);
      encoded.flip();
      if (transport.send(remoteAddress, encoded)) {
        lastSent = now;
        if (frame.reliability().isReliable()) {
          recovery.track(sequence, encoded.rewind(), now);
        }
      }
    }
  }

  private void flushAcknowledgements() {
    flushControlRanges(ACK, pendingAcknowledgements);
    flushControlRanges(NACK, pendingNegativeAcknowledgements);
  }

  private void flushControlRanges(byte type, List<Integer> sequences) {
    List<AckRange> ranges =
        sequences.stream()
            .distinct()
            .sorted()
            .map(sequence -> new AckRange(sequence, sequence))
            .toList();
    for (int offset = 0; offset < ranges.size(); offset += 512) {
      List<AckRange> batch = ranges.subList(offset, Math.min(offset + 512, ranges.size()));
      ByteBuffer encoded = ByteBuffer.allocate(4 + batch.size() * 7);
      encoded.put(type);
      ackCodec.encode(batch, encoded);
      encoded.flip();
      transport.send(remoteAddress, encoded);
    }
  }

  private List<Integer> recoverySequences(AckRange range) {
    List<Integer> result = new ArrayList<>();
    int sequence = range.start();
    while (true) {
      result.add(sequence);
      if (sequence == range.end()) {
        return result;
      }
      sequence = UnsignedTriad.increment(sequence);
      if (result.size() > 4096) {
        throw new IllegalArgumentException("ACK range is too large");
      }
    }
  }

  private static Duration positive(Duration duration, String name) {
    Objects.requireNonNull(duration, name);
    if (duration.isZero() || duration.isNegative()) {
      throw new IllegalArgumentException(name + " must be positive");
    }
    return duration;
  }
}
