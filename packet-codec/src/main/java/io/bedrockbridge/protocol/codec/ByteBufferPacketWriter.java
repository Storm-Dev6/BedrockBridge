package io.bedrockbridge.protocol.codec;

import io.bedrockbridge.protocol.PacketWriter;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** ByteBuffer-backed writer with explicit capacity and string-size enforcement. */
public final class ByteBufferPacketWriter implements PacketWriter {
    private final ByteBuffer output;
    private final int start;

    /** Wraps a caller-owned output buffer at its current position. */
    public ByteBufferPacketWriter(ByteBuffer output) {
        this.output = Objects.requireNonNull(output, "output");
        start = output.position();
    }

    @Override
    public void writeByte(byte value) {
        require(1);
        output.put(value);
    }

    @Override
    public void writeInt(int value) {
        require(Integer.BYTES);
        output.putInt(value);
    }

    @Override
    public void writeVarInt(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("VarInt value must be nonnegative");
        }
        int remaining = value;
        do {
            int next = remaining & 0x7F;
            remaining >>>= 7;
            writeByte((byte) (remaining == 0 ? next : next | 0x80));
        } while (remaining != 0);
    }

    @Override
    public void writeString(String value, int maximumBytes) {
        byte[] bytes = Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8);
        if (maximumBytes < 0 || bytes.length > maximumBytes) {
            throw new IllegalArgumentException("String length exceeds configured limit");
        }
        writeVarInt(bytes.length);
        require(bytes.length);
        output.put(bytes);
    }

    @Override
    public void writeBytes(ByteBuffer value) {
        ByteBuffer copy = Objects.requireNonNull(value, "value").duplicate();
        require(copy.remaining());
        output.put(copy);
    }

    @Override
    public int writtenBytes() {
        return output.position() - start;
    }

    private void require(int bytes) {
        if (bytes < 0 || output.remaining() < bytes) {
            throw new BufferOverflowException();
        }
    }
}
