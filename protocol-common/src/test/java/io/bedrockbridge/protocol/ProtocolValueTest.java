package io.bedrockbridge.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ProtocolValueTest {
    @Test
    void packetKeyIncludesVersionStateAndDirection() {
        var version = new ProtocolVersion("test", "1.0", 1);
        var key = new PacketKey(version, ProtocolState.LOGIN, PacketDirection.SERVERBOUND, 3);
        assertEquals(3, key.packetId());
        assertThrows(
                IllegalArgumentException.class,
                () -> new PacketKey(version, ProtocolState.LOGIN, PacketDirection.SERVERBOUND, -1));
    }
}
