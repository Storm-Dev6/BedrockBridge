package io.bedrockbridge.bedrock.packet.play;

import io.bedrockbridge.bedrock.BedrockPacketIds;
import io.bedrockbridge.protocol.PacketDirection;
import java.util.List;
import java.util.Objects;

/** Server-selected behavior/resource pack stack and experiment state. */
public record ResourcePackStackPacket(
    boolean texturePackRequired,
    List<ResourcePackStackEntry> addonPacks,
    List<ResourcePackStackEntry> texturePacks,
    String baseGameVersion,
    List<BedrockExperiment> experiments,
    boolean experimentsEverToggled,
    boolean includeEditorPacks)
    implements BedrockPlayPacket {
  /** Defensively copies collections and validates the base game version text. */
  public ResourcePackStackPacket {
    addonPacks = List.copyOf(addonPacks);
    texturePacks = List.copyOf(texturePacks);
    Objects.requireNonNull(baseGameVersion, "baseGameVersion");
    experiments = List.copyOf(experiments);
  }

  @Override
  public int packetId() {
    return BedrockPacketIds.RESOURCE_PACK_STACK;
  }

  @Override
  public PacketDirection direction() {
    return PacketDirection.CLIENTBOUND;
  }
}
