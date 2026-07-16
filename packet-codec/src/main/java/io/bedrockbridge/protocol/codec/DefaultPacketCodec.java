package io.bedrockbridge.protocol.codec;

import io.bedrockbridge.protocol.Packet;
import io.bedrockbridge.protocol.PacketReader;
import io.bedrockbridge.protocol.PacketWriter;

/** Generic codec that delegates to the packet API's encode and decode methods. */
public final class DefaultPacketCodec<T extends Packet> implements PacketCodec<T> {
    @Override
    public void encode(T packet, PacketWriter writer) {
        packet.encode(writer);
    }

    @Override
    public void decode(T packet, PacketReader reader) {
        packet.decode(reader);
    }
}
