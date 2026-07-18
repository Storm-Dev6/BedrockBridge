package io.bedrockbridge.bedrock.packet.play;

import io.bedrockbridge.bedrock.BedrockPacketIds;
import io.bedrockbridge.protocol.PacketDirection;
import java.util.Objects;

/** Server login or spawn status encoded as a big-endian integer. */
public record PlayStatusPacket(PlayStatus status) implements BedrockPlayPacket {
  /** Validates the status value. */
  public PlayStatusPacket {
    Objects.requireNonNull(status, "status");
  }

  @Override
  public int packetId() {
    return BedrockPacketIds.PLAY_STATUS;
  }

  @Override
  public PacketDirection direction() {
    return PacketDirection.CLIENTBOUND;
  }
}
