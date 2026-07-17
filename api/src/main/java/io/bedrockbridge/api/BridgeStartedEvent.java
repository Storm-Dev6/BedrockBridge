package io.bedrockbridge.api;

import java.time.Instant;
import java.util.Objects;

/** Published after all foundational application services have started. */
public record BridgeStartedEvent(Instant occurredAt) {
  /** Creates a timestamped immutable lifecycle event. */
  public BridgeStartedEvent {
    Objects.requireNonNull(occurredAt, "occurredAt");
  }
}
