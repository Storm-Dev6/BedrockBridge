package io.bedrockbridge.bedrock.codec;

import io.bedrockbridge.bedrock.BedrockProtocolLimits;
import io.bedrockbridge.bedrock.BedrockValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Length-prefixes complete packet frames into a bounded uncompressed Bedrock batch. */
public final class BedrockBatchCodec {
  private final BedrockProtocolLimits limits;
  private final BedrockPacketFrameCodec frames;

  /** Creates a batch codec under immutable session limits. */
  public BedrockBatchCodec(BedrockProtocolLimits limits) {
    this.limits = Objects.requireNonNull(limits, "limits");
    frames = new BedrockPacketFrameCodec(limits);
  }

  /** Encodes one nonempty sequence of packet frames. */
  public byte[] encode(List<BedrockPacketFrame> batch) {
    List<BedrockPacketFrame> values = List.copyOf(Objects.requireNonNull(batch, "batch"));
    validateCount(values.size());
    BedrockBinaryWriter writer = new BedrockBinaryWriter(limits.maximumDecompressedBatchBytes());
    for (BedrockPacketFrame frame : values) {
      byte[] encoded = frames.encode(frame);
      writer.writeUnsignedVarInt(encoded.length);
      writer.writeBytes(encoded);
    }
    return writer.toByteArray();
  }

  /** Decodes a nonempty batch and rejects partial or over-budget entries before copying. */
  public List<BedrockPacketFrame> decode(byte[] encoded) {
    byte[] bytes = Objects.requireNonNull(encoded, "encoded");
    if (bytes.length == 0 || bytes.length > limits.maximumDecompressedBatchBytes()) {
      throw new BedrockValidationException("Bedrock batch size is invalid");
    }
    BedrockBinaryReader reader = new BedrockBinaryReader(bytes, limits);
    List<BedrockPacketFrame> result = new ArrayList<>();
    while (reader.remaining() > 0) {
      if (result.size() >= limits.maximumPacketsPerBatch()) {
        throw new BedrockValidationException("Bedrock batch packet count exceeds limit");
      }
      int length = reader.readUnsignedVarInt(limits.maximumPacketBytes());
      if (length == 0) {
        throw new BedrockValidationException("Bedrock batch contains an empty packet");
      }
      result.add(frames.decode(reader.readBytes(length)));
    }
    return List.copyOf(result);
  }

  private void validateCount(int count) {
    if (count < 1 || count > limits.maximumPacketsPerBatch()) {
      throw new BedrockValidationException("Bedrock batch packet count is invalid");
    }
  }
}
