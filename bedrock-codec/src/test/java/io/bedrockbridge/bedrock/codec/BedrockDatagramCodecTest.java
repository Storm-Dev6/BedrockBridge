package io.bedrockbridge.bedrock.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.bedrockbridge.bedrock.BedrockProtocol;
import io.bedrockbridge.bedrock.BedrockValidationException;
import io.bedrockbridge.bedrock.packet.OpenConnectionReply1;
import io.bedrockbridge.bedrock.packet.OpenConnectionRequest1;
import io.bedrockbridge.bedrock.packet.UnconnectedPong;
import io.bedrockbridge.network.raknet.MtuPolicy;
import io.bedrockbridge.protocol.PacketDirection;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BedrockDatagramCodecTest {
  private final BedrockDatagramCodec codec =
      new BedrockDatagramCodec(
          BedrockPacketRegistry.create(),
          new BedrockPacketValidator(new MtuPolicy(576, 1492, 1492)));

  @Test
  void roundTripsRequest1AndInfersMtuFromPadding() {
    ByteBuffer encoded = ByteBuffer.allocate(1492);
    assertEquals(1200, codec.encode(new OpenConnectionRequest1(11, 1200), encoded));
    encoded.flip();
    OpenConnectionRequest1 decoded =
        assertInstanceOf(
            OpenConnectionRequest1.class, codec.decode(encoded, PacketDirection.SERVERBOUND));
    assertEquals(1200, decoded.mtu());
  }

  @Test
  void replyContainsOfficialOfflineMagic() {
    ByteBuffer encoded = ByteBuffer.allocate(128);
    codec.encode(new OpenConnectionReply1(5, 1200), encoded);
    encoded.flip();
    encoded.get();
    byte[] magic = new byte[16];
    encoded.get(magic);
    assertArrayEquals(BedrockProtocol.offlineMessageMagic(), magic);
  }

  @Test
  void unconnectedPongUsesRakNetBigEndianMotdLength() {
    ByteBuffer encoded = ByteBuffer.allocate(128);
    codec.encode(new UnconnectedPong(0x0102030405060708L, 0x1112131415161718L, "MCPE;X"), encoded);
    encoded.flip();

    assertEquals(0x1C, Byte.toUnsignedInt(encoded.get()));
    assertEquals(0x0102030405060708L, encoded.getLong());
    assertEquals(0x1112131415161718L, encoded.getLong());
    byte[] magic = new byte[BedrockProtocol.OFFLINE_MESSAGE_MAGIC_LENGTH];
    encoded.get(magic);
    assertArrayEquals(BedrockProtocol.offlineMessageMagic(), magic);
    assertEquals(6, Short.toUnsignedInt(encoded.getShort()));
    byte[] motd = new byte[6];
    encoded.get(motd);
    assertArrayEquals("MCPE;X".getBytes(StandardCharsets.UTF_8), motd);

    encoded.rewind();
    UnconnectedPong decoded =
        assertInstanceOf(UnconnectedPong.class, codec.decode(encoded, PacketDirection.CLIENTBOUND));
    assertEquals("MCPE;X", decoded.motd());
  }

  @Test
  void rejectsUnsupportedTransportVersion() {
    ByteBuffer encoded = ByteBuffer.allocate(1492);
    encoded.put((byte) 0x05).put(BedrockProtocol.offlineMessageMagic()).put((byte) 10);
    while (encoded.position() < 1200) {
      encoded.put((byte) 0);
    }
    encoded.flip();
    assertThrows(
        BedrockValidationException.class, () -> codec.decode(encoded, PacketDirection.SERVERBOUND));
  }

  @Test
  void compressionRoundTripsAndEnforcesLimits() {
    var compression =
        new BedrockCompressionCodec(
            new CompressionSettings(CompressionAlgorithm.ZLIB, 32, 1024, 4096, 100));
    byte[] clear = "bedrock-login".repeat(20).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    assertArrayEquals(clear, compression.decompress(compression.compress(clear)));

    byte[] compressedPacket = compression.compressPacket(clear);
    assertEquals(0x00, Byte.toUnsignedInt(compressedPacket[0]));
    assertArrayEquals(clear, compression.decompressPacket(compressedPacket));

    byte[] small = "login".getBytes(StandardCharsets.UTF_8);
    byte[] uncompressedPacket = compression.compressPacket(small);
    assertEquals(0xFF, Byte.toUnsignedInt(uncompressedPacket[0]));
    assertArrayEquals(small, compression.decompressPacket(uncompressedPacket));
    assertThrows(
        BedrockValidationException.class,
        () -> compression.decompressPacket(new byte[] {0x01, 0x00}));
  }
}
