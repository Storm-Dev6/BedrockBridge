package io.bedrockbridge.bedrock.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.bedrockbridge.protocol.codec.ByteBufferPacketReader;
import io.bedrockbridge.protocol.codec.ByteBufferPacketWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class BedrockAddressCodecTest {
  @Test
  void roundTripsIpv4Address() {
    InetSocketAddress expected = new InetSocketAddress(InetAddress.getLoopbackAddress(), 19132);
    ByteBuffer buffer = ByteBuffer.allocate(32);
    BedrockAddressCodec.write(new ByteBufferPacketWriter(buffer), expected);
    buffer.flip();
    assertEquals(expected, BedrockAddressCodec.read(new ByteBufferPacketReader(buffer)));
  }
}
