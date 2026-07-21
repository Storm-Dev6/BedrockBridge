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
    assertEquals(748, BedrockProtocol.PLAY_VERSION_748.protocolId());
    assertEquals("1.21.40", BedrockProtocol.PLAY_VERSION_748.name());
    assertEquals(1001, BedrockProtocol.PLAY_VERSION_1001.protocolId());
    assertEquals("1.26.33", BedrockProtocol.PLAY_VERSION_1001.name());
    assertEquals(
        BedrockProtocol.PLAY_VERSION_1001,
        BedrockProtocol.playVersion(BedrockProtocol.NETWORK_PROTOCOL_1001));
    assertEquals(BedrockProtocol.PLAY_VERSION_1001, BedrockProtocol.PREFERRED_PLAY_VERSION);
  }

  @Test
  void protocolLimitsArePositiveAndInternallyBounded() {
    BedrockProtocolLimits limits = BedrockProtocolLimits.defaults();
    assertEquals(1 << 20, limits.maximumPacketBytes());
    assertEquals(4 << 20, limits.maximumDecompressedBatchBytes());
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> new BedrockProtocolLimits(1, 2, 1, 1, 1, 1, 1, 1, 1, 1));
  }
}
