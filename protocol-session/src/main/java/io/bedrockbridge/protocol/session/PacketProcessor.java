package io.bedrockbridge.protocol.session;

import io.bedrockbridge.protocol.Packet;
import io.bedrockbridge.protocol.PacketKey;
import io.bedrockbridge.protocol.ProtocolState;
import io.bedrockbridge.protocol.ProtocolVersion;
import io.bedrockbridge.protocol.PacketDirection;
import io.bedrockbridge.protocol.codec.ByteBufferPacketReader;
import io.bedrockbridge.protocol.codec.ByteBufferPacketWriter;
import io.bedrockbridge.protocol.registry.PacketRegistration;
import io.bedrockbridge.protocol.registry.PacketRegistry;
import java.nio.ByteBuffer;
import java.util.Objects;

/** Registry-driven packet factory, decoder, and encoder without switch statements. */
public final class PacketProcessor {
    private final PacketRegistry registry;

    /** Creates a processor over the authoritative packet registry. */
    public PacketProcessor(PacketRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /** Instantiates and decodes a packet for an exact wire key. */
    public Packet decode(
            ProtocolVersion version,
            ProtocolState state,
            PacketDirection direction,
            int packetId,
            ByteBuffer payload) {
        PacketRegistration<?> registration = registry
                .find(new PacketKey(version, state, direction, packetId))
                .orElseThrow(() -> new IllegalArgumentException("Unknown packet registration"));
        return decodeRegistered(registration, payload);
    }

    /** Encodes packet fields into the caller-owned output and returns written bytes. */
    public int encode(Packet packet, ByteBuffer output) {
        PacketRegistration<?> registration = registry.find(packet.getClass())
                .orElseThrow(() -> new IllegalArgumentException("Unknown packet type"));
        if (!registration.key().equals(new PacketKey(
                packet.protocolVersion(), packet.state(), packet.direction(), packet.packetId()))) {
            throw new IllegalArgumentException("Packet metadata differs from its registration");
        }
        return encodeRegistered(registration, packet, output);
    }

    private static <T extends Packet> T decodeRegistered(
            PacketRegistration<T> registration, ByteBuffer payload) {
        T packet = registration.factory().create();
        ByteBufferPacketReader reader = new ByteBufferPacketReader(payload);
        registration.codec().decode(packet, reader);
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException("Packet decoder left trailing bytes");
        }
        return packet;
    }

    private static <T extends Packet> int encodeRegistered(
            PacketRegistration<T> registration, Packet packet, ByteBuffer output) {
        T typed = registration.packetType().cast(packet);
        ByteBufferPacketWriter writer = new ByteBufferPacketWriter(output);
        registration.codec().encode(typed, writer);
        return writer.writtenBytes();
    }
}
