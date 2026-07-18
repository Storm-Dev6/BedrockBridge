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

  private static void writeString(ByteArrayOutputStream output, String value) throws Exception {
    byte[] bytes = value.getBytes(UTF_8);
    JavaWireCodec.writeVarInt(output, bytes.length);
    output.write(bytes);
  }
}
