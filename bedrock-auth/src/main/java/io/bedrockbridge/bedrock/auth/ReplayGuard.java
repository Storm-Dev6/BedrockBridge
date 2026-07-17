package io.bedrockbridge.bedrock.auth;

import java.time.Instant;

/** Atomic bounded replay admission for authenticated login fingerprints. */
@FunctionalInterface
public interface ReplayGuard {
  /** Returns true exactly once for a fingerprint within its expiry window. */
  boolean admit(String fingerprint, Instant expiresAt, Instant now);
}
