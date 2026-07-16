package io.bedrockbridge.protocol.registry;

import io.bedrockbridge.protocol.Packet;
import io.bedrockbridge.protocol.PacketKey;
import io.bedrockbridge.protocol.codec.PacketCodec;
import io.bedrockbridge.protocol.codec.PacketFactory;
import java.util.Objects;

/** Typed packet ID to codec and factory registration. */
public record PacketRegistration<T extends Packet>(
        PacketKey key, Class<T> packetType, PacketCodec<T> codec, PacketFactory<T> factory) {
    /** Validates every registration component. */
    public PacketRegistration {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(packetType, "packetType");
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(factory, "factory");
    }
}
