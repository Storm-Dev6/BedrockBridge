package io.bedrockbridge.bedrock.packet.play;

import io.bedrockbridge.bedrock.BedrockPacketIds;
import io.bedrockbridge.protocol.PacketDirection;

/** Empty client acknowledgement that completes the Bedrock encryption handshake. */
public record ClientToServerHandshakePacket() implements BedrockPlayPacket {
  @Override
  public int packetId() {
    return BedrockPacketIds.CLIENT_TO_SERVER_HANDSHAKE;
  }

  @Override
  public PacketDirection direction() {
    return PacketDirection.SERVERBOUND;
  }
}
