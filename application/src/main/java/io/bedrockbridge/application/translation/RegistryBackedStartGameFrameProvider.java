package io.bedrockbridge.application.translation;

import io.bedrockbridge.application.javawire.JavaWirePacket;
import io.bedrockbridge.application.javawire.JavaWorldState;
import io.bedrockbridge.bedrock.BedrockProtocolLimits;
import io.bedrockbridge.bedrock.auth.BedrockIdentity;
import io.bedrockbridge.bedrock.codec.BedrockBinaryWriter;
import io.bedrockbridge.bedrock.codec.BedrockPacketFrame;
import io.bedrockbridge.bedrock.codec.BedrockPacketFrameCodec;
import io.bedrockbridge.bedrock.codec.BedrockPacketHeader;
import io.bedrockbridge.registry.generator.ExternalItemRegistry;
import io.bedrockbridge.registry.generator.ObservedItem;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Builds the bounded protocol-748 StartGame frame from a validated external item registry.
 *
 * <p>The field order and primitive encodings follow Mojang's r/21_u4 StartGamePacket, ItemData,
 * LevelSettings, and related type definitions. No item, block, texture, or pack data is inferred.
 */
public final class RegistryBackedStartGameFrameProvider
    implements BedrockJavaSession.StartGameFrameProvider {
  private static final int START_GAME_PACKET_ID = 11;
  private final ExternalItemRegistry registry;
  private final BedrockProtocolLimits limits;
  private final String serverVersion;
  private final BedrockPacketFrameCodec frames;

  public RegistryBackedStartGameFrameProvider(
      ExternalItemRegistry registry, BedrockProtocolLimits limits, String serverVersion) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.limits = Objects.requireNonNull(limits, "limits");
    this.serverVersion = Objects.requireNonNull(serverVersion, "serverVersion");
    if (!registry.protocolVersion().equals("748")) {
      throw new IllegalArgumentException("StartGame provider requires protocol 748 registry");
    }
    if (registry.items().size() > limits.maximumRegistryEntries()) {
      throw new IllegalArgumentException("External item registry exceeds configured limit");
    }
    frames = new BedrockPacketFrameCodec(limits);
  }

  @Override
  public byte[] build(BedrockIdentity identity, JavaWorldState worldState) throws IOException {
    Objects.requireNonNull(identity, "identity");
    JavaWirePacket.PlayLogin login = Objects.requireNonNull(worldState, "worldState").login();
    if (login == null) {
      throw new IOException("Java world state has no Play Login packet");
    }
    BedrockBinaryWriter payload = new BedrockBinaryWriter(limits.maximumPacketBytes());
    writeStartGamePayload(payload, identity, login, worldState);
    return frames.encode(
        new BedrockPacketFrame(
            new BedrockPacketHeader(START_GAME_PACKET_ID, 0, 0), payload.toByteArray()));
  }

  private void writeStartGamePayload(
      BedrockBinaryWriter out,
      BedrockIdentity identity,
      JavaWirePacket.PlayLogin login,
      JavaWorldState worldState) {
    out.writeVarLong(login.entityId());
    out.writeUnsignedVarLong(Integer.toUnsignedLong(login.entityId()));
    out.writeVarInt(login.gameMode());
    out.writeFloatLE(0.0f);
    out.writeFloatLE(64.0f);
    out.writeFloatLE(0.0f);
    out.writeFloatLE(0.0f);
    out.writeFloatLE(0.0f);
    writeLevelSettings(out, login, worldState);
    out.writeLongLE(worldState.worldAge());
    out.writeVarInt(0);
    out.writeUnsignedVarInt(0); // block properties
    out.writeUnsignedVarInt(registry.items().size());
    for (ObservedItem item : registry.items()) {
      out.writeString(item.itemName(), limits.maximumStringBytes());
      out.writeShortLE(item.itemId());
      out.writeBoolean(item.componentBased());
    }
    UUID correlation =
        UUID.nameUUIDFromBytes(
            ("BedrockBridge:" + identity.identity()).getBytes(StandardCharsets.UTF_8));
    out.writeLongLE(correlation.getMostSignificantBits());
    out.writeLongLE(correlation.getLeastSignificantBits());
    out.writeBoolean(false);
    out.writeString(serverVersion, limits.maximumStringBytes());
    writeEmptyCompound(out);
    out.writeLongLE(0L);
    out.writeBoolean(false);
    out.writeBoolean(false);
    out.writeBoolean(false);
  }

  private void writeLevelSettings(
      BedrockBinaryWriter out, JavaWirePacket.PlayLogin login, JavaWorldState worldState) {
    out.writeLongLE(login.hashedSeed());
    out.writeShortLE(0);
    out.writeString("", limits.maximumStringBytes());
    out.writeVarInt(0);
    out.writeVarInt(login.gameMode());
    out.writeBoolean(login.hardcore());
    out.writeVarInt(0);
    JavaWirePacket.BlockPosition spawn = worldState.defaultSpawn();
    int spawnX = spawn == null ? 0 : spawn.x();
    int spawnY = spawn == null ? 64 : spawn.y();
    int spawnZ = spawn == null ? 0 : spawn.z();
    out.writeVarInt(spawnX);
    out.writeUnsignedVarInt(spawnY);
    out.writeVarInt(spawnZ);
    out.writeBoolean(true);
    out.writeVarInt(0);
    out.writeBoolean(false);
    out.writeBoolean(false);
    out.writeVarInt(-1);
    out.writeVarInt(0);
    out.writeBoolean(false);
    out.writeString("", limits.maximumStringBytes());
    out.writeFloatLE(0.0f);
    out.writeFloatLE(0.0f);
    out.writeBoolean(false);
    out.writeBoolean(true);
    out.writeBoolean(true);
    out.writeVarInt(0);
    out.writeVarInt(0);
    out.writeBoolean(true);
    out.writeBoolean(false);
    out.writeUnsignedVarInt(0); // game rules
    out.writeUnsignedIntLE(0); // experiments array
    out.writeBoolean(false); // were experiments ever toggled
    out.writeBoolean(false);
    out.writeBoolean(false);
    out.writeVarInt(1);
    out.writeIntLE(4);
    out.writeBoolean(false);
    out.writeBoolean(false);
    out.writeBoolean(false);
    out.writeBoolean(false);
    out.writeBoolean(false);
    out.writeBoolean(false);
    out.writeBoolean(false);
    out.writeString("1.21.40", limits.maximumStringBytes());
    out.writeIntLE(0);
    out.writeIntLE(0);
    out.writeBoolean(false);
    out.writeString("", limits.maximumStringBytes());
    out.writeString("", limits.maximumStringBytes());
    out.writeBoolean(false);
    out.writeByte(0);
    out.writeBoolean(false);
    out.writeString("BedrockBridge", limits.maximumStringBytes());
    out.writeString("", limits.maximumStringBytes());
    out.writeString("", limits.maximumStringBytes());
    out.writeVarInt(0);
    out.writeVarInt(0);
    out.writeBoolean(false);
  }

  private static void writeEmptyCompound(BedrockBinaryWriter out) {
    out.writeByte(0);
  }
}
