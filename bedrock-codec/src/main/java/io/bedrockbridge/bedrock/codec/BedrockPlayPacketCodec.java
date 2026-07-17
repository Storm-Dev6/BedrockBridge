package io.bedrockbridge.bedrock.codec;

import io.bedrockbridge.bedrock.packet.play.BedrockPlayPacket;

/** Stateless serializer and factory-decoder for one typed Bedrock packet layout. */
public interface BedrockPlayPacketCodec<T extends BedrockPlayPacket> {
  /** Encodes packet fields without the common packet header. */
  void encode(T packet, BedrockBinaryWriter writer);

  /** Decodes packet fields from the bytes remaining after the common packet header. */
  T decode(BedrockBinaryReader reader);
}
