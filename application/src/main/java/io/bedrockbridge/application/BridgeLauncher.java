package io.bedrockbridge.application;

import io.bedrockbridge.application.translation.BedrockConnectedSessionFactory;
import io.bedrockbridge.application.translation.JavaUpstreamConnection;
import io.bedrockbridge.application.translation.RegistryBackedStartGameFrameProvider;
import io.bedrockbridge.bedrock.BedrockProtocolLimits;
import io.bedrockbridge.bedrock.auth.BedrockChainVerifier;
import io.bedrockbridge.bedrock.auth.InMemoryReplayGuard;
import io.bedrockbridge.bedrock.crypto.BedrockKeyAgreement;
import io.bedrockbridge.bedrock.login.BedrockAuthMode;
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
import io.bedrockbridge.registry.generator.ExternalItemRegistry;
import io.bedrockbridge.registry.generator.ExternalPublicKeyLoader;
import io.bedrockbridge.registry.generator.RegistryCheckCli;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
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
    ExternalItemRegistry registry = requireExternalRegistry(configuration);
    BedrockConnectedSessionFactory sessionFactory =
        productionSessionFactory(configuration, registry);
    BedrockBridge bridge =
        create(
            configuration,
            (listenerPort, ignoredAddress) -> sessionFactory.createForListener(listenerPort));
    Runtime.getRuntime().addShutdownHook(new Thread(bridge::close, "bridge-shutdown"));
    bridge.start();
    try {
      bridge.awaitTermination();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      bridge.close();
    }
  }

  private static ExternalItemRegistry requireExternalRegistry(BridgeConfiguration configuration) {
    if (configuration.registryPath().isBlank()
        || configuration.registryProtocolVersion().isBlank()
        || configuration.registrySha256().isBlank()) {
      throw new IllegalStateException(
          "BLOCKED_EXTERNAL_OFFICIAL_ARTIFACT: configure bridge.registry-path, "
              + "bridge.registry-protocol-version, and bridge.registry-sha256 before startup");
    }
    try {
      return RegistryCheckCli.validate(
          Path.of(configuration.registryPath()),
          configuration.registryProtocolVersion(),
          configuration.registrySha256());
    } catch (java.io.IOException | RuntimeException failure) {
      throw new IllegalStateException(
          "BLOCKED_EXTERNAL_OFFICIAL_ARTIFACT: external registry validation failed", failure);
    }
  }

  private static BedrockConnectedSessionFactory productionSessionFactory(
      BridgeConfiguration configuration, ExternalItemRegistry registry) {
    BedrockAuthMode authMode =
        configuration.offlineAuthMode().equals("allow-self-signed")
            ? BedrockAuthMode.OFFLINE_ALLOW_SELF_SIGNED
            : BedrockAuthMode.ONLINE;
    PublicKey trustRoot;
    try {
      trustRoot =
          authMode == BedrockAuthMode.ONLINE
              ? ExternalPublicKeyLoader.load(requiredTrustedRoot(configuration))
              : BedrockKeyAgreement.generate(new SecureRandom()).getPublic();
    } catch (java.io.IOException failure) {
      throw new IllegalStateException(
          "Bedrock authentication trust root could not be loaded", failure);
    }
    BedrockChainVerifier verifier =
        new BedrockChainVerifier(
            List.of(trustRoot),
            new InMemoryReplayGuard(Math.max(8, configuration.maximumSessions() * 2)),
            Clock.systemUTC(),
            Duration.ofSeconds(30));
    return new BedrockConnectedSessionFactory(
        BedrockProtocolLimits.defaults(),
        verifier,
        authMode,
        (listenerPort, username) -> {
          var upstreamName = configuration.listenerUpstreamNames().get(listenerPort);
          var upstream = configuration.namedUpstreams().get(upstreamName);
          if (upstream == null) {
            throw new java.io.IOException("No Java upstream mapping for listener " + listenerPort);
          }
          return JavaUpstreamConnection.loginOffline(
              upstream.address(),
              upstream.port(),
              username,
              upstream.connectTimeoutMillis(),
              upstream.readTimeoutMillis(),
              new io.bedrockbridge.application.translation.JavaBedrockTranslator());
        },
        new RegistryBackedStartGameFrameProvider(
            registry, BedrockProtocolLimits.defaults(), configuration.applicationName()));
  }

  private static Path requiredTrustedRoot(BridgeConfiguration configuration) {
    if (configuration.authTrustedRootPath().isBlank()) {
      throw new IllegalStateException(
          "Online Bedrock authentication requires bridge.auth-trusted-root");
    }
    return Path.of(configuration.authTrustedRootPath());
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
