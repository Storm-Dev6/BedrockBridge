package io.bedrockbridge.network.raknet;

import io.bedrockbridge.network.core.UnsignedTriad;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Allocation-free encoder/decoder for the Phase 2 RakNet frame envelope. */
public final class RakNetFrameCodec {
  private static final int SPLIT_FLAG = 0x10;

  /** Encodes one frame into a caller-owned buffer. */
  public void encode(RakNetFrame frame, ByteBuffer output) {
    ByteBuffer payload = frame.payload().duplicate();
    if (payload.remaining() > 8191) {
      throw new IllegalArgumentException("Frame payload exceeds 8191 bytes");
    }
    int flags = frame.reliability().protocolId() << 5;
    if (frame.split() != null) {
      flags |= SPLIT_FLAG;
    }
    output
        .put((byte) flags)
        .order(ByteOrder.BIG_ENDIAN)
        .putShort((short) (payload.remaining() * 8));
    if (frame.reliability().isReliable()) {
      putTriad(output, frame.reliableIndex());
    }
    if (frame.reliability().isSequenced()) {
      putTriad(output, frame.sequenceIndex());
    }
    if (frame.reliability().isOrdered()) {
      putTriad(output, frame.orderIndex());
      output.put((byte) frame.orderChannel());
    }
    if (frame.split() != null) {
      output.putInt(frame.split().count());
      output.putShort((short) frame.split().id());
      output.putInt(frame.split().index());
    }
    output.put(payload);
  }

  /** Decodes one complete frame and advances the input position. */
  public RakNetFrame decode(ByteBuffer input) {
    require(input, 3);
    int flags = Byte.toUnsignedInt(input.get());
    int reliabilityId = flags >>> 5;
    if (reliabilityId >= Reliability.values().length) {
      throw new IllegalArgumentException("Unsupported reliability id: " + reliabilityId);
    }
    Reliability reliability = Reliability.fromProtocolId(reliabilityId);
    int bitLength = Short.toUnsignedInt(input.order(ByteOrder.BIG_ENDIAN).getShort());
    int length = (bitLength + 7) / 8;
    int reliable = reliability.isReliable() ? getTriad(input) : 0;
    int sequence = reliability.isSequenced() ? getTriad(input) : 0;
    int order = 0;
    int channel = 0;
    if (reliability.isOrdered()) {
      order = getTriad(input);
      require(input, 1);
      channel = Byte.toUnsignedInt(input.get());
    }
    RakNetFrame.SplitInfo split = null;
    if ((flags & SPLIT_FLAG) != 0) {
      require(input, 10);
      split =
          new RakNetFrame.SplitInfo(
              input.getInt(), Short.toUnsignedInt(input.getShort()), input.getInt());
    }
    require(input, length);
    ByteBuffer payload = input.slice(input.position(), length).asReadOnlyBuffer();
    input.position(input.position() + length);
    return new RakNetFrame(reliability, reliable, sequence, order, channel, split, payload);
  }

  /** Writes one unsigned 24-bit little-endian integer. */
  public static void putTriad(ByteBuffer output, int value) {
    int normalized = UnsignedTriad.normalize(value);
    output.put((byte) normalized).put((byte) (normalized >>> 8)).put((byte) (normalized >>> 16));
  }

  /** Reads one unsigned 24-bit little-endian integer. */
  public static int getTriad(ByteBuffer input) {
    require(input, 3);
    return Byte.toUnsignedInt(input.get())
        | (Byte.toUnsignedInt(input.get()) << 8)
        | (Byte.toUnsignedInt(input.get()) << 16);
  }

  private static void require(ByteBuffer input, int bytes) {
    if (bytes < 0 || input.remaining() < bytes) {
      throw new IllegalArgumentException("Truncated RakNet frame");
    }
  }
}
