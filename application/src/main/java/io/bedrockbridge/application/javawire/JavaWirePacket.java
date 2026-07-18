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
        JavaWirePacket.BundleDelimiter,
        JavaWirePacket.SpawnEntity,
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
        JavaWirePacket.ServerData,
        JavaWirePacket.UpdateRecipeBook,
        JavaWirePacket.UpdateRecipesIgnored,
        JavaWirePacket.Commands,
        JavaWirePacket.ChunkData,
        JavaWirePacket.LightUpdate,
        JavaWirePacket.SetChunkCacheRadius,
        JavaWirePacket.SetSimulationDistance,
        JavaWirePacket.SetTime,
        JavaWirePacket.UpdateAttributes,
        JavaWirePacket.EntityEffect {
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

  record BundleDelimiter() implements JavaWirePacket {}

  record SpawnEntity(
      int entityId,
      UUID entityUuid,
      int type,
      double x,
      double y,
      double z,
      int pitch,
      int yaw,
      int headYaw,
      int data,
      short velocityX,
      short velocityY,
      short velocityZ)
      implements JavaWirePacket {}

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

  record UpdateRecipeBook(
      int action,
      boolean craftingOpen,
      boolean craftingFilter,
      boolean smeltingOpen,
      boolean smeltingFilter,
      boolean blastFurnaceOpen,
      boolean blastFurnaceFilter,
      boolean smokerOpen,
      boolean smokerFilter,
      java.util.List<String> displayedRecipes,
      java.util.List<String> addedRecipes)
      implements JavaWirePacket {
    public UpdateRecipeBook {
      displayedRecipes = java.util.List.copyOf(displayedRecipes);
      addedRecipes = java.util.List.copyOf(addedRecipes);
    }
  }

  /** Bounded opaque handling for recipe definitions that are not needed for spawning. */
  record UpdateRecipesIgnored(int payloadBytes) implements JavaWirePacket {}

  record Commands(int rootIndex, java.util.List<CommandNode> nodes) implements JavaWirePacket {
    public Commands {
      nodes = java.util.List.copyOf(nodes);
    }
  }

  record CommandNode(
      int flags,
      java.util.List<Integer> children,
      Integer redirect,
      String name,
      Integer parserId,
      String parserProperties,
      String suggestionsType) {
    public CommandNode {
      children = java.util.List.copyOf(children);
    }
  }

  record ChunkData(
      int chunkX,
      int chunkZ,
      JavaNbt heightmaps,
      java.util.List<ChunkSection> sections,
      java.util.List<BlockEntity> blockEntities,
      LightData light)
      implements JavaWirePacket {
    public ChunkData {
      sections = java.util.List.copyOf(sections);
      blockEntities = java.util.List.copyOf(blockEntities);
    }
  }

  record ChunkSection(short blockCount, PaletteReference blockStates, PaletteReference biomes) {}

  record PaletteReference(
      int bitsPerEntry, java.util.List<Integer> paletteIds, java.util.List<Long> data) {
    public PaletteReference {
      paletteIds = java.util.List.copyOf(paletteIds);
      data = java.util.List.copyOf(data);
    }
  }

  record BlockEntity(int packedXz, short y, int type, JavaNbt data) {}

  record LightUpdate(int chunkX, int chunkZ, LightData light) implements JavaWirePacket {}

  record SetChunkCacheRadius(int viewDistance) implements JavaWirePacket {}

  record SetSimulationDistance(int simulationDistance) implements JavaWirePacket {}

  record SetTime(long worldAge, long timeOfDay) implements JavaWirePacket {}

  record UpdateAttributes(int entityId, java.util.List<Attribute> attributes)
      implements JavaWirePacket {
    public UpdateAttributes {
      attributes = java.util.List.copyOf(attributes);
    }
  }

  record Attribute(int id, double value, java.util.List<AttributeModifier> modifiers) {
    public Attribute {
      modifiers = java.util.List.copyOf(modifiers);
    }
  }

  record AttributeModifier(String id, double amount, int operation) {}

  record EntityEffect(int entityId, int effectId, int amplifier, int duration, int flags)
      implements JavaWirePacket {}

  record LightData(
      java.util.List<Long> skyMask,
      java.util.List<Long> blockMask,
      java.util.List<Long> emptySkyMask,
      java.util.List<Long> emptyBlockMask,
      java.util.List<byte[]> skyArrays,
      java.util.List<byte[]> blockArrays) {
    public LightData {
      skyMask = java.util.List.copyOf(skyMask);
      blockMask = java.util.List.copyOf(blockMask);
      emptySkyMask = java.util.List.copyOf(emptySkyMask);
      emptyBlockMask = java.util.List.copyOf(emptyBlockMask);
      skyArrays = copyArrays(skyArrays);
      blockArrays = copyArrays(blockArrays);
    }

    @Override
    public java.util.List<byte[]> skyArrays() {
      return copyArrays(skyArrays);
    }

    @Override
    public java.util.List<byte[]> blockArrays() {
      return copyArrays(blockArrays);
    }

    private static java.util.List<byte[]> copyArrays(java.util.List<byte[]> arrays) {
      return arrays.stream().map(byte[]::clone).toList();
    }
  }
}
