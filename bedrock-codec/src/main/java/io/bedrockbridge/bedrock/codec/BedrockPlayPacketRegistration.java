package io.bedrockbridge.bedrock.codec;

import io.bedrockbridge.bedrock.BedrockPlayState;
import io.bedrockbridge.bedrock.packet.play.BedrockPlayPacket;
import io.bedrockbridge.protocol.PacketDirection;
import io.bedrockbridge.protocol.ProtocolVersion;
import java.util.Objects;
import java.util.Set;

/** Exact-version packet registration including direction, legal states, type, and codec. */
public record BedrockPlayPacketRegistration<T extends BedrockPlayPacket>(
    ProtocolVersion version,
    int packetId,
    PacketDirection direction,
    Set<BedrockPlayState> states,
    Class<T> packetType,
    BedrockPlayPacketCodec<T> codec) {
  /** Validates and defensively copies registration metadata. */
  public BedrockPlayPacketRegistration {
    Objects.requireNonNull(version, "version");
    Objects.requireNonNull(direction, "direction");
    states = Set.copyOf(Objects.requireNonNull(states, "states"));
    Objects.requireNonNull(packetType, "packetType");
    Objects.requireNonNull(codec, "codec");
    if (packetId < 0 || packetId > 0x3FF) {
      throw new IllegalArgumentException("Bedrock packet ID must fit ten bits");
    }
    if (states.isEmpty() || states.contains(BedrockPlayState.DISCONNECTED)) {
      throw new IllegalArgumentException("Packet registration states are invalid");
    }
  }
}
