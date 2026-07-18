package io.bedrockbridge.application;

import io.bedrockbridge.bedrock.session.BedrockSessionBootstrap;
import io.bedrockbridge.bedrock.session.ConnectedFrameHandler;
import io.bedrockbridge.common.TaskScheduler;
import io.bedrockbridge.config.BridgeConfiguration;
import io.bedrockbridge.network.buffer.DirectPacketBufferPool;
import io.bedrockbridge.network.raknet.MtuPolicy;
import io.bedrockbridge.network.udp.NioUdpTransport;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

/** Owns the real UDP listener and bounded RakNet admission path for Bedrock clients. */
public final class BedrockServerRuntime implements AutoCloseable {
  private final BridgeConfiguration configuration;
  private final TaskScheduler scheduler;
  private final Clock clock;
  private final BiFunction<Integer, InetSocketAddress, ConnectedFrameHandler>
      connectedHandlerFactory;
  private final AtomicBoolean started = new AtomicBoolean();
  private final Map<Integer, NioUdpTransport> transports = new LinkedHashMap<>();
  private final Map<Integer, DirectPacketBufferPool> buffers = new LinkedHashMap<>();
  private final Map<Integer, BedrockSessionBootstrap> sessions = new LinkedHashMap<>();

  public BedrockServerRuntime(
      BridgeConfiguration configuration, TaskScheduler scheduler, Clock clock) {
    this(configuration, scheduler, clock, null);
  }

  /** Creates the UDP runtime with an optional per-listener connected DATA handler factory. */
  public BedrockServerRuntime(
      BridgeConfiguration configuration,
      TaskScheduler scheduler,
      Clock clock,
      BiFunction<Integer, InetSocketAddress, ConnectedFrameHandler> connectedHandlerFactory) {
    this.configuration = java.util.Objects.requireNonNull(configuration, "configuration");
    this.scheduler = java.util.Objects.requireNonNull(scheduler, "scheduler");
    this.clock = java.util.Objects.requireNonNull(clock, "clock");
    this.connectedHandlerFactory = connectedHandlerFactory;
  }

  public synchronized void start() {
    if (!started.compareAndSet(false, true)) {
      throw new IllegalStateException("Bedrock server runtime already started");
    }
    try {
      for (int listenerPort : configuration.listenerUpstreamNames().keySet()) {
        DirectPacketBufferPool listenerBuffers =
            new DirectPacketBufferPool(65_507, configuration.maximumSessions());
        NioUdpTransport listenerTransport =
            new NioUdpTransport(
                new InetSocketAddress(configuration.bindAddress(), listenerPort),
                65_507,
                configuration.maximumSessions() * 8,
                listenerBuffers,
                clock);
        BedrockSessionBootstrap listenerSessions =
            new BedrockSessionBootstrap(
                listenerTransport,
                scheduler,
                clock,
                0x4244524F434B3734L + listenerPort,
                configuration.maximumSessions(),
                Duration.ofSeconds(30),
                Duration.ofSeconds(1),
                new MtuPolicy(576, 1_492, 1_492),
                connectedHandlerFactory == null
                    ? null
                    : address -> connectedHandlerFactory.apply(listenerPort, address));
        buffers.put(listenerPort, listenerBuffers);
        transports.put(listenerPort, listenerTransport);
        sessions.put(listenerPort, listenerSessions);
      }
    } catch (RuntimeException failure) {
      close();
      throw failure;
    }
  }

  public synchronized InetSocketAddress localAddress() {
    NioUdpTransport transport = transports.get(configuration.bindPort());
    if (transport == null) {
      throw new IllegalStateException("Bedrock server runtime is not started");
    }
    return transport.localAddress();
  }

  @Override
  public synchronized void close() {
    if (!sessions.isEmpty()) {
      sessions.values().forEach(BedrockSessionBootstrap::close);
      sessions.clear();
      transports.clear();
      buffers.clear();
    }
  }
}
