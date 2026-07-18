package io.bedrockbridge.application.javawire;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JavaWireCodecTest {
  @Test
  void encodesHandshakeWithBigEndianPortAndVarInts() throws Exception {
    byte[] fields =
        JavaWireCodec.encode(
            new JavaWirePacket.Handshake(767, "localhost", 25565, 2), JavaWireState.HANDSHAKING);
    assertArrayEquals(
        new byte[] {
          (byte) 0xFF,
          0x05,
          0x09,
          'l',
          'o',
          'c',
          'a',
          'l',
          'h',
          'o',
          's',
          't',
          0x63,
          (byte) 0xDD,
          0x02
        },
        fields);
  }

  @Test
  void decodesLoginSuccessAndKnownPacks() throws Exception {
    UUID uuid = UUID.randomUUID();
    ByteArrayOutputStream fields = new ByteArrayOutputStream();
    java.io.DataOutputStream data = new java.io.DataOutputStream(fields);
    data.writeLong(uuid.getMostSignificantBits());
    data.writeLong(uuid.getLeastSignificantBits());
    JavaWireCodec.writeVarInt(fields, 4);
    fields.writeBytes("Alex".getBytes(UTF_8));
    JavaWireCodec.writeVarInt(fields, 0);
    fields.write(1);
    JavaWirePacket.LoginSuccess success =
        (JavaWirePacket.LoginSuccess)
            JavaWireCodec.decode(JavaWireState.LOGIN, 2, fields.toByteArray());
    assertEquals(uuid, success.uuid());
    assertEquals("Alex", success.username());
    assertEquals(true, success.strictErrorHandling());

    ByteArrayOutputStream packs = new ByteArrayOutputStream();
    JavaWireCodec.writeVarInt(packs, 1);
    packs.writeBytes(new byte[] {9});
    packs.writeBytes("minecraft".getBytes(UTF_8));
    JavaWireCodec.writeVarInt(packs, 4);
    packs.writeBytes("core".getBytes(UTF_8));
    JavaWireCodec.writeVarInt(packs, 4);
    packs.writeBytes("1.21".getBytes(UTF_8));
    JavaWirePacket.KnownPacks known =
        (JavaWirePacket.KnownPacks)
            JavaWireCodec.decode(JavaWireState.CONFIGURATION, 0x0E, packs.toByteArray());
    assertEquals(List.of(new JavaWirePacket.KnownPack("minecraft", "core", "1.21")), known.packs());
  }

  @Test
  void roundTripsCompressedFrame() throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    JavaWireCodec.writePacket(
        bytes,
        0x02,
        JavaWireCodec.encode(
            new JavaWirePacket.LoginStart("Alex", new UUID(1, 2)), JavaWireState.LOGIN),
        1);
    JavaWireCodec.Frame frame =
        JavaWireCodec.readFrame(new ByteArrayInputStream(bytes.toByteArray()), 1);
    assertEquals(2, frame.packetId());
    assertEquals(
        JavaWireCodec.encode(
                new JavaWirePacket.LoginStart("Alex", new UUID(1, 2)), JavaWireState.LOGIN)
            .length,
        frame.fields().length);
  }

  @Test
  void decodesRegistryFeatureAndTagPacketsAndRejectsUnknownIds() throws Exception {
    ByteArrayOutputStream registry = new ByteArrayOutputStream();
    writeString(registry, "minecraft:dimension_type");
    JavaWireCodec.writeVarInt(registry, 1);
    writeString(registry, "minecraft:overworld");
    registry.write(1);
    registry.write(10);
    registry.write(8);
    registry.write(0);
    registry.write(1);
    registry.write('x');
    registry.write(0);
    registry.write(1);
    registry.write('v');
    registry.write(0);
    JavaWirePacket.RegistryData decoded =
        (JavaWirePacket.RegistryData)
            JavaWireCodec.decode(JavaWireState.CONFIGURATION, 0x07, registry.toByteArray());
    assertEquals("minecraft:dimension_type", decoded.registryId());
    assertEquals(1, decoded.entries().size());
    assertEquals(JavaNbt.CompoundValue.class, decoded.entries().getFirst().data().getClass());

    ByteArrayOutputStream tags = new ByteArrayOutputStream();
    JavaWireCodec.writeVarInt(tags, 1);
    writeString(tags, "minecraft:block");
    JavaWireCodec.writeVarInt(tags, 1);
    writeString(tags, "minecraft:mineable/pickaxe");
    JavaWireCodec.writeVarInt(tags, 2);
    JavaWireCodec.writeVarInt(tags, 3);
    JavaWireCodec.writeVarInt(tags, 7);
    JavaWirePacket.UpdateTags decodedTags =
        (JavaWirePacket.UpdateTags)
            JavaWireCodec.decode(JavaWireState.CONFIGURATION, 0x0D, tags.toByteArray());
    assertEquals(2, decodedTags.registries().getFirst().tags().getFirst().entries().size());
    assertThrows(
        JavaWireException.class,
        () -> JavaWireCodec.decode(JavaWireState.CONFIGURATION, 0x7F, new byte[0]));
  }

  @Test
  void decodesProtocol767PlayVerticalSlicePackets() throws Exception {
    ByteArrayOutputStream keepAlive = new ByteArrayOutputStream();
    new java.io.DataOutputStream(keepAlive).writeLong(42L);
    assertEquals(
        42L,
        ((JavaWirePacket.PlayKeepAlive)
                JavaWireCodec.decode(JavaWireState.PLAY, 0x26, keepAlive.toByteArray()))
            .payload());

    ByteArrayOutputStream position = new ByteArrayOutputStream();
    java.io.DataOutputStream positionData = new java.io.DataOutputStream(position);
    positionData.writeDouble(1.0);
    positionData.writeDouble(2.0);
    positionData.writeDouble(3.0);
    positionData.writeFloat(90.0f);
    positionData.writeFloat(-10.0f);
    positionData.writeByte(0x10);
    JavaWireCodec.writeVarInt(position, 7);
    JavaWirePacket.SynchronizePlayerPosition decodedPosition =
        (JavaWirePacket.SynchronizePlayerPosition)
            JavaWireCodec.decode(JavaWireState.PLAY, 0x40, position.toByteArray());
    assertEquals(7, decodedPosition.teleportId());
    assertEquals(0x10, decodedPosition.flags());

    ByteArrayOutputStream abilities = new ByteArrayOutputStream();
    java.io.DataOutputStream abilityData = new java.io.DataOutputStream(abilities);
    abilityData.writeByte(0x06);
    abilityData.writeFloat(0.05f);
    abilityData.writeFloat(0.1f);
    assertEquals(
        (byte) 0x06,
        ((JavaWirePacket.PlayPlayerAbilities)
                JavaWireCodec.decode(JavaWireState.PLAY, 0x38, abilities.toByteArray()))
            .flags());

    ByteArrayOutputStream encoded = new ByteArrayOutputStream();
    encoded.write(
        JavaWireCodec.encode(new JavaWirePacket.ConfirmTeleportation(7), JavaWireState.PLAY));
    assertArrayEquals(new byte[] {7}, encoded.toByteArray());
    assertThrows(
        JavaWireException.class, () -> JavaWireCodec.decode(JavaWireState.PLAY, 0x2B, new byte[0]));
  }

  @Test
  void decodesPlayLoginAndPopulatesWorldStateFields() throws Exception {
    ByteArrayOutputStream fields = new ByteArrayOutputStream();
    DataOutputStream data = new DataOutputStream(fields);
    data.writeInt(42);
    data.writeBoolean(true);
    JavaWireCodec.writeVarInt(fields, 2);
    writeString(fields, "minecraft:overworld");
    writeString(fields, "minecraft:the_nether");
    JavaWireCodec.writeVarInt(fields, 20);
    JavaWireCodec.writeVarInt(fields, 10);
    JavaWireCodec.writeVarInt(fields, 8);
    data.writeBoolean(false);
    data.writeBoolean(true);
    data.writeBoolean(false);
    JavaWireCodec.writeVarInt(fields, 0);
    writeString(fields, "minecraft:overworld");
    data.writeLong(123L);
    data.writeByte(0);
    data.writeByte(-1);
    data.writeBoolean(false);
    data.writeBoolean(false);
    data.writeBoolean(false);
    JavaWireCodec.writeVarInt(fields, 0);
    data.writeBoolean(true);

    JavaWirePacket.PlayLogin login =
        (JavaWirePacket.PlayLogin)
            JavaWireCodec.decode(JavaWireState.PLAY, 0x2B, fields.toByteArray());
    assertEquals(42, login.entityId());
    assertEquals(true, login.hardcore());
    assertEquals(List.of("minecraft:overworld", "minecraft:the_nether"), login.dimensionNames());
    assertEquals("minecraft:overworld", login.dimensionName());
    assertEquals(123L, login.hashedSeed());
    assertEquals(-1, login.previousGameMode());
    assertEquals(true, login.enforcesSecureChat());

    JavaWorldState world = new JavaWorldState();
    world.apply(login);
    world.apply(new JavaWirePacket.SetChunkCacheCenter(3, -2));
    world.apply(
        new JavaWirePacket.SetDefaultSpawnPosition(
            new JavaWirePacket.BlockPosition(8, 64, 8), 90.0f));
    assertEquals(login, world.login());
    assertEquals(3, world.centerChunkX());
    assertEquals(-2, world.centerChunkZ());
    assertEquals(new JavaWirePacket.BlockPosition(8, 64, 8), world.defaultSpawn());
  }

  @Test
  void decodesAttributesAndEntityEffectsWithProtocolBounds() throws Exception {
    ByteArrayOutputStream attributes = new ByteArrayOutputStream();
    DataOutputStream attributeData = new DataOutputStream(attributes);
    JavaWireCodec.writeVarInt(attributes, 1);
    JavaWireCodec.writeVarInt(attributes, 1);
    JavaWireCodec.writeVarInt(attributes, 16);
    attributeData.writeDouble(20.0);
    JavaWireCodec.writeVarInt(attributes, 1);
    writeString(attributes, "minecraft:test");
    attributeData.writeDouble(0.5);
    attributeData.writeByte(0);
    JavaWirePacket.UpdateAttributes decodedAttributes =
        (JavaWirePacket.UpdateAttributes)
            JavaWireCodec.decode(JavaWireState.PLAY, 0x75, attributes.toByteArray());
    assertEquals(1, decodedAttributes.entityId());
    assertEquals(16, decodedAttributes.attributes().getFirst().id());
    assertEquals(
        "minecraft:test", decodedAttributes.attributes().getFirst().modifiers().getFirst().id());
    assertEquals(0, decodedAttributes.attributes().getFirst().modifiers().getFirst().operation());

    ByteArrayOutputStream effect = new ByteArrayOutputStream();
    JavaWireCodec.writeVarInt(effect, 1);
    JavaWireCodec.writeVarInt(effect, 1);
    JavaWireCodec.writeVarInt(effect, 0);
    JavaWireCodec.writeVarInt(effect, -1);
    effect.write(0x07);
    JavaWirePacket.EntityEffect decodedEffect =
        (JavaWirePacket.EntityEffect)
            JavaWireCodec.decode(JavaWireState.PLAY, 0x76, effect.toByteArray());
    assertEquals(-1, decodedEffect.duration());
    assertEquals(0x07, decodedEffect.flags());

    ByteArrayOutputStream invalid = new ByteArrayOutputStream();
    JavaWireCodec.writeVarInt(invalid, 1);
    JavaWireCodec.writeVarInt(invalid, 1);
    JavaWireCodec.writeVarInt(invalid, 16);
    new DataOutputStream(invalid).writeDouble(Double.NaN);
    assertThrows(
        JavaWireException.class,
        () -> JavaWireCodec.decode(JavaWireState.PLAY, 0x75, invalid.toByteArray()));
  }

  @Test
  void decodesBundleDelimiterAndSpawnEntity() throws Exception {
    assertEquals(
        JavaWirePacket.BundleDelimiter.class,
        JavaWireCodec.decode(JavaWireState.PLAY, 0x00, new byte[0]).getClass());
    ByteArrayOutputStream spawn = new ByteArrayOutputStream();
    DataOutputStream data = new DataOutputStream(spawn);
    JavaWireCodec.writeVarInt(spawn, 7);
    UUID uuid = new UUID(1L, 2L);
    data.writeLong(uuid.getMostSignificantBits());
    data.writeLong(uuid.getLeastSignificantBits());
    JavaWireCodec.writeVarInt(spawn, 1);
    data.writeDouble(1.0);
    data.writeDouble(64.0);
    data.writeDouble(-2.0);
    data.writeByte(10);
    data.writeByte(20);
    data.writeByte(30);
    JavaWireCodec.writeVarInt(spawn, 0);
    data.writeShort(80);
    data.writeShort(0);
    data.writeShort(-80);
    JavaWirePacket.SpawnEntity decoded =
        (JavaWirePacket.SpawnEntity)
            JavaWireCodec.decode(JavaWireState.PLAY, 0x01, spawn.toByteArray());
    assertEquals(uuid, decoded.entityUuid());
    assertEquals(64.0, decoded.y());
    assertEquals((short) -80, decoded.velocityZ());
  }

  @Test
  void boundsOpaqueEntityMetadataAndDecodesTeleportEntity() throws Exception {
    JavaWirePacket.EntityMetadataIgnored metadata =
        (JavaWirePacket.EntityMetadataIgnored)
            JavaWireCodec.decode(JavaWireState.PLAY, 0x58, new byte[] {1, 2, 3});
    assertEquals(3, metadata.payloadBytes());

    ByteArrayOutputStream teleport = new ByteArrayOutputStream();
    DataOutputStream data = new DataOutputStream(teleport);
    JavaWireCodec.writeVarInt(teleport, 7);
    data.writeDouble(1.0);
    data.writeDouble(64.0);
    data.writeDouble(-2.0);
    data.writeByte(20);
    data.writeByte(30);
    data.writeBoolean(true);
    JavaWirePacket.TeleportEntity decoded =
        (JavaWirePacket.TeleportEntity)
            JavaWireCodec.decode(JavaWireState.PLAY, 0x70, teleport.toByteArray());
    assertEquals(7, decoded.entityId());
    assertEquals(64.0, decoded.y());
    assertEquals(true, decoded.onGround());
  }

  @Test
  void decodesBoundedBlockAndEntityLifecycleUpdates() throws Exception {
    ByteArrayOutputStream block = new ByteArrayOutputStream();
    DataOutputStream blockData = new DataOutputStream(block);
    blockData.writeLong(0L);
    JavaWireCodec.writeVarInt(block, 42);
    JavaWirePacket.BlockUpdate decodedBlock =
        (JavaWirePacket.BlockUpdate)
            JavaWireCodec.decode(JavaWireState.PLAY, 0x09, block.toByteArray());
    assertEquals(42, decodedBlock.blockStateId());

    ByteArrayOutputStream section = new ByteArrayOutputStream();
    DataOutputStream sectionData = new DataOutputStream(section);
    sectionData.writeLong(0L);
    JavaWireCodec.writeVarInt(section, 1);
    section.write(0x80);
    section.write(0x01);
    JavaWirePacket.SectionBlocksUpdate decodedSection =
        (JavaWirePacket.SectionBlocksUpdate)
            JavaWireCodec.decode(JavaWireState.PLAY, 0x49, section.toByteArray());
    assertEquals(List.of(128L), decodedSection.blocks());

    ByteArrayOutputStream removed = new ByteArrayOutputStream();
    JavaWireCodec.writeVarInt(removed, 2);
    JavaWireCodec.writeVarInt(removed, 7);
    JavaWireCodec.writeVarInt(removed, 8);
    JavaWirePacket.RemoveEntities decodedRemoved =
        (JavaWirePacket.RemoveEntities)
            JavaWireCodec.decode(JavaWireState.PLAY, 0x42, removed.toByteArray());
    assertEquals(List.of(7, 8), decodedRemoved.entityIds());
  }

  @Test
  void decodesExperienceAndHealthUpdatesWithBounds() throws Exception {
    ByteArrayOutputStream experience = new ByteArrayOutputStream();
    DataOutputStream experienceData = new DataOutputStream(experience);
    experienceData.writeFloat(0.5f);
    JavaWireCodec.writeVarInt(experience, 3);
    JavaWireCodec.writeVarInt(experience, 42);
    JavaWirePacket.SetExperience decodedExperience =
        (JavaWirePacket.SetExperience)
            JavaWireCodec.decode(JavaWireState.PLAY, 0x5C, experience.toByteArray());
    assertEquals(3, decodedExperience.level());
    assertEquals(42, decodedExperience.totalExperience());

    ByteArrayOutputStream health = new ByteArrayOutputStream();
    DataOutputStream healthData = new DataOutputStream(health);
    healthData.writeFloat(20.0f);
    JavaWireCodec.writeVarInt(health, 20);
    healthData.writeFloat(5.0f);
    JavaWirePacket.SetHealth decodedHealth =
        (JavaWirePacket.SetHealth)
            JavaWireCodec.decode(JavaWireState.PLAY, 0x5D, health.toByteArray());
    assertEquals(20, decodedHealth.food());
    assertEquals(5.0f, decodedHealth.saturation());
  }

  @Test
  void decodesRecipeBookCommandsAndOpaqueRecipesBoundedly() throws Exception {
    ByteArrayOutputStream recipeBook = new ByteArrayOutputStream();
    JavaWireCodec.writeVarInt(recipeBook, 0);
    recipeBook.write(new byte[] {1, 0, 0, 0, 0, 0, 0, 0});
    JavaWireCodec.writeVarInt(recipeBook, 1);
    writeString(recipeBook, "minecraft:crafting");
    JavaWireCodec.writeVarInt(recipeBook, 1);
    writeString(recipeBook, "minecraft:smelting");
    JavaWirePacket.UpdateRecipeBook decodedBook =
        (JavaWirePacket.UpdateRecipeBook)
            JavaWireCodec.decode(JavaWireState.PLAY, 0x41, recipeBook.toByteArray());
    assertEquals(1, decodedBook.displayedRecipes().size());
    assertEquals(1, decodedBook.addedRecipes().size());

    ByteArrayOutputStream commands = new ByteArrayOutputStream();
    JavaWireCodec.writeVarInt(commands, 1);
    commands.write(0);
    JavaWireCodec.writeVarInt(commands, 0);
    JavaWireCodec.writeVarInt(commands, 0);
    JavaWirePacket.Commands decodedCommands =
        (JavaWirePacket.Commands)
            JavaWireCodec.decode(JavaWireState.PLAY, 0x11, commands.toByteArray());
    assertEquals(0, decodedCommands.rootIndex());
    assertEquals(1, decodedCommands.nodes().size());

    JavaWirePacket.UpdateRecipesIgnored ignored =
        (JavaWirePacket.UpdateRecipesIgnored)
            JavaWireCodec.decode(JavaWireState.PLAY, 0x77, new byte[] {1, 2, 3});
    assertEquals(3, ignored.payloadBytes());
  }

  @Test
  void decodesMinimalChunkSectionsAndLightMasks() throws Exception {
    ByteArrayOutputStream sectionBytes = new ByteArrayOutputStream();
    DataOutputStream sectionData = new DataOutputStream(sectionBytes);
    sectionData.writeShort(0);
    sectionData.writeByte(0);
    JavaWireCodec.writeVarInt(sectionBytes, 0);
    JavaWireCodec.writeVarInt(sectionBytes, 0);
    sectionData.writeByte(0);
    JavaWireCodec.writeVarInt(sectionBytes, 0);
    JavaWireCodec.writeVarInt(sectionBytes, 0);

    ByteArrayOutputStream chunk = new ByteArrayOutputStream();
    DataOutputStream chunkData = new DataOutputStream(chunk);
    chunkData.writeInt(4);
    chunkData.writeInt(-2);
    chunkData.writeByte(0);
    JavaWireCodec.writeVarInt(chunk, sectionBytes.size());
    chunk.write(sectionBytes.toByteArray());
    JavaWireCodec.writeVarInt(chunk, 0);
    for (int mask = 0; mask < 4; mask++) {
      JavaWireCodec.writeVarInt(chunk, 0);
    }
    JavaWireCodec.writeVarInt(chunk, 0);
    JavaWireCodec.writeVarInt(chunk, 0);
    JavaWirePacket.ChunkData decodedChunk =
        (JavaWirePacket.ChunkData)
            JavaWireCodec.decode(JavaWireState.PLAY, 0x27, chunk.toByteArray());
    assertEquals(4, decodedChunk.chunkX());
    assertEquals(-2, decodedChunk.chunkZ());
    assertEquals(1, decodedChunk.sections().size());

    ByteArrayOutputStream light = new ByteArrayOutputStream();
    JavaWireCodec.writeVarInt(light, 4);
    JavaWireCodec.writeVarInt(light, -2);
    for (int mask = 0; mask < 4; mask++) {
      JavaWireCodec.writeVarInt(light, 0);
    }
    JavaWireCodec.writeVarInt(light, 0);
    JavaWireCodec.writeVarInt(light, 0);
    JavaWirePacket.LightUpdate decodedLight =
        (JavaWirePacket.LightUpdate)
            JavaWireCodec.decode(JavaWireState.PLAY, 0x2A, light.toByteArray());
    assertEquals(4, decodedLight.chunkX());
    assertEquals(0, decodedLight.light().skyArrays().size());

    JavaWorldState world = new JavaWorldState();
    world.apply(decodedChunk);
    world.apply(decodedLight);
    assertEquals(1, world.chunks().size());
    assertEquals(1, world.lightUpdates().size());
  }

  private static void writeString(ByteArrayOutputStream output, String value) throws Exception {
    byte[] bytes = value.getBytes(UTF_8);
    JavaWireCodec.writeVarInt(output, bytes.length);
    output.write(bytes);
  }
}
