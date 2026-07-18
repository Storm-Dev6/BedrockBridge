package io.bedrockbridge.application.javawire;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded big-endian NBT decoder for Java Registry Data entries. */
public final class JavaNbtCodec {
  private static final int MAX_DEPTH = 32;
  private static final int MAX_ELEMENTS = 65_536;
  private static final int MAX_STRING = 32_767;

  private JavaNbtCodec() {}

  public static JavaNbt read(DataInputStream input) throws IOException, JavaWireException {
    return readNamed(input, 0);
  }

  private static JavaNbt readNamed(DataInputStream input, int depth)
      throws IOException, JavaWireException {
    if (depth > MAX_DEPTH) {
      throw new JavaWireException("NBT nesting exceeds " + MAX_DEPTH);
    }
    int type = input.readUnsignedByte();
    if (type == 0) {
      return new JavaNbt.End();
    }
    readString(input);
    return readPayload(input, type, depth + 1);
  }

  private static JavaNbt readPayload(DataInputStream input, int type, int depth)
      throws IOException, JavaWireException {
    return switch (type) {
      case 1 -> new JavaNbt.ByteValue(input.readByte());
      case 2 -> new JavaNbt.ShortValue(input.readShort());
      case 3 -> new JavaNbt.IntValue(input.readInt());
      case 4 -> new JavaNbt.LongValue(input.readLong());
      case 5 -> new JavaNbt.FloatValue(input.readFloat());
      case 6 -> new JavaNbt.DoubleValue(input.readDouble());
      case 7 -> new JavaNbt.ByteArrayValue(readBytes(input));
      case 8 -> new JavaNbt.StringValue(readString(input));
      case 9 -> readList(input, depth);
      case 10 -> readCompound(input, depth);
      case 11 -> new JavaNbt.IntArrayValue(readInts(input));
      case 12 -> new JavaNbt.LongArrayValue(readLongs(input));
      default -> throw new JavaWireException("unsupported NBT tag type=" + type);
    };
  }

  private static JavaNbt readList(DataInputStream input, int depth)
      throws IOException, JavaWireException {
    int elementType = input.readUnsignedByte();
    int count = boundedCount(input.readInt());
    List<JavaNbt> values = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      values.add(readPayload(input, elementType, depth + 1));
    }
    return new JavaNbt.ListValue(elementType, values);
  }

  private static JavaNbt readCompound(DataInputStream input, int depth)
      throws IOException, JavaWireException {
    Map<String, JavaNbt> values = new LinkedHashMap<>();
    while (true) {
      int type = input.readUnsignedByte();
      if (type == 0) {
        return new JavaNbt.CompoundValue(values);
      }
      if (values.size() >= MAX_ELEMENTS) {
        throw new JavaWireException("NBT compound exceeds element limit");
      }
      String name = readString(input);
      values.put(name, readPayload(input, type, depth + 1));
    }
  }

  private static byte[] readBytes(DataInputStream input) throws IOException, JavaWireException {
    int count = boundedCount(input.readInt());
    byte[] values = input.readNBytes(count);
    if (values.length != count) {
      throw new EOFException("truncated NBT byte array");
    }
    return values;
  }

  private static int[] readInts(DataInputStream input) throws IOException, JavaWireException {
    int count = boundedCount(input.readInt());
    int[] values = new int[count];
    for (int i = 0; i < count; i++) {
      values[i] = input.readInt();
    }
    return values;
  }

  private static long[] readLongs(DataInputStream input) throws IOException, JavaWireException {
    int count = boundedCount(input.readInt());
    long[] values = new long[count];
    for (int i = 0; i < count; i++) {
      values[i] = input.readLong();
    }
    return values;
  }

  private static int boundedCount(int count) throws JavaWireException {
    if (count < 0 || count > MAX_ELEMENTS) {
      throw new JavaWireException("NBT element count out of bounds: " + count);
    }
    return count;
  }

  private static String readString(DataInputStream input) throws IOException, JavaWireException {
    int length = input.readUnsignedShort();
    if (length > MAX_STRING) {
      throw new JavaWireException("NBT string exceeds limit");
    }
    byte[] bytes = input.readNBytes(length);
    if (bytes.length != length) {
      throw new EOFException("truncated NBT string");
    }
    return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
  }
}
