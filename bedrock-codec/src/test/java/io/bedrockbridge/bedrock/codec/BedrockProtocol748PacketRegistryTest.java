package io.bedrockbridge.bedrock.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.bedrockbridge.bedrock.BedrockPlayState;
import io.bedrockbridge.bedrock.BedrockProtocol;
import io.bedrockbridge.bedrock.BedrockProtocolLimits;
import io.bedrockbridge.bedrock.BedrockValidationException;
import io.bedrockbridge.bedrock.packet.play.BedrockExperiment;
import io.bedrockbridge.bedrock.packet.play.BedrockPlayPacket;
import io.bedrockbridge.bedrock.packet.play.ClientToServerHandshakePacket;
import io.bedrockbridge.bedrock.packet.play.DisconnectPacket;
import io.bedrockbridge.bedrock.packet.play.LoginPacket;
import io.bedrockbridge.bedrock.packet.play.NetworkCompressionAlgorithm;
import io.bedrockbridge.bedrock.packet.play.NetworkSettingsPacket;
import io.bedrockbridge.bedrock.packet.play.PlayStatus;
import io.bedrockbridge.bedrock.packet.play.PlayStatusPacket;
import io.bedrockbridge.bedrock.packet.play.RequestNetworkSettingsPacket;
import io.bedrockbridge.bedrock.packet.play.ResourcePackClientResponsePacket;
import io.bedrockbridge.bedrock.packet.play.ResourcePackInfo;
import io.bedrockbridge.bedrock.packet.play.ResourcePackResponse;
import io.bedrockbridge.bedrock.packet.play.ResourcePackStackEntry;
import io.bedrockbridge.bedrock.packet.play.ResourcePackStackPacket;
import io.bedrockbridge.bedrock.packet.play.ResourcePacksInfoPacket;
import io.bedrockbridge.bedrock.packet.play.ServerToClientHandshakePacket;
import io.bedrockbridge.protocol.PacketDirection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class BedrockProtocol748PacketRegistryTest {
  private static final BedrockProtocolLimits LIMITS =
      new BedrockProtocolLimits(2048, 1024, 16, 2048, 16, 256, 128, 4, 64, 32);
  private final BedrockPlayCodec codec =
      new BedrockPlayCodec(
          BedrockProtocol.PLAY_VERSION_748,
          LIMITS,
          BedrockProtocol748PacketRegistry.create(LIMITS));

  @Test
  void corePacketVectorsMatchProtocol748FieldDefinitions() {
    assertArrayEquals(
        bytes(0xC1, 0x01, 0x00, 0x00, 0x02, 0xEC),
        encode(new RequestNetworkSettingsPacket(748), BedrockPlayState.NETWORK_SETTINGS));
    assertArrayEquals(
        bytes(0x8F, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0, 0, 0, 0),
        encode(
            new NetworkSettingsPacket(256, NetworkCompressionAlgorithm.ZLIB, false, 0, 0.0F),
            BedrockPlayState.NETWORK_SETTINGS));
    assertArrayEquals(
        bytes(0x01, 0x00, 0x00, 0x02, 0xEC, 0x03, 0xAA, 0xBB, 0xCC),
        encode(new LoginPacket(748, bytes(0xAA, 0xBB, 0xCC)), BedrockPlayState.LOGIN));
    assertArrayEquals(
        bytes(0x02, 0, 0, 0, 0),
        encode(new PlayStatusPacket(PlayStatus.LOGIN_SUCCESS), BedrockPlayState.LOGIN));
    assertArrayEquals(
        concat(bytes(0x03, 3), "jwt".getBytes(StandardCharsets.UTF_8)),
        encode(new ServerToClientHandshakePacket("jwt"), BedrockPlayState.AUTHENTICATING));
    assertArrayEquals(
        bytes(0x04), encode(new ClientToServerHandshakePacket(), BedrockPlayState.AUTHENTICATING));
    assertArrayEquals(
        bytes(0x05, 0x52, 0x01), encode(DisconnectPacket.silent(41), BedrockPlayState.LOGIN));
    assertArrayEquals(
        bytes(0x06, 0, 0, 0, 0, 0),
        encode(
            new ResourcePacksInfoPacket(false, false, false, List.of()),
            BedrockPlayState.RESOURCE_PACKS));
    assertArrayEquals(
        concat(
            bytes(0x07, 0, 0, 0, 7),
            "1.21.40".getBytes(StandardCharsets.UTF_8),
            bytes(0, 0, 0, 0, 0, 0)),
        encode(emptyStack(), BedrockPlayState.RESOURCE_PACKS));
    assertArrayEquals(
        bytes(0x08, 0x04, 0, 0),
        encode(
            new ResourcePackClientResponsePacket(
                ResourcePackResponse.RESOURCE_PACK_STACK_FINISHED, List.of()),
            BedrockPlayState.RESOURCE_PACKS));
  }

  @Test
  void roundTripsEveryCatalogPacketAndNonemptyCollections() {
    assertRoundTrip(new RequestNetworkSettingsPacket(748), BedrockPlayState.NETWORK_SETTINGS);
    assertRoundTrip(
        new NetworkSettingsPacket(1, NetworkCompressionAlgorithm.NONE, true, 7, 0.25F),
        BedrockPlayState.NETWORK_SETTINGS);
    assertRoundTrip(new LoginPacket(748, bytes(1, 2, 3)), BedrockPlayState.LOGIN);
    assertRoundTrip(
        new ServerToClientHandshakePacket("header.payload.signature"),
        BedrockPlayState.AUTHENTICATING);
    assertRoundTrip(new ClientToServerHandshakePacket(), BedrockPlayState.AUTHENTICATING);
    assertRoundTrip(new PlayStatusPacket(PlayStatus.PLAYER_SPAWN), BedrockPlayState.PLAY_READY);
    assertRoundTrip(new DisconnectPacket(41, false, "message", "filtered"), BedrockPlayState.LOGIN);
    assertRoundTrip(
        new ResourcePacksInfoPacket(
            true,
            true,
            true,
            List.of(
                new ResourcePackInfo(
                    "id", "1.0.0", 123, "key", "sub", "identity", true, true, false, "url"))),
        BedrockPlayState.RESOURCE_PACKS);
    assertRoundTrip(
        new ResourcePackStackPacket(
            true,
            List.of(new ResourcePackStackEntry("addon", "1", "sub")),
            List.of(new ResourcePackStackEntry("texture", "2", "")),
            "1.21.40",
            List.of(new BedrockExperiment("toggle", true, "always", false)),
            true,
            false),
        BedrockPlayState.RESOURCE_PACKS);
    assertRoundTrip(
        new ResourcePackClientResponsePacket(ResourcePackResponse.DOWNLOADING, List.of("pack-1")),
        BedrockPlayState.RESOURCE_PACKS);
  }

  @Test
  void rejectsOversizeLoginCollectionsAndUnknownEnums() {
    var tinyLimits = new BedrockProtocolLimits(128, 64, 4, 128, 4, 2, 16, 1, 8, 16);
    var tinyCodec =
        new BedrockPlayCodec(
            BedrockProtocol.PLAY_VERSION_748,
            tinyLimits,
            BedrockProtocol748PacketRegistry.create(tinyLimits));
    assertThrows(
        BedrockValidationException.class,
        () ->
            tinyCodec.decode(
                bytes(0x01, 0, 0, 2, 0xEC, 3, 1, 2, 3),
                BedrockPlayState.LOGIN,
                PacketDirection.SERVERBOUND));
    assertThrows(
        BedrockValidationException.class,
        () ->
            tinyCodec.decode(
                bytes(0x08, 0x7F, 0, 0),
                BedrockPlayState.RESOURCE_PACKS,
                PacketDirection.SERVERBOUND));
    assertThrows(
        BedrockValidationException.class,
        () ->
            tinyCodec.decode(
                bytes(0x06, 0, 0, 0, 2, 0),
                BedrockPlayState.RESOURCE_PACKS,
                PacketDirection.CLIENTBOUND));
  }

  private byte[] encode(BedrockPlayPacket packet, BedrockPlayState state) {
    return codec.encode(packet, state, 0, 0);
  }

  private void assertRoundTrip(BedrockPlayPacket packet, BedrockPlayState state) {
    byte[] encoded = encode(packet, state);
    assertEquals(packet, codec.decode(encoded, state, packet.direction()).packet());
  }

  private static ResourcePackStackPacket emptyStack() {
    return new ResourcePackStackPacket(
        false, List.of(), List.of(), "1.21.40", List.of(), false, false);
  }

  private static byte[] bytes(int... values) {
    byte[] result = new byte[values.length];
    for (int index = 0; index < values.length; index++) {
      result[index] = (byte) values[index];
    }
    return result;
  }

  private static byte[] concat(byte[]... values) {
    int length = 0;
    for (byte[] value : values) {
      length += value.length;
    }
    byte[] result = new byte[length];
    int position = 0;
    for (byte[] value : values) {
      System.arraycopy(value, 0, result, position, value.length);
      position += value.length;
    }
    return result;
  }
}
