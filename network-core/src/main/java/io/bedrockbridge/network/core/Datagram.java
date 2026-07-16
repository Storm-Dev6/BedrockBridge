package io.bedrockbridge.network.core;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Objects;

/** Immutable metadata and read-only payload view for one received UDP datagram. */
public record Datagram(InetSocketAddress remoteAddress, ByteBuffer payload, Instant receivedAt) {
    /** Validates and freezes the payload view without copying its bytes. */
    public Datagram {
        Objects.requireNonNull(remoteAddress, "remoteAddress");
        payload = Objects.requireNonNull(payload, "payload").asReadOnlyBuffer();
        Objects.requireNonNull(receivedAt, "receivedAt");
    }
}
