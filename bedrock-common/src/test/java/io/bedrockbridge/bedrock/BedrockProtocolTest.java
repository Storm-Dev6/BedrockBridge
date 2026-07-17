package io.bedrockbridge.bedrock;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.Test;

class BedrockProtocolTest {
  @Test
  void exposesDefensiveMagicAndVersion() {
    byte[] first = BedrockProtocol.offlineMessageMagic();
    byte[] second = BedrockProtocol.offlineMessageMagic();
    assertEquals(BedrockProtocol.OFFLINE_MESSAGE_MAGIC_LENGTH, first.length);
    assertNotSame(first, second);
    first[0] = 0x01;
    assertArrayEquals(second, BedrockProtocol.offlineMessageMagic());
    assertEquals(11, BedrockProtocol.HANDSHAKE_VERSION.protocolId());
  }
}
