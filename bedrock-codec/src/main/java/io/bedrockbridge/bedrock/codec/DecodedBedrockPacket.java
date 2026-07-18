package io.bedrockbridge.bedrock.codec;

import io.bedrockbridge.bedrock.packet.play.BedrockPlayPacket;
import java.util.Objects;

/** Typed packet paired with the sub-client routing fields from its common header. */
public record DecodedBedrockPacket(BedrockPacketHeader header, BedrockPlayPacket packet) {
  /** Validates decoded components. */
  public DecodedBedrockPacket {
    Objects.requireNonNull(header, "header");
    Objects.requireNonNull(packet, "packet");
    if (header.packetId() != packet.packetId()) {
      throw new IllegalArgumentException("Decoded packet ID differs from its header");
    }
  }
}
