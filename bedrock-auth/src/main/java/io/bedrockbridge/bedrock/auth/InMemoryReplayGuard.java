package io.bedrockbridge.bedrock.auth;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded in-memory replay cache with opportunistic expiry. */
public final class InMemoryReplayGuard implements ReplayGuard {
  private final int maximumEntries;
  private final ConcurrentHashMap<String, Instant> entries = new ConcurrentHashMap<>();

  /** Creates a cache with a strict positive entry limit. */
  public InMemoryReplayGuard(int maximumEntries) {
    if (maximumEntries < 1) {
      throw new IllegalArgumentException("maximumEntries must be positive");
    }
    this.maximumEntries = maximumEntries;
  }

  @Override
  public synchronized boolean admit(String fingerprint, Instant expiresAt, Instant now) {
    Objects.requireNonNull(fingerprint, "fingerprint");
    Objects.requireNonNull(expiresAt, "expiresAt");
    Objects.requireNonNull(now, "now");
    entries.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
    if (!expiresAt.isAfter(now) || entries.size() >= maximumEntries) {
      return false;
    }
    return entries.putIfAbsent(fingerprint, expiresAt) == null;
  }
}
