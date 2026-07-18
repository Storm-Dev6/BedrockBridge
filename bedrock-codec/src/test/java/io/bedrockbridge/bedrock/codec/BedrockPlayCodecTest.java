package io.bedrockbridge.bedrock.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.bedrockbridge.bedrock.BedrockPlayState;
import io.bedrockbridge.bedrock.BedrockProtocol;
import io.bedrockbridge.bedrock.BedrockProtocolLimits;
import io.bedrockbridge.bedrock.BedrockValidationException;
import io.bedrockbridge.bedrock.packet.play.BedrockPlayPacket;
import io.bedrockbridge.common.RegistrationException;
import io.bedrockbridge.protocol.PacketDirection;
import io.bedrockbridge.protocol.ProtocolVersion;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BedrockPlayCodecTest {
  private static final BedrockProtocolLimits LIMITS =
      new BedrockProtocolLimits(256, 64, 4, 128, 16, 64, 32, 8, 16, 16);
  private static final BedrockPlayPacketCodec<NetworkVersionProbe> PROBE_CODEC =
      new BedrockPlayPacketCodec<>() {
        @Override
        public void encode(NetworkVersionProbe packet, BedrockBinaryWriter writer) {
          writer.writeIntBE(packet.networkVersion());
        }

        @Override
        public NetworkVersionProbe decode(BedrockBinaryReader reader) {
          return new NetworkVersionProbe(reader.readIntBE());
        }
      };

  @Test
  void registryDrivenCodecMatchesRequestNetworkSettingsShape() {
    BedrockPlayPacketRegistration<NetworkVersionProbe> registration = registration();
    BedrockPlayPacketRegistry registry =
        BedrockPlayPacketRegistry.builder(LIMITS).register(registration).build();
    var codec = new BedrockPlayCodec(BedrockProtocol.PLAY_VERSION_748, LIMITS, registry);

    byte[] vector = {(byte) 0xC1, 0x01, 0x00, 0x00, 0x02, (byte) 0xEC};
    assertArrayEquals(
        vector,
        codec.encode(new NetworkVersionProbe(748), BedrockPlayState.NETWORK_SETTINGS, 0, 0));
    DecodedBedrockPacket decoded =
        codec.decode(vector, BedrockPlayState.NETWORK_SETTINGS, PacketDirection.SERVERBOUND);
    assertEquals(new BedrockPacketHeader(193, 0, 0), decoded.header());
    assertEquals(new NetworkVersionProbe(748), decoded.packet());
    assertSame(
        registration,
        registry
            .find(
                BedrockProtocol.PLAY_VERSION_748,
                BedrockPlayState.NETWORK_SETTINGS,
                PacketDirection.SERVERBOUND,
                193)
            .orElseThrow());
  }

  @Test
  void registryRejectsWireAndTypeCollisions() {
    var builder = BedrockPlayPacketRegistry.builder(LIMITS).register(registration());
    assertThrows(RegistrationException.class, () -> builder.register(registration()));

    var collision =
        new BedrockPlayPacketRegistration<>(
            BedrockProtocol.PLAY_VERSION_748,
            193,
            PacketDirection.SERVERBOUND,
            Set.of(BedrockPlayState.NETWORK_SETTINGS),
            OtherProbe.class,
            new BedrockPlayPacketCodec<>() {
              @Override
              public void encode(OtherProbe packet, BedrockBinaryWriter writer) {
                writer.writeByte(packet.value());
              }

              @Override
              public OtherProbe decode(BedrockBinaryReader reader) {
                return new OtherProbe(reader.readUnsignedByte());
              }
            });
    assertThrows(RegistrationException.class, () -> builder.register(collision));
  }

  @Test
  void rejectsUnknownWrongDirectionWrongStateAndTrailingBytes() {
    var codec =
        new BedrockPlayCodec(
            BedrockProtocol.PLAY_VERSION_748,
            LIMITS,
            BedrockPlayPacketRegistry.builder(LIMITS).register(registration()).build());
    byte[] valid = {(byte) 0xC1, 0x01, 0x00, 0x00, 0x02, (byte) 0xEC};
    assertThrows(
        BedrockValidationException.class,
        () -> codec.decode(valid, BedrockPlayState.LOGIN, PacketDirection.SERVERBOUND));
    assertThrows(
        BedrockValidationException.class,
        () -> codec.decode(valid, BedrockPlayState.NETWORK_SETTINGS, PacketDirection.CLIENTBOUND));
    assertThrows(
        BedrockValidationException.class,
        () ->
            codec.decode(
                new byte[] {0x7F}, BedrockPlayState.NETWORK_SETTINGS, PacketDirection.SERVERBOUND));
    assertThrows(
        BedrockValidationException.class,
        () ->
            codec.decode(
                new byte[] {(byte) 0xC1, 0x01, 0, 0, 2, (byte) 0xEC, 0},
                BedrockPlayState.NETWORK_SETTINGS,
                PacketDirection.SERVERBOUND));
  }

  private static BedrockPlayPacketRegistration<NetworkVersionProbe> registration() {
    return new BedrockPlayPacketRegistration<>(
        BedrockProtocol.PLAY_VERSION_748,
        193,
        PacketDirection.SERVERBOUND,
        Set.of(BedrockPlayState.NETWORK_SETTINGS),
        NetworkVersionProbe.class,
        PROBE_CODEC);
  }

  private record NetworkVersionProbe(int networkVersion) implements BedrockPlayPacket {
    @Override
    public int packetId() {
      return 193;
    }

    @Override
    public ProtocolVersion protocolVersion() {
      return BedrockProtocol.PLAY_VERSION_748;
    }

    @Override
    public PacketDirection direction() {
      return PacketDirection.SERVERBOUND;
    }
  }

  private record OtherProbe(int value) implements BedrockPlayPacket {
    @Override
    public int packetId() {
      return 193;
    }

    @Override
    public ProtocolVersion protocolVersion() {
      return BedrockProtocol.PLAY_VERSION_748;
    }

    @Override
    public PacketDirection direction() {
      return PacketDirection.SERVERBOUND;
    }
  }
}
