package io.bedrockbridge.config;

import io.bedrockbridge.common.Checks;

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
    String offlineAuthMode) {
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
        "deny");
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
  }
}
