package io.bedrockbridge.application.javawire;

import java.util.List;
import java.util.Map;

/** Minimal immutable Java NBT tree used for bounded Registry Data validation. */
public sealed interface JavaNbt
    permits JavaNbt.End,
        JavaNbt.ByteValue,
        JavaNbt.ShortValue,
        JavaNbt.IntValue,
        JavaNbt.LongValue,
        JavaNbt.FloatValue,
        JavaNbt.DoubleValue,
        JavaNbt.StringValue,
        JavaNbt.ListValue,
        JavaNbt.CompoundValue,
        JavaNbt.ByteArrayValue,
        JavaNbt.IntArrayValue,
        JavaNbt.LongArrayValue {
  record End() implements JavaNbt {}

  record ByteValue(byte value) implements JavaNbt {}

  record ShortValue(short value) implements JavaNbt {}

  record IntValue(int value) implements JavaNbt {}

  record LongValue(long value) implements JavaNbt {}

  record FloatValue(float value) implements JavaNbt {}

  record DoubleValue(double value) implements JavaNbt {}

  record StringValue(String value) implements JavaNbt {}

  record ListValue(int elementType, List<JavaNbt> values) implements JavaNbt {
    public ListValue {
      values = List.copyOf(values);
    }
  }

  record CompoundValue(Map<String, JavaNbt> values) implements JavaNbt {
    public CompoundValue {
      values = Map.copyOf(values);
    }
  }

  final class ByteArrayValue implements JavaNbt {
    private final byte[] values;

    public ByteArrayValue(byte[] values) {
      this.values = values.clone();
    }

    public byte[] values() {
      return values.clone();
    }
  }

  final class IntArrayValue implements JavaNbt {
    private final int[] values;

    public IntArrayValue(int[] values) {
      this.values = values.clone();
    }

    public int[] values() {
      return values.clone();
    }
  }

  final class LongArrayValue implements JavaNbt {
    private final long[] values;

    public LongArrayValue(long[] values) {
      this.values = values.clone();
    }

    public long[] values() {
      return values.clone();
    }
  }
}
