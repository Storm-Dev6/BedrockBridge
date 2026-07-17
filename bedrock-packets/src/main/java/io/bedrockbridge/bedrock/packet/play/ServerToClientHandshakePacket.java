package io.bedrockbridge.bedrock.packet.play;

import io.bedrockbridge.bedrock.BedrockPacketIds;
import io.bedrockbridge.protocol.PacketDirection;
import java.util.Objects;

/** Server handshake carrying the signed JWT used to establish Bedrock packet encryption. */
public record ServerToClientHandshakePacket(String handshakeJwt) implements BedrockPlayPacket {
  public ServerToClientHandshakePacket {
    Objects.requireNonNull(handshakeJwt, "handshakeJwt");
    if (handshakeJwt.isBlank()) {
      throw new IllegalArgumentException("Handshake JWT must not be blank");
    }
  }

  @Override
  public int packetId() {
    return BedrockPacketIds.SERVER_TO_CLIENT_HANDSHAKE;
  }

  @Override
  public PacketDirection direction() {
    return PacketDirection.CLIENTBOUND;
  }
}
