package io.bedrockbridge.config;

import io.bedrockbridge.common.Checks;
import java.util.Map;

/** Immutable, validated process configuration for infrastructure services. */
public record BridgeConfiguration(
    String applicationName,
    String bindAddress,
    int bindPort,
    String upstreamAddress,
    int upstreamPort,
    int maximumSessions,
    int schedulerThreads,
    boolean developmentMode,
    String registryPath,
    String registryProtocolVersion,
    String registrySha256,
    String offlineAuthMode,
    Map<String, JavaUpstreamDefinition> namedUpstreams,
    Map<Integer, String> listenerUpstreamNames) {
  /** Compatibility constructor for infrastructure-only tests and callers. */
  public BridgeConfiguration(
      String applicationName,
      String bindAddress,
      int bindPort,
      String upstreamAddress,
      int upstreamPort,
      int maximumSessions,
      int schedulerThreads,
      boolean developmentMode) {
    this(
        applicationName,
        bindAddress,
        bindPort,
        upstreamAddress,
        upstreamPort,
        maximumSessions,
        schedulerThreads,
        developmentMode,
        "",
        "",
        "",
        "deny",
        Map.of(
            "default",
            new JavaUpstreamDefinition("default", upstreamAddress, upstreamPort, 5_000, 15_000)),
        Map.of(bindPort, "default"));
  }

  /** Applies local structural invariants; cross-field policy is handled by the validator. */
  public BridgeConfiguration {
    applicationName = Checks.notBlank(applicationName, "applicationName");
    bindAddress = Checks.notBlank(bindAddress, "bindAddress");
    bindPort = Checks.inRange(bindPort, 1, 65_535, "bindPort");
    upstreamAddress = Checks.notBlank(upstreamAddress, "upstreamAddress");
    upstreamPort = Checks.inRange(upstreamPort, 1, 65_535, "upstreamPort");
    maximumSessions = Checks.inRange(maximumSessions, 1, 10_000, "maximumSessions");
    schedulerThreads = Checks.inRange(schedulerThreads, 1, 64, "schedulerThreads");
    registryPath = registryPath == null ? "" : registryPath.trim();
    registryProtocolVersion = registryProtocolVersion == null ? "" : registryProtocolVersion.trim();
    registrySha256 =
        registrySha256 == null ? "" : registrySha256.trim().toLowerCase(java.util.Locale.ROOT);
    offlineAuthMode =
        Checks.notBlank(offlineAuthMode, "offlineAuthMode").toLowerCase(java.util.Locale.ROOT);
    if (!offlineAuthMode.equals("deny") && !offlineAuthMode.equals("allow-self-signed")) {
      throw new IllegalArgumentException("offlineAuthMode must be deny or allow-self-signed");
    }
    namedUpstreams = Map.copyOf(namedUpstreams == null ? Map.of() : namedUpstreams);
    listenerUpstreamNames =
        Map.copyOf(listenerUpstreamNames == null ? Map.of() : listenerUpstreamNames);
    if (namedUpstreams.isEmpty()) {
      throw new IllegalArgumentException("at least one named upstream is required");
    }
    if (listenerUpstreamNames.isEmpty()) {
      throw new IllegalArgumentException("at least one listener mapping is required");
    }
    for (Map.Entry<String, JavaUpstreamDefinition> entry : namedUpstreams.entrySet()) {
      if (!entry.getKey().equals(entry.getValue().name())) {
        throw new IllegalArgumentException("upstream map key must match definition name");
      }
    }
    for (Map.Entry<Integer, String> entry : listenerUpstreamNames.entrySet()) {
      Checks.inRange(entry.getKey(), 1, 65_535, "listener port");
      if (!namedUpstreams.containsKey(entry.getValue())) {
        throw new IllegalArgumentException(
            "listener maps to unknown upstream: " + entry.getValue());
      }
    }
  }
}
