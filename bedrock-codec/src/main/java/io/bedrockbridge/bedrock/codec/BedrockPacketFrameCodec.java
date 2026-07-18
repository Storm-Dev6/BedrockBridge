package io.bedrockbridge.bedrock.codec;

import io.bedrockbridge.bedrock.BedrockProtocolLimits;
import io.bedrockbridge.bedrock.BedrockValidationException;
import java.util.Objects;

/** Encodes and decodes one bounded Bedrock packet header plus field payload. */
public final class BedrockPacketFrameCodec {
  private final BedrockProtocolLimits limits;

  /** Creates a frame codec under immutable session limits. */
  public BedrockPacketFrameCodec(BedrockProtocolLimits limits) {
    this.limits = Objects.requireNonNull(limits, "limits");
  }

  /** Encodes one complete packet frame. */
  public byte[] encode(BedrockPacketFrame frame) {
    Objects.requireNonNull(frame, "frame");
    BedrockBinaryWriter writer = new BedrockBinaryWriter(limits.maximumPacketBytes());
    writer.writeUnsignedVarInt(frame.header().packedValue());
    writer.writeBytes(frame.payload());
    return writer.toByteArray();
  }

  /** Decodes one complete packet frame and preserves all bytes after its header as fields. */
  public BedrockPacketFrame decode(byte[] encoded) {
    byte[] bytes = Objects.requireNonNull(encoded, "encoded");
    if (bytes.length == 0 || bytes.length > limits.maximumPacketBytes()) {
      throw new BedrockValidationException("Bedrock packet frame size is invalid");
    }
    BedrockBinaryReader reader = new BedrockBinaryReader(bytes, limits);
    BedrockPacketHeader header;
    try {
      header = BedrockPacketHeader.fromPackedValue(reader.readUnsignedVarInt());
    } catch (IllegalArgumentException invalid) {
      throw new BedrockValidationException("Bedrock packet header is invalid");
    }
    return new BedrockPacketFrame(header, reader.readBytes(reader.remaining()));
  }
}
