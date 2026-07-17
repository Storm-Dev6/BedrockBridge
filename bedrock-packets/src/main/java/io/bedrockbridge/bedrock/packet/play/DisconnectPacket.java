package io.bedrockbridge.bedrock.packet.play;

import io.bedrockbridge.bedrock.BedrockPacketIds;
import io.bedrockbridge.protocol.PacketDirection;
import java.util.Objects;

/** Protocol disconnect reason with conditionally present user-facing messages. */
public record DisconnectPacket(
    int reason, boolean skipMessage, String message, String filteredMessage)
    implements BedrockPlayPacket {
  /** Validates conditional message presence without constraining Mojang's evolving reason enum. */
  public DisconnectPacket {
    Objects.requireNonNull(message, "message");
    Objects.requireNonNull(filteredMessage, "filteredMessage");
    if (reason < 0 || (skipMessage && (!message.isEmpty() || !filteredMessage.isEmpty()))) {
      throw new IllegalArgumentException("Disconnect fields are invalid");
    }
  }

  /** Creates a message-free disconnect. */
  public static DisconnectPacket silent(int reason) {
    return new DisconnectPacket(reason, true, "", "");
  }

  @Override
  public int packetId() {
    return BedrockPacketIds.DISCONNECT;
  }

  @Override
  public PacketDirection direction() {
    return PacketDirection.CLIENTBOUND;
  }
}
