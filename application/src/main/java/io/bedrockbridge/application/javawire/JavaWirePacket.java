package io.bedrockbridge.application.javawire;

import java.util.UUID;

/** Typed packets used by the small, offline-compatible Java vertical slice. */
public sealed interface JavaWirePacket
    permits JavaWirePacket.Handshake,
        JavaWirePacket.StatusRequest,
        JavaWirePacket.StatusResponse,
        JavaWirePacket.Ping,
        JavaWirePacket.Pong,
        JavaWirePacket.LoginStart,
        JavaWirePacket.LoginSuccess,
        JavaWirePacket.SetCompression,
        JavaWirePacket.LoginAcknowledged,
        JavaWirePacket.Disconnect,
        JavaWirePacket.KeepAlive,
        JavaWirePacket.KnownPacks,
        JavaWirePacket.ClientInformation,
        JavaWirePacket.RegistryData,
        JavaWirePacket.FeatureFlags,
        JavaWirePacket.UpdateTags,
        JavaWirePacket.ConfigurationPluginMessage,
        JavaWirePacket.FinishConfiguration,
        JavaWirePacket.AcknowledgeFinishConfiguration,
        JavaWirePacket.PlayKeepAlive,
        JavaWirePacket.PlayDisconnect,
        JavaWirePacket.SystemChat,
        JavaWirePacket.SynchronizePlayerPosition,
        JavaWirePacket.GameEvent,
        JavaWirePacket.PlayPlayerAbilities,
        JavaWirePacket.ChunkBatchStart,
        JavaWirePacket.ChunkBatchFinished,
        JavaWirePacket.ConfirmTeleportation,
        JavaWirePacket.ChunkBatchReceived,
        JavaWirePacket.SetPlayerPosition,
        JavaWirePacket.SetPlayerPositionRotation,
        JavaWirePacket.SetPlayerRotation,
        JavaWirePacket.SetPlayerOnGround,
        JavaWirePacket.PlayLogin,
        JavaWirePacket.SetChunkCacheCenter,
        JavaWirePacket.SetDefaultSpawnPosition,
        JavaWirePacket.ChangeDifficulty,
        JavaWirePacket.EntityEvent,
        JavaWirePacket.SetCarriedItem,
        JavaWirePacket.ServerData {
  record Handshake(int protocolVersion, String host, int port, int nextState)
      implements JavaWirePacket {}

  record StatusRequest() implements JavaWirePacket {}

  record StatusResponse(String json) implements JavaWirePacket {}

  record Ping(long payload) implements JavaWirePacket {}

  record Pong(long payload) implements JavaWirePacket {}

  record LoginStart(String username, UUID uuid) implements JavaWirePacket {}

  record LoginSuccess(UUID uuid, String username, boolean strictErrorHandling)
      implements JavaWirePacket {}

  record SetCompression(int threshold) implements JavaWirePacket {}

  record LoginAcknowledged() implements JavaWirePacket {}

  record Disconnect(String reasonJson) implements JavaWirePacket {}

  record KeepAlive(long payload) implements JavaWirePacket {}

  record KnownPacks(java.util.List<KnownPack> packs) implements JavaWirePacket {
    public KnownPacks {
      packs = java.util.List.copyOf(packs);
    }
  }

  record KnownPack(String namespace, String id, String version) {}

  record ClientInformation(
      String locale,
      int viewDistance,
      int chatMode,
      boolean chatColors,
      int displayedSkinParts,
      int mainHand,
      boolean textFiltering,
      boolean serverListings)
      implements JavaWirePacket {}

  record RegistryData(String registryId, java.util.List<RegistryEntry> entries)
      implements JavaWirePacket {
    public RegistryData {
      entries = java.util.List.copyOf(entries);
    }
  }

  record RegistryEntry(String entryId, JavaNbt data) {}

  record FeatureFlags(java.util.List<String> flags) implements JavaWirePacket {
    public FeatureFlags {
      flags = java.util.List.copyOf(flags);
    }
  }

  record UpdateTags(java.util.List<RegistryTags> registries) implements JavaWirePacket {
    public UpdateTags {
      registries = java.util.List.copyOf(registries);
    }
  }

  record RegistryTags(String registryId, java.util.List<Tag> tags) {
    public RegistryTags {
      tags = java.util.List.copyOf(tags);
    }
  }

  record Tag(String name, java.util.List<Integer> entries) {
    public Tag {
      entries = java.util.List.copyOf(entries);
    }
  }

  record ConfigurationPluginMessage(String channel, int payloadBytes) implements JavaWirePacket {}

  record FinishConfiguration() implements JavaWirePacket {}

  record AcknowledgeFinishConfiguration() implements JavaWirePacket {}

  record PlayKeepAlive(long payload) implements JavaWirePacket {}

  record PlayDisconnect(JavaNbt reason) implements JavaWirePacket {
    public PlayDisconnect(String reasonJson) {
      this(new JavaNbt.StringValue(reasonJson));
    }

    public String reasonJson() {
      return reason.toString();
    }
  }

  record SystemChat(JavaNbt content, boolean overlay) implements JavaWirePacket {
    public SystemChat(String contentJson, boolean overlay) {
      this(new JavaNbt.StringValue(contentJson), overlay);
    }

    public String contentJson() {
      return content.toString();
    }
  }

  record SynchronizePlayerPosition(
      double x, double y, double z, float yaw, float pitch, int flags, int teleportId)
      implements JavaWirePacket {}

  record GameEvent(int event, float value) implements JavaWirePacket {}

  record PlayPlayerAbilities(byte flags, float flyingSpeed, float fieldOfViewModifier)
      implements JavaWirePacket {}

  record ChunkBatchStart() implements JavaWirePacket {}

  record ChunkBatchFinished(int batchSize) implements JavaWirePacket {}

  record ConfirmTeleportation(int teleportId) implements JavaWirePacket {}

  record ChunkBatchReceived(float chunksPerTick) implements JavaWirePacket {}

  record SetPlayerPosition(double x, double feetY, double z, boolean onGround)
      implements JavaWirePacket {}

  record SetPlayerPositionRotation(
      double x, double feetY, double z, float yaw, float pitch, boolean onGround)
      implements JavaWirePacket {}

  record SetPlayerRotation(float yaw, float pitch, boolean onGround) implements JavaWirePacket {}

  record SetPlayerOnGround(boolean onGround) implements JavaWirePacket {}

  record BlockPosition(int x, int y, int z) {}

  record PlayLogin(
      int entityId,
      boolean hardcore,
      java.util.List<String> dimensionNames,
      int maxPlayers,
      int viewDistance,
      int simulationDistance,
      boolean reducedDebugInfo,
      boolean enableRespawnScreen,
      boolean doLimitedCrafting,
      int dimensionType,
      String dimensionName,
      long hashedSeed,
      int gameMode,
      int previousGameMode,
      boolean debug,
      boolean flat,
      String deathDimensionName,
      BlockPosition deathLocation,
      int portalCooldown,
      boolean enforcesSecureChat)
      implements JavaWirePacket {
    public PlayLogin {
      dimensionNames = java.util.List.copyOf(dimensionNames);
    }
  }

  record SetChunkCacheCenter(int chunkX, int chunkZ) implements JavaWirePacket {}

  record SetDefaultSpawnPosition(BlockPosition location, float angle) implements JavaWirePacket {}

  record ChangeDifficulty(int difficulty, boolean locked) implements JavaWirePacket {}

  record EntityEvent(int entityId, byte status) implements JavaWirePacket {}

  record SetCarriedItem(int slot) implements JavaWirePacket {}

  record ServerData(JavaNbt motd, int iconBytes) implements JavaWirePacket {}
}
