package io.bedrockbridge.application;

import io.bedrockbridge.bedrock.session.BedrockSessionBootstrap;
import io.bedrockbridge.common.TaskScheduler;
import io.bedrockbridge.config.BridgeConfiguration;
import io.bedrockbridge.network.buffer.DirectPacketBufferPool;
import io.bedrockbridge.network.raknet.MtuPolicy;
import io.bedrockbridge.network.udp.NioUdpTransport;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns the real UDP listener and bounded RakNet admission path for Bedrock clients. */
public final class BedrockServerRuntime implements AutoCloseable {
  private final BridgeConfiguration configuration;
  private final TaskScheduler scheduler;
  private final Clock clock;
  private final AtomicBoolean started = new AtomicBoolean();
  private NioUdpTransport transport;
  private DirectPacketBufferPool buffers;
  private BedrockSessionBootstrap sessions;

  public BedrockServerRuntime(
      BridgeConfiguration configuration, TaskScheduler scheduler, Clock clock) {
    this.configuration = java.util.Objects.requireNonNull(configuration, "configuration");
    this.scheduler = java.util.Objects.requireNonNull(scheduler, "scheduler");
    this.clock = java.util.Objects.requireNonNull(clock, "clock");
  }

  public synchronized void start() {
    if (!started.compareAndSet(false, true)) {
      throw new IllegalStateException("Bedrock server runtime already started");
    }
    try {
      buffers = new DirectPacketBufferPool(65_507, configuration.maximumSessions());
      transport =
          new NioUdpTransport(
              new InetSocketAddress(configuration.bindAddress(), configuration.bindPort()),
              65_507,
              configuration.maximumSessions() * 8,
              buffers,
              clock);
      sessions =
          new BedrockSessionBootstrap(
              transport,
              scheduler,
              clock,
              0x4244524F434B3734L,
              configuration.maximumSessions(),
              Duration.ofSeconds(30),
              Duration.ofSeconds(1),
              new MtuPolicy(576, 1_492, 1_492));
    } catch (RuntimeException failure) {
      close();
      throw failure;
    }
  }

  public synchronized InetSocketAddress localAddress() {
    if (transport == null) {
      throw new IllegalStateException("Bedrock server runtime is not started");
    }
    return transport.localAddress();
  }

  @Override
  public synchronized void close() {
    if (sessions != null) {
      sessions.close();
      sessions = null;
      transport = null;
      buffers = null;
    }
  }
}
