package io.bedrockbridge.protocol.registry;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.bedrockbridge.common.RegistrationException;
import io.bedrockbridge.protocol.Packet;
import io.bedrockbridge.protocol.PacketDirection;
import io.bedrockbridge.protocol.PacketKey;
import io.bedrockbridge.protocol.PacketReader;
import io.bedrockbridge.protocol.PacketWriter;
import io.bedrockbridge.protocol.ProtocolState;
import io.bedrockbridge.protocol.ProtocolVersion;
import io.bedrockbridge.protocol.codec.DefaultPacketCodec;
import org.junit.jupiter.api.Test;

class RegistryTest {
    @Test
    void mapsIdToCodecAndFactoryWithoutSwitches() {
        ProtocolVersion version = new ProtocolVersion("test", "1", 1);
        PacketKey key = new PacketKey(version, ProtocolState.PLAY, PacketDirection.CLIENTBOUND, 7);
        var registration = new PacketRegistration<>(
                key, TestPacket.class, new DefaultPacketCodec<>(), TestPacket::new);
        var registry = new DynamicPacketRegistry();
        registry.register(registration);
        assertSame(registration, registry.find(key).orElseThrow());
        assertTrue(new PacketIdRegistry(registry)
                .find(TestPacket.class, version, ProtocolState.PLAY, PacketDirection.CLIENTBOUND)
                .isPresent());
        assertThrows(RegistrationException.class, () -> registry.register(registration));
    }

    private static final class TestPacket implements Packet {
        private static final ProtocolVersion VERSION = new ProtocolVersion("test", "1", 1);

        @Override public int packetId() { return 7; }
        @Override public ProtocolVersion protocolVersion() { return VERSION; }
        @Override public ProtocolState state() { return ProtocolState.PLAY; }
        @Override public PacketDirection direction() { return PacketDirection.CLIENTBOUND; }
        @Override public void encode(PacketWriter writer) {}
        @Override public void decode(PacketReader reader) {}
    }
}
