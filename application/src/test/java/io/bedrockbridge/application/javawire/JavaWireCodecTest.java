package io.bedrockbridge.application.javawire;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
}
