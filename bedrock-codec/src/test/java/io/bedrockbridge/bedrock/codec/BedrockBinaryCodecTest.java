package io.bedrockbridge.bedrock.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.bedrockbridge.bedrock.BedrockProtocolLimits;
import io.bedrockbridge.bedrock.BedrockValidationException;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;

class BedrockBinaryCodecTest {
  private static final BedrockProtocolLimits LIMITS = limits(4);

  @Test
  void encodesIndependentPrimitiveVectors() {
    var writer = new BedrockBinaryWriter(64);
    writer.writeIntBE(748);
    writer.writeIntLE(0x1234_5678);
    writer.writeFloatLE(1.0F);
    writer.writeUnsignedVarInt(300);
    writer.writeVarInt(-1);
    writer.writeString("A", 8);

    assertArrayEquals(
        bytes(
            0x00, 0x00, 0x02, 0xEC, 0x78, 0x56, 0x34, 0x12, 0x00, 0x00, 0x80, 0x3F, 0xAC, 0x02,
            0x01, 0x01, 0x41),
        writer.toByteArray());
  }

  @Test
  void roundTripsVarintBoundaries() {
    long[] unsignedValues = {0, 1, 127, 128, 300, 0xFFFF_FFFFL};
    for (long expected : unsignedValues) {
      var writer = new BedrockBinaryWriter(16);
      writer.writeUnsignedVarInt(expected);
      assertEquals(
          expected, new BedrockBinaryReader(writer.toByteArray(), LIMITS).readUnsignedVarInt());
    }

    int[] signedValues = {Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE};
    for (int expected : signedValues) {
      var writer = new BedrockBinaryWriter(16);
      writer.writeVarInt(expected);
      assertEquals(expected, new BedrockBinaryReader(writer.toByteArray(), LIMITS).readVarInt());
    }
  }

  @Test
  void rejectsMalformedVarintsStringsAndTruncation() {
    assertThrows(
        BedrockValidationException.class,
        () -> new BedrockBinaryReader(bytes(0x80, 0x00), LIMITS).readUnsignedVarInt());
    assertThrows(
        BedrockValidationException.class,
        () ->
            new BedrockBinaryReader(bytes(0xFF, 0xFF, 0xFF, 0xFF, 0x10), LIMITS)
                .readUnsignedVarInt());
    assertThrows(
        BedrockValidationException.class,
        () -> new BedrockBinaryReader(bytes(0x02, 0xC3, 0x28), LIMITS).readString(8));
    assertThrows(
        BedrockValidationException.class,
        () -> new BedrockBinaryReader(bytes(0x04, 0x01), LIMITS).readBytes(4));
  }

  @Test
  void packetHeaderMatchesMojangBitLayout() {
    var header = new BedrockPacketHeader(193, 2, 1);
    assertEquals(0x18C1, header.packedValue());
    assertEquals(header, BedrockPacketHeader.fromPackedValue(0x18C1));

    var frameCodec = new BedrockPacketFrameCodec(LIMITS);
    var frame = new BedrockPacketFrame(header, bytes(0x00, 0x00, 0x02, 0xEC));
    assertArrayEquals(bytes(0xC1, 0x31, 0x00, 0x00, 0x02, 0xEC), frameCodec.encode(frame));
    assertEquals(frame, frameCodec.decode(frameCodec.encode(frame)));
    assertThrows(IllegalArgumentException.class, () -> BedrockPacketHeader.fromPackedValue(0x4000));
  }

  @Test
  void packetFrameOwnsItsPayload() {
    byte[] source = bytes(1, 2, 3);
    var frame = new BedrockPacketFrame(new BedrockPacketHeader(1, 0, 0), source);
    source[0] = 9;
    assertEquals(1, Byte.toUnsignedInt(frame.payload().get()));
    assertTrue(frame.payload().isReadOnly());
  }

  @Test
  void batchMatchesIndependentLengthPrefixedVector() {
    var batch = new BedrockBatchCodec(LIMITS);
    List<BedrockPacketFrame> frames =
        List.of(
            new BedrockPacketFrame(new BedrockPacketHeader(1, 0, 0), bytes(0xAA)),
            new BedrockPacketFrame(
                new BedrockPacketHeader(193, 0, 0), bytes(0x00, 0x00, 0x02, 0xEC)));
    byte[] vector = bytes(0x02, 0x01, 0xAA, 0x06, 0xC1, 0x01, 0x00, 0x00, 0x02, 0xEC);
    assertArrayEquals(vector, batch.encode(frames));
    assertEquals(frames, batch.decode(vector));
  }

  @Test
  void rejectsMalformedAndOverCountBatches() {
    var twoPacketBatch = new BedrockBatchCodec(limits(2));
    assertThrows(BedrockValidationException.class, () -> twoPacketBatch.decode(bytes(0x00)));
    assertThrows(BedrockValidationException.class, () -> twoPacketBatch.decode(bytes(0x02, 0x01)));
    assertThrows(
        BedrockValidationException.class,
        () -> twoPacketBatch.decode(bytes(0x01, 0x01, 0x01, 0x01, 0x01, 0x01)));
  }

  private static BedrockProtocolLimits limits(int maximumPackets) {
    return new BedrockProtocolLimits(256, 64, maximumPackets, 128, 16, 64, 32, 8, 16, 16);
  }

  private static byte[] bytes(int... values) {
    ByteBuffer buffer = ByteBuffer.allocate(values.length);
    for (int value : values) {
      buffer.put((byte) value);
    }
    return buffer.array();
  }
}
