package io.bedrockbridge.network.session;

import java.util.Objects;
import java.util.UUID;

/** Opaque process-local identity for one RakNet transport session. */
public record SessionId(UUID value) {
    /** Validates the identifier. */
    public SessionId {
        Objects.requireNonNull(value, "value");
    }

    /** Creates a cryptographically unpredictable session identifier. */
    public static SessionId create() {
        return new SessionId(UUID.randomUUID());
    }
}
