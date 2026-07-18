package io.bedrockbridge.bedrock.packet.play;

import io.bedrockbridge.bedrock.BedrockPacketIds;
import io.bedrockbridge.protocol.PacketDirection;
import java.util.List;
import java.util.Objects;

/** Client resource-pack state and currently downloading pack identifiers. */
public record ResourcePackClientResponsePacket(
    ResourcePackResponse response, List<String> downloadingPacks) implements BedrockPlayPacket {
  /** Validates and copies response data. */
  public ResourcePackClientResponsePacket {
    Objects.requireNonNull(response, "response");
    downloadingPacks = List.copyOf(downloadingPacks);
  }

  @Override
  public int packetId() {
    return BedrockPacketIds.RESOURCE_PACK_CLIENT_RESPONSE;
  }

  @Override
  public PacketDirection direction() {
    return PacketDirection.SERVERBOUND;
  }
}
