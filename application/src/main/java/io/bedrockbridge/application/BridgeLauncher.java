package io.bedrockbridge.application;

import io.bedrockbridge.bedrock.session.ConnectedFrameHandler;
import io.bedrockbridge.common.DefaultEventBus;
import io.bedrockbridge.common.EventBus;
import io.bedrockbridge.common.ExecutorTaskScheduler;
import io.bedrockbridge.common.ServiceContainer;
import io.bedrockbridge.common.TaskScheduler;
import io.bedrockbridge.config.BridgeConfiguration;
import io.bedrockbridge.config.DefaultConfigurationValidator;
import io.bedrockbridge.config.PropertiesConfigurationLoader;
import io.bedrockbridge.observability.BridgeMetrics;
import io.bedrockbridge.registry.generator.RegistryCheckCli;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Clock;
import java.util.function.BiFunction;

/** Standalone composition root and command-line entry point for BedrockBridge. */
public final class BridgeLauncher {
  private BridgeLauncher() {}

  /** Loads a properties file, wires infrastructure, and keeps it alive until JVM shutdown. */
  public static void main(String[] arguments) {
    if (arguments.length != 1) {
      throw new IllegalArgumentException("Usage: BedrockBridge <configuration.properties>");
    }
    BridgeConfiguration configuration =
        new DefaultConfigurationValidator()
            .validate(new PropertiesConfigurationLoader().load(Path.of(arguments[0])));
    requireExternalRegistry(configuration);
    BedrockBridge bridge = create(configuration);
    Runtime.getRuntime().addShutdownHook(new Thread(bridge::close, "bridge-shutdown"));
    bridge.start();
    try {
      bridge.awaitTermination();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      bridge.close();
    }
  }

  private static void requireExternalRegistry(BridgeConfiguration configuration) {
    if (configuration.registryPath().isBlank()
        || configuration.registryProtocolVersion().isBlank()
        || configuration.registrySha256().isBlank()) {
      throw new IllegalStateException(
          "BLOCKED_EXTERNAL_OFFICIAL_ARTIFACT: configure bridge.registry-path, "
              + "bridge.registry-protocol-version, and bridge.registry-sha256 before startup");
    }
    try {
      RegistryCheckCli.validate(
          Path.of(configuration.registryPath()),
          configuration.registryProtocolVersion(),
          configuration.registrySha256());
    } catch (java.io.IOException | RuntimeException failure) {
      throw new IllegalStateException(
          "BLOCKED_EXTERNAL_OFFICIAL_ARTIFACT: external registry validation failed", failure);
    }
  }

  /** Creates a production composition with no hidden service locator outside this root. */
  public static BedrockBridge create(BridgeConfiguration configuration) {
    return create(configuration, null);
  }

  /**
   * Creates a production composition with an explicitly supplied connected-DATA handler factory.
   * The factory is intentionally not synthesized here: callers must provide an explicit Bedrock
   * authentication trust policy and a StartGame registry provider before accepting real clients.
   */
  public static BedrockBridge create(
      BridgeConfiguration configuration,
      BiFunction<Integer, InetSocketAddress, ConnectedFrameHandler> connectedHandlerFactory) {
    ServiceContainer services = new ServiceContainer();
    EventBus eventBus = new DefaultEventBus();
    TaskScheduler scheduler =
        new ExecutorTaskScheduler(configuration.schedulerThreads(), "bridge-scheduler");
    MeterRegistry meterRegistry = new SimpleMeterRegistry();
    services
        .register(EventBus.class, eventBus)
        .register(TaskScheduler.class, scheduler)
        .register(MeterRegistry.class, meterRegistry)
        .register(BridgeMetrics.class, new BridgeMetrics(meterRegistry));
    BedrockServerRuntime runtime =
        new BedrockServerRuntime(
            configuration,
            services.require(TaskScheduler.class),
            Clock.systemUTC(),
            connectedHandlerFactory);
    return new BedrockBridge(
        configuration,
        services.require(EventBus.class),
        services.require(TaskScheduler.class),
        services.require(BridgeMetrics.class),
        Clock.systemUTC(),
        runtime);
  }
}
