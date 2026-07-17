package io.bedrockbridge.bedrock.packet;

import io.bedrockbridge.bedrock.BedrockPacketIds;
import io.bedrockbridge.protocol.PacketDirection;
import io.bedrockbridge.protocol.PacketReader;
import io.bedrockbridge.protocol.PacketWriter;

/** Connected peer notification that the transport should close immediately. */
public final class DisconnectNotification extends AbstractBedrockHandshakePacket {
  /** Creates a serverbound disconnect notification. */
  public DisconnectNotification() {
    super(BedrockPacketIds.DISCONNECT_NOTIFICATION, PacketDirection.SERVERBOUND);
  }

  @Override
  public void encode(PacketWriter writer) {}

  @Override
  public void decode(PacketReader reader) {}
}
