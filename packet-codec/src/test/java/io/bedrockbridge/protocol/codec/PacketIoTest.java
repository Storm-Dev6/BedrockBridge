package io.bedrockbridge.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class PacketIoTest {
    @Test
    void roundTripsPrimitivesAndUtf8() {
        ByteBuffer buffer = ByteBuffer.allocate(64);
        var writer = new ByteBufferPacketWriter(buffer);
        writer.writeVarInt(300);
        writer.writeString("árvíz", 32);
        buffer.flip();
        var reader = new ByteBufferPacketReader(buffer);
        assertEquals(300, reader.readVarInt());
        assertEquals("árvíz", reader.readString(32));
        assertEquals(0, reader.remaining());
    }

    @Test
    void rejectsOverlongVarInt() {
        ByteBuffer input = ByteBuffer.wrap(new byte[] {(byte) 0x80, (byte) 0x80, (byte) 0x80,
                (byte) 0x80, (byte) 0x80, 0});
        assertThrows(
                IllegalArgumentException.class,
                () -> new ByteBufferPacketReader(input).readVarInt());
    }
}
