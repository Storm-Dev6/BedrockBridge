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
        JavaWirePacket.SetPlayerOnGround {
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

  record PlayDisconnect(String reasonJson) implements JavaWirePacket {}

  record SystemChat(String contentJson, boolean overlay) implements JavaWirePacket {}

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
}
