package io.bedrockbridge.protocol;

import java.nio.ByteBuffer;

/** Capacity-checked primitive writer used by generic packets and encoders. */
public interface PacketWriter {
    /** Writes one byte. */
    void writeByte(byte value);

    /** Writes a signed big-endian integer. */
    void writeInt(int value);

    /** Writes a signed big-endian long. */
    void writeLong(long value);

    /** Writes an unsigned big-endian short. */
    void writeUnsignedShort(int value);

    /** Writes a boolean as zero or one. */
    void writeBoolean(boolean value);

    /** Writes a nonnegative variable-length integer. */
    void writeVarInt(int value);

    /** Writes a length-prefixed UTF-8 string up to the requested byte limit. */
    void writeString(String value, int maximumBytes);

    /** Writes the remaining bytes without consuming the source position. */
    void writeBytes(ByteBuffer value);

    /** Returns the number of encoded bytes. */
    int writtenBytes();
}
