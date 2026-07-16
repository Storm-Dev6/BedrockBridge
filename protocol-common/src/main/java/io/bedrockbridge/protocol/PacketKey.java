package io.bedrockbridge.protocol;

import java.util.Objects;

/** Complete collision-free key for a packet registration. */
public record PacketKey(
        ProtocolVersion version, ProtocolState state, PacketDirection direction, int packetId) {
    /** Validates all key components. */
    public PacketKey {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(direction, "direction");
        if (packetId < 0) {
            throw new IllegalArgumentException("packetId must be nonnegative");
        }
    }
}
