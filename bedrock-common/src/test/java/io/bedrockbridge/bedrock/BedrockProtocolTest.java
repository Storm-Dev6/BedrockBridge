package io.bedrockbridge.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.Test;

class BedrockProtocolTest {
    @Test
    void exposesDefensiveMagicAndVersion() {
        byte[] first = BedrockProtocol.offlineMessageMagic();
        byte[] second = BedrockProtocol.offlineMessageMagic();
        assertEquals(16, first.length);
        assertNotSame(first, second);
        assertEquals(11, BedrockProtocol.HANDSHAKE_VERSION.protocolId());
    }
}
