package io.bedrockbridge.bedrock.packet.play;

import io.bedrockbridge.bedrock.BedrockPacketIds;
import io.bedrockbridge.protocol.PacketDirection;

/** Client request carrying its big-endian Bedrock network protocol version. */
public record RequestNetworkSettingsPacket(int clientNetworkVersion) implements BedrockPlayPacket {
  @Override
  public int packetId() {
    return BedrockPacketIds.REQUEST_NETWORK_SETTINGS;
  }

  @Override
  public PacketDirection direction() {
    return PacketDirection.SERVERBOUND;
  }
}
