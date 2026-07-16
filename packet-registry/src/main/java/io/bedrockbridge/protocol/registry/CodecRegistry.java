package io.bedrockbridge.protocol.registry;

import io.bedrockbridge.protocol.Packet;
import io.bedrockbridge.protocol.codec.PacketCodec;
import java.util.Optional;

/** Read-only codec projection over a packet registry. */
public final class CodecRegistry {
    private final PacketRegistry packets;

    /** Creates a codec view over the authoritative registry. */
    public CodecRegistry(PacketRegistry packets) {
        this.packets = java.util.Objects.requireNonNull(packets, "packets");
    }

    /** Finds the codec for a packet type. */
    public Optional<PacketCodec<?>> find(Class<? extends Packet> type) {
        return packets.find(type).map(PacketRegistration::codec);
    }
}
