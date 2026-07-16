package io.bedrockbridge.bedrock.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.bedrockbridge.bedrock.BedrockProtocol;
import io.bedrockbridge.bedrock.packet.OpenConnectionReply1;
import io.bedrockbridge.bedrock.packet.OpenConnectionRequest1;
import io.bedrockbridge.network.raknet.MtuPolicy;
import io.bedrockbridge.protocol.PacketDirection;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class BedrockDatagramCodecTest {
    private final BedrockDatagramCodec codec = new BedrockDatagramCodec(
            BedrockPacketRegistry.create(),
            new BedrockPacketValidator(new MtuPolicy(576, 1492, 1492)));

    @Test
    void roundTripsRequest1AndInfersMtuFromPadding() {
        ByteBuffer encoded = ByteBuffer.allocate(1492);
        assertEquals(1200, codec.encode(new OpenConnectionRequest1(11, 1200), encoded));
        encoded.flip();
        OpenConnectionRequest1 decoded = assertInstanceOf(
                OpenConnectionRequest1.class,
                codec.decode(encoded, PacketDirection.SERVERBOUND));
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
        assertArrayEquals(BedrockProtocol.OFFLINE_MESSAGE_MAGIC, magic);
    }

    @Test
    void rejectsUnsupportedTransportVersion() {
        ByteBuffer encoded = ByteBuffer.allocate(1492);
        encoded.put((byte) 0x05).put(BedrockProtocol.OFFLINE_MESSAGE_MAGIC).put((byte) 10);
        while (encoded.position() < 1200) encoded.put((byte) 0);
        encoded.flip();
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.decode(encoded, PacketDirection.SERVERBOUND));
    }

    @Test
    void compressionRoundTripsAndEnforcesLimits() {
        var compression = new BedrockCompressionCodec(
                new CompressionSettings(CompressionAlgorithm.ZLIB, 0, 1024, 4096, 100));
        byte[] clear = "bedrock-login".repeat(20).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertArrayEquals(clear, compression.decompress(compression.compress(clear)));
    }
}
