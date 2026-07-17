package io.bedrockbridge.bedrock.codec;

import io.bedrockbridge.bedrock.BedrockValidationException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/** Growable, maximum-bounded writer for Bedrock primitives and canonical varints. */
public final class BedrockBinaryWriter {
  private static final int INITIAL_CAPACITY = 128;

  private final int maximumBytes;
  private byte[] output;
  private int position;

  /** Creates an empty writer that can never grow past the supplied byte budget. */
  public BedrockBinaryWriter(int maximumBytes) {
    if (maximumBytes < 1) {
      throw new IllegalArgumentException("maximumBytes must be positive");
    }
    this.maximumBytes = maximumBytes;
    output = new byte[Math.min(INITIAL_CAPACITY, maximumBytes)];
  }

  /** Writes one byte. */
  public void writeByte(int value) {
    require(1);
    output[position++] = (byte) value;
  }

  /** Writes a boolean as exactly zero or one. */
  public void writeBoolean(boolean value) {
    writeByte(value ? 1 : 0);
  }

  /** Writes a signed little-endian short. */
  public void writeShortLE(int value) {
    require(Short.BYTES);
    output[position++] = (byte) value;
    output[position++] = (byte) (value >>> 8);
  }

  /** Writes an unsigned little-endian short. */
  public void writeUnsignedShortLE(int value) {
    if (value < 0 || value > 0xFFFF) {
      throw new IllegalArgumentException("Unsigned short is out of range");
    }
    writeShortLE(value);
  }

  /** Writes a signed little-endian integer. */
  public void writeIntLE(int value) {
    require(Integer.BYTES);
    for (int shift = 0; shift < Integer.SIZE; shift += Byte.SIZE) {
      output[position++] = (byte) (value >>> shift);
    }
  }

  /** Writes an unsigned little-endian integer supplied in a long. */
  public void writeUnsignedIntLE(long value) {
    if (value < 0 || value > 0xFFFF_FFFFL) {
      throw new IllegalArgumentException("Unsigned integer is out of range");
    }
    writeIntLE((int) value);
  }

  /** Writes a signed little-endian long. */
  public void writeLongLE(long value) {
    require(Long.BYTES);
    for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
      output[position++] = (byte) (value >>> shift);
    }
  }

  /** Writes a little-endian IEEE-754 float. */
  public void writeFloatLE(float value) {
    writeIntLE(Float.floatToRawIntBits(value));
  }

  /** Writes a signed big-endian integer used by network-version fields. */
  public void writeIntBE(int value) {
    writeIntLE(Integer.reverseBytes(value));
  }

  /** Writes a canonical unsigned 32-bit varint. */
  public void writeUnsignedVarInt(long value) {
    if (value < 0 || value > 0xFFFF_FFFFL) {
      throw new IllegalArgumentException("Unsigned varint is out of range");
    }
    long remaining = value;
    do {
      int current = (int) (remaining & 0x7F);
      remaining >>>= 7;
      writeByte(remaining == 0 ? current : current | 0x80);
    } while (remaining != 0);
  }

  /** Writes a ZigZag-encoded signed 32-bit varint. */
  public void writeVarInt(int value) {
    writeUnsignedVarInt(Integer.toUnsignedLong((value << 1) ^ (value >> 31)));
  }

  /** Writes a canonical unsigned 64-bit varint from a Java long bit pattern. */
  public void writeUnsignedVarLong(long value) {
    long remaining = value;
    do {
      int current = (int) (remaining & 0x7F);
      remaining >>>= 7;
      writeByte(remaining == 0 ? current : current | 0x80);
    } while (remaining != 0);
  }

  /** Writes a ZigZag-encoded signed 64-bit varint. */
  public void writeVarLong(long value) {
    writeUnsignedVarLong((value << 1) ^ (value >> 63));
  }

  /** Writes a length-prefixed UTF-8 string under a caller-supplied byte limit. */
  public void writeString(String value, int maximumStringBytes) {
    if (maximumStringBytes < 0) {
      throw new IllegalArgumentException("maximumStringBytes must be nonnegative");
    }
    byte[] bytes = Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8);
    if (bytes.length > maximumStringBytes) {
      throw new BedrockValidationException("String exceeds configured byte limit");
    }
    writeUnsignedVarInt(bytes.length);
    writeBytes(bytes);
  }

  /** Writes every byte in the supplied array. */
  public void writeBytes(byte[] value) {
    byte[] bytes = Objects.requireNonNull(value, "value");
    require(bytes.length);
    System.arraycopy(bytes, 0, output, position, bytes.length);
    position += bytes.length;
  }

  /** Writes the remaining source bytes without changing its position. */
  public void writeBytes(ByteBuffer value) {
    ByteBuffer source = Objects.requireNonNull(value, "value").duplicate();
    require(source.remaining());
    int length = source.remaining();
    source.get(output, position, length);
    position += length;
  }

  /** Returns the encoded byte count. */
  public int writtenBytes() {
    return position;
  }

  /** Returns an exact defensive copy of the encoded bytes. */
  public byte[] toByteArray() {
    return Arrays.copyOf(output, position);
  }

  private void require(int additionalBytes) {
    if (additionalBytes < 0 || (long) position + additionalBytes > maximumBytes) {
      throw new BedrockValidationException("Encoded Bedrock payload exceeds configured limit");
    }
    int required = position + additionalBytes;
    if (required > output.length) {
      long doubled = (long) output.length * 2;
      int expanded = (int) Math.min(Math.max(doubled, required), maximumBytes);
      output = Arrays.copyOf(output, expanded);
    }
  }
}
