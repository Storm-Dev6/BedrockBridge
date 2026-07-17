package io.bedrockbridge.protocol.codec;

import io.bedrockbridge.protocol.PacketReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** ByteBuffer-backed reader that rejects truncated and overlong values before allocation. */
public final class ByteBufferPacketReader implements PacketReader {
  private final ByteBuffer input;

  /** Wraps the caller-owned input view. */
  public ByteBufferPacketReader(ByteBuffer input) {
    this.input = Objects.requireNonNull(input, "input").slice();
  }

  @Override
  public byte readByte() {
    require(1);
    return input.get();
  }

  @Override
  public int readInt() {
    require(Integer.BYTES);
    return input.getInt();
  }

  @Override
  public long readLong() {
    require(Long.BYTES);
    return input.getLong();
  }

  @Override
  public int readUnsignedShort() {
    require(Short.BYTES);
    return Short.toUnsignedInt(input.getShort());
  }

  @Override
  public boolean readBoolean() {
    byte value = readByte();
    if (value != 0 && value != 1) {
      throw new IllegalArgumentException("Boolean must be encoded as zero or one");
    }
    return value == 1;
  }

  @Override
  public int readVarInt() {
    int result = 0;
    for (int index = 0; index < 5; index++) {
      int current = Byte.toUnsignedInt(readByte());
      result |= (current & 0x7F) << (index * 7);
      if ((current & 0x80) == 0) {
        if (result < 0) {
          throw new IllegalArgumentException("VarInt exceeds signed positive range");
        }
        return result;
      }
    }
    throw new IllegalArgumentException("VarInt exceeds five bytes");
  }

  @Override
  public String readString(int maximumBytes) {
    int length = readVarInt();
    if (maximumBytes < 0 || length > maximumBytes) {
      throw new IllegalArgumentException("String length exceeds configured limit");
    }
    ByteBuffer bytes = readSlice(length);
    byte[] copy = new byte[length];
    bytes.get(copy);
    return new String(copy, StandardCharsets.UTF_8);
  }

  @Override
  public ByteBuffer readSlice(int length) {
    require(length);
    ByteBuffer slice = input.slice(input.position(), length).asReadOnlyBuffer();
    input.position(input.position() + length);
    return slice;
  }

  @Override
  public int remaining() {
    return input.remaining();
  }

  private void require(int bytes) {
    if (bytes < 0 || input.remaining() < bytes) {
      throw new IllegalArgumentException("Packet payload is truncated");
    }
  }
}
