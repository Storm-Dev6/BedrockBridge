package io.bedrockbridge.protocol;

import java.nio.ByteBuffer;

/** Bounds-checked primitive reader used by generic packets and decoders. */
public interface PacketReader {
    /** Reads one byte. */
    byte readByte();

    /** Reads a signed big-endian integer. */
    int readInt();

    /** Reads a signed big-endian long. */
    long readLong();

    /** Reads an unsigned big-endian short. */
    int readUnsignedShort();

    /** Reads a strict zero/one boolean. */
    boolean readBoolean();

    /** Reads a bounded unsigned variable-length integer. */
    int readVarInt();

    /** Reads a length-prefixed UTF-8 string up to the requested byte limit. */
    String readString(int maximumBytes);

    /** Returns a read-only slice of exactly length bytes and advances. */
    ByteBuffer readSlice(int length);

    /** Returns the unread byte count. */
    int remaining();
}
