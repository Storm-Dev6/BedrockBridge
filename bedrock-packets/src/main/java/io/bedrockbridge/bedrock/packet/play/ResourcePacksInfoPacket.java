package io.bedrockbridge.bedrock.packet.play;

import io.bedrockbridge.bedrock.BedrockPacketIds;
import io.bedrockbridge.protocol.PacketDirection;
import java.util.List;

/** Server resource-pack catalog sent after successful authentication. */
public record ResourcePacksInfoPacket(
    boolean resourcePackRequired,
    boolean hasAddonPacks,
    boolean hasScripts,
    List<ResourcePackInfo> resourcePacks)
    implements BedrockPlayPacket {
  /** Defensively copies the resource-pack catalog. */
  public ResourcePacksInfoPacket {
    resourcePacks = List.copyOf(resourcePacks);
  }

  @Override
  public int packetId() {
    return BedrockPacketIds.RESOURCE_PACKS_INFO;
  }

  @Override
  public PacketDirection direction() {
    return PacketDirection.CLIENTBOUND;
  }
}
