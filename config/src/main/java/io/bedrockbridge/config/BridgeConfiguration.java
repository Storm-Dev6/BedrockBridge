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
    boolean developmentMode) {
  /** Applies local structural invariants; cross-field policy is handled by the validator. */
  public BridgeConfiguration {
    applicationName = Checks.notBlank(applicationName, "applicationName");
    bindAddress = Checks.notBlank(bindAddress, "bindAddress");
    bindPort = Checks.inRange(bindPort, 1, 65_535, "bindPort");
    upstreamAddress = Checks.notBlank(upstreamAddress, "upstreamAddress");
    upstreamPort = Checks.inRange(upstreamPort, 1, 65_535, "upstreamPort");
    maximumSessions = Checks.inRange(maximumSessions, 1, 10_000, "maximumSessions");
    schedulerThreads = Checks.inRange(schedulerThreads, 1, 64, "schedulerThreads");
  }
}
