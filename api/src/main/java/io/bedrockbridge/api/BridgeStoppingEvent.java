package io.bedrockbridge.api;

import java.time.Instant;
import java.util.Objects;

/** Published immediately before foundational services begin shutting down. */
public record BridgeStoppingEvent(Instant occurredAt) {
    /** Creates a timestamped immutable lifecycle event. */
    public BridgeStoppingEvent {
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
