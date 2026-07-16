package io.bedrockbridge.protocol.registry;

import io.bedrockbridge.protocol.Packet;
import io.bedrockbridge.protocol.PacketDirection;
import io.bedrockbridge.protocol.PacketKey;
import io.bedrockbridge.protocol.ProtocolState;
import io.bedrockbridge.protocol.ProtocolVersion;
import java.util.OptionalInt;

/** Packet-class to wire-ID lookup scoped by version, state, and direction. */
public final class PacketIdRegistry {
    private final PacketRegistry packets;

    /** Creates an ID view over the authoritative packet registry. */
    public PacketIdRegistry(PacketRegistry packets) {
        this.packets = java.util.Objects.requireNonNull(packets, "packets");
    }

    /** Finds an ID only if the packet type registration matches the requested scope. */
    public OptionalInt find(
            Class<? extends Packet> type,
            ProtocolVersion version,
            ProtocolState state,
            PacketDirection direction) {
        var registeredKey = packets.find(type)
                .map(PacketRegistration::key)
                .filter(key -> key.equals(new PacketKey(version, state, direction, key.packetId())))
                .orElse(null);
        return registeredKey == null
                ? OptionalInt.empty()
                : OptionalInt.of(registeredKey.packetId());
    }
}
