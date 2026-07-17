package io.bedrockbridge.bedrock.codec;

import io.bedrockbridge.bedrock.BedrockProtocolLimits;
import io.bedrockbridge.bedrock.BedrockValidationException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Bounds-checked reader for Bedrock little-endian primitives and canonical varints. */
public final class BedrockBinaryReader {
  private final ByteBuffer input;
  private final BedrockProtocolLimits limits;

  /** Creates a reader over an isolated view of the caller-owned input. */
  public BedrockBinaryReader(ByteBuffer input, BedrockProtocolLimits limits) {
    this.input = Objects.requireNonNull(input, "input").slice().order(ByteOrder.LITTLE_ENDIAN);
    this.limits = Objects.requireNonNull(limits, "limits");
  }

  /** Creates a reader over the complete byte array. */
  public BedrockBinaryReader(byte[] input, BedrockProtocolLimits limits) {
    this(ByteBuffer.wrap(Objects.requireNonNull(input, "input")), limits);
  }

  /** Reads one signed byte. */
  public byte readByte() {
    require(1);
    return input.get();
  }

  /** Reads one unsigned byte. */
  public int readUnsignedByte() {
    return Byte.toUnsignedInt(readByte());
  }

  /** Reads a strict zero-or-one boolean. */
  public boolean readBoolean() {
    int value = readUnsignedByte();
    if (value > 1) {
      throw malformed("Boolean must be encoded as zero or one");
    }
    return value == 1;
  }

  /** Reads a signed little-endian short. */
  public short readShortLE() {
    require(Short.BYTES);
    return input.getShort();
  }

  /** Reads an unsigned little-endian short. */
  public int readUnsignedShortLE() {
    return Short.toUnsignedInt(readShortLE());
  }

  /** Reads a signed little-endian integer. */
  public int readIntLE() {
    require(Integer.BYTES);
    return input.getInt();
  }

  /** Reads an unsigned little-endian integer into a long. */
  public long readUnsignedIntLE() {
    return Integer.toUnsignedLong(readIntLE());
  }

  /** Reads a signed little-endian long. */
  public long readLongLE() {
    require(Long.BYTES);
    return input.getLong();
  }

  /** Reads a little-endian IEEE-754 float. */
  public float readFloatLE() {
    require(Float.BYTES);
    return input.getFloat();
  }

  /** Reads a signed big-endian integer used by network-version fields. */
  public int readIntBE() {
    return Integer.reverseBytes(readIntLE());
  }

  /** Reads a canonical unsigned 32-bit varint into a long. */
  public long readUnsignedVarInt() {
    long result = 0;
    for (int index = 0; index < 5; index++) {
      int current = readUnsignedByte();
      if (index == 4 && (current & 0xF0) != 0) {
        throw malformed("Unsigned varint exceeds 32 bits");
      }
      result |= (long) (current & 0x7F) << (index * 7);
      if ((current & 0x80) == 0) {
        requireCanonicalTerminal(index, current);
        return result;
      }
    }
    throw malformed("Unsigned varint exceeds five bytes");
  }

  /** Reads a canonical unsigned varint constrained to a nonnegative Java int. */
  public int readUnsignedVarInt(int maximumValue) {
    if (maximumValue < 0) {
      throw new IllegalArgumentException("maximumValue must be nonnegative");
    }
    long value = readUnsignedVarInt();
    if (value > maximumValue) {
      throw malformed("Unsigned varint exceeds configured limit");
    }
    return (int) value;
  }

  /** Reads a ZigZag-encoded signed 32-bit varint. */
  public int readVarInt() {
    int encoded = (int) readUnsignedVarInt();
    return (encoded >>> 1) ^ -(encoded & 1);
  }

  /** Reads a canonical unsigned 64-bit varint as its Java long bit pattern. */
  public long readUnsignedVarLong() {
    long result = 0;
    for (int index = 0; index < 10; index++) {
      int current = readUnsignedByte();
      if (index == 9 && (current & 0xFE) != 0) {
        throw malformed("Unsigned varlong exceeds 64 bits");
      }
      result |= (long) (current & 0x7F) << (index * 7);
      if ((current & 0x80) == 0) {
        requireCanonicalTerminal(index, current);
        return result;
      }
    }
    throw malformed("Unsigned varlong exceeds ten bytes");
  }

  /** Reads a ZigZag-encoded signed 64-bit varint. */
  public long readVarLong() {
    long encoded = readUnsignedVarLong();
    return (encoded >>> 1) ^ -(encoded & 1);
  }

  /** Reads a UTF-8 string bounded by both the caller and session limits. */
  public String readString(int maximumBytes) {
    int effectiveMaximum = Math.min(validateMaximum(maximumBytes), limits.maximumStringBytes());
    int length = readUnsignedVarInt(effectiveMaximum);
    ByteBuffer bytes = readSlice(length);
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(bytes)
          .toString();
    } catch (CharacterCodingException invalid) {
      throw malformed("String contains invalid UTF-8");
    }
  }

  /** Returns an isolated read-only view of exactly the requested bytes. */
  public ByteBuffer readSlice(int length) {
    require(length);
    ByteBuffer slice = input.slice(input.position(), length).asReadOnlyBuffer();
    input.position(input.position() + length);
    return slice;
  }

  /** Copies exactly the requested number of bytes. */
  public byte[] readBytes(int length) {
    ByteBuffer slice = readSlice(length);
    byte[] bytes = new byte[length];
    slice.get(bytes);
    return bytes;
  }

  /** Returns the number of unread bytes. */
  public int remaining() {
    return input.remaining();
  }

  private static int validateMaximum(int maximumBytes) {
    if (maximumBytes < 0) {
      throw new IllegalArgumentException("maximumBytes must be nonnegative");
    }
    return maximumBytes;
  }

  private static void requireCanonicalTerminal(int index, int terminal) {
    if (index > 0 && (terminal & 0x7F) == 0) {
      throw malformed("Varint uses a non-canonical overlong encoding");
    }
  }

  private void require(int bytes) {
    if (bytes < 0 || input.remaining() < bytes) {
      throw malformed("Bedrock payload is truncated");
    }
  }

  private static BedrockValidationException malformed(String message) {
    return new BedrockValidationException(message);
  }
}
