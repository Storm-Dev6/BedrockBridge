package io.bedrockbridge.network.udp;

import io.bedrockbridge.common.Checks;
import io.bedrockbridge.common.LifecycleException;
import io.bedrockbridge.network.buffer.PacketBufferPool;
import io.bedrockbridge.network.buffer.PooledBuffer;
import io.bedrockbridge.network.core.Datagram;
import io.bedrockbridge.network.core.DatagramHandler;
import io.bedrockbridge.network.core.UdpTransport;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/** Single-selector, non-blocking UDP transport with a bounded multi-producer send queue. */
public final class NioUdpTransport implements UdpTransport {
  private final DatagramChannel channel;
  private final Selector selector;
  private final PacketBufferPool bufferPool;
  private final int maximumDatagramSize;
  private final ArrayBlockingQueue<OutboundDatagram> outbound;
  private final Clock clock;
  private final AtomicBoolean started = new AtomicBoolean();
  private final AtomicBoolean running = new AtomicBoolean();
  private final CountDownLatch stopped = new CountDownLatch(1);

  /** Opens and binds a transport; no thread starts until {@link #start(DatagramHandler)}. */
  public NioUdpTransport(
      InetSocketAddress bindAddress,
      int maximumDatagramSize,
      int sendQueueCapacity,
      PacketBufferPool bufferPool,
      Clock clock) {
    Objects.requireNonNull(bindAddress, "bindAddress");
    this.maximumDatagramSize =
        Checks.inRange(maximumDatagramSize, 512, 65_507, "maximumDatagramSize");
    Checks.inRange(sendQueueCapacity, 1, 1_000_000, "sendQueueCapacity");
    this.bufferPool = Objects.requireNonNull(bufferPool, "bufferPool");
    this.clock = Objects.requireNonNull(clock, "clock");
    outbound = new ArrayBlockingQueue<>(sendQueueCapacity);
    try {
      selector = Selector.open();
      channel = DatagramChannel.open();
      channel.configureBlocking(false);
      channel.bind(bindAddress);
      channel.register(selector, SelectionKey.OP_READ);
    } catch (IOException failure) {
      throw new LifecycleException("Unable to bind UDP transport to " + bindAddress, failure);
    }
  }

  @Override
  public void start(DatagramHandler handler) {
    Objects.requireNonNull(handler, "handler");
    if (!started.compareAndSet(false, true)) {
      throw new LifecycleException("UDP transport has already started");
    }
    running.set(true);
    Thread.ofPlatform().name("bedrockbridge-udp").daemon(true).start(() -> runLoop(handler));
  }

  @Override
  public boolean send(InetSocketAddress remoteAddress, ByteBuffer payload) {
    Objects.requireNonNull(remoteAddress, "remoteAddress");
    Objects.requireNonNull(payload, "payload");
    int length = payload.remaining();
    if (length == 0 || length > maximumDatagramSize || !running.get()) {
      return false;
    }
    byte[] snapshot = new byte[length];
    payload.duplicate().get(snapshot);
    boolean accepted = outbound.offer(new OutboundDatagram(remoteAddress, snapshot));
    if (accepted) {
      selector.wakeup();
    }
    return accepted;
  }

  @Override
  public InetSocketAddress localAddress() {
    try {
      return (InetSocketAddress) channel.getLocalAddress();
    } catch (IOException failure) {
      throw new LifecycleException("Unable to read UDP local address", failure);
    }
  }

  private void runLoop(DatagramHandler handler) {
    try (PooledBuffer lease = bufferPool.acquire(maximumDatagramSize)) {
      ByteBuffer receiveBuffer = lease.buffer();
      while (running.get()) {
        selector.select(100);
        receive(handler, receiveBuffer);
        flushSends();
      }
      flushSends();
    } catch (IOException failure) {
      if (running.get()) {
        throw new LifecycleException("UDP selector loop failed", failure);
      }
    } finally {
      running.set(false);
      stopped.countDown();
    }
  }

  private void receive(DatagramHandler handler, ByteBuffer buffer) throws IOException {
    while (running.get()) {
      buffer.clear();
      InetSocketAddress remoteAddress = (InetSocketAddress) channel.receive(buffer);
      if (remoteAddress == null) {
        return;
      }
      buffer.flip();
      handler.handle(new Datagram(remoteAddress, buffer, Instant.now(clock)));
    }
  }

  private void flushSends() throws IOException {
    OutboundDatagram datagram;
    while ((datagram = outbound.poll()) != null) {
      channel.send(ByteBuffer.wrap(datagram.payload), datagram.remoteAddress);
    }
  }

  @Override
  public void close() {
    if (running.compareAndSet(true, false)) {
      selector.wakeup();
    }
    if (started.get()) {
      boolean interrupted = false;
      while (true) {
        try {
          stopped.await();
          break;
        } catch (InterruptedException ignored) {
          interrupted = true;
        }
      }
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
    try {
      channel.close();
      selector.close();
    } catch (IOException failure) {
      throw new LifecycleException("Unable to close UDP transport", failure);
    }
  }

  private static final class OutboundDatagram {
    private final InetSocketAddress remoteAddress;
    private final byte[] payload;

    private OutboundDatagram(InetSocketAddress remoteAddress, byte[] payload) {
      this.remoteAddress = remoteAddress;
      this.payload = payload;
    }
  }
}
