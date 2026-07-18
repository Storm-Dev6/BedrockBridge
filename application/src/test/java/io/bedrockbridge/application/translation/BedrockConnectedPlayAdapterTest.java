package io.bedrockbridge.application.translation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.bedrockbridge.application.javawire.JavaWireException;
import io.bedrockbridge.application.javawire.JavaWorldState;
import io.bedrockbridge.bedrock.BedrockPlayState;
import io.bedrockbridge.bedrock.BedrockProtocol;
import io.bedrockbridge.bedrock.BedrockProtocolLimits;
import io.bedrockbridge.bedrock.auth.BedrockChainVerifier;
import io.bedrockbridge.bedrock.auth.InMemoryReplayGuard;
import io.bedrockbridge.bedrock.codec.BedrockBatchCodec;
import io.bedrockbridge.bedrock.codec.BedrockCompressionCodec;
import io.bedrockbridge.bedrock.codec.BedrockPacketFrameCodec;
import io.bedrockbridge.bedrock.codec.BedrockPlayCodec;
import io.bedrockbridge.bedrock.codec.BedrockProtocol748PacketRegistry;
import io.bedrockbridge.bedrock.codec.CompressionAlgorithm;
import io.bedrockbridge.bedrock.codec.CompressionSettings;
import io.bedrockbridge.bedrock.crypto.HandshakeJwtSigner;
import io.bedrockbridge.bedrock.login.BedrockAuthMode;
import io.bedrockbridge.bedrock.login.BedrockAuthenticationSession;
import io.bedrockbridge.bedrock.packet.play.BedrockPlayPacket;
import io.bedrockbridge.bedrock.packet.play.ClientToServerHandshakePacket;
import io.bedrockbridge.bedrock.packet.play.LoginPacket;
import io.bedrockbridge.bedrock.packet.play.RequestNetworkSettingsPacket;
import io.bedrockbridge.registry.generator.OfflineLoginMaterial;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BedrockConnectedPlayAdapterTest {
  @Test
  void dispatchesGameBatchThroughTypedSessionAndActivatesEncryptionBoundary() throws Exception {
    Instant now = Instant.parse("2026-07-18T08:00:00Z");
    OfflineLoginMaterial.Generated generated = OfflineLoginMaterial.generate(19132, now);
    BedrockProtocolLimits limits = BedrockProtocolLimits.defaults();
    BedrockChainVerifier verifier =
        new BedrockChainVerifier(
            List.of(generated.identityKey()),
            new InMemoryReplayGuard(8),
            Clock.fixed(now, ZoneOffset.UTC),
            Duration.ofSeconds(5));
    JavaSessionGateway gateway = gateway();
    BedrockJavaSession session =
        new BedrockJavaSession(
            limits,
            new BedrockAuthenticationSession(
                verifier,
                new SecureRandom(),
                new HandshakeJwtSigner(),
                BedrockAuthMode.OFFLINE_ALLOW_SELF_SIGNED),
            ignored -> gateway,
            null);
    BedrockConnectedPlayAdapter adapter = new BedrockConnectedPlayAdapter(session);
    List<byte[]> sent = new ArrayList<>();

    byte[] settings =
        adapterInput(
            new BedrockPlayCodec(
                    BedrockProtocol.PLAY_VERSION_748,
                    limits,
                    BedrockProtocol748PacketRegistry.create(limits))
                .encode(
                    new RequestNetworkSettingsPacket(BedrockProtocol.NETWORK_PROTOCOL_748),
                    BedrockPlayState.NETWORK_SETTINGS,
                    0,
                    0),
            limits);
    adapter.handle(ByteBuffer.wrap(settings), value -> sent.add(copy(value)));
    assertEquals(BedrockPlayState.LOGIN, session.state());
    assertFalse(sent.isEmpty());
    assertTrue(sent.getFirst().length > 1);

    BedrockPlayCodec codec =
        new BedrockPlayCodec(
            BedrockProtocol.PLAY_VERSION_748,
            limits,
            BedrockProtocol748PacketRegistry.create(limits));
    byte[] login =
        adapterInput(
            codec.encode(
                new LoginPacket(
                    BedrockProtocol.NETWORK_PROTOCOL_748, generated.connectionRequest()),
                BedrockPlayState.LOGIN,
                0,
                0),
            limits);
    adapter.handle(ByteBuffer.wrap(login), value -> sent.add(copy(value)));
    assertEquals(BedrockPlayState.AUTHENTICATING, session.state());
    assertTrue(sent.size() >= 2);

    byte[] handshake =
        adapterInput(
            codec.encode(
                new ClientToServerHandshakePacket(), BedrockPlayState.AUTHENTICATING, 0, 0),
            limits);
    adapter.handle(ByteBuffer.wrap(handshake), value -> sent.add(copy(value)));
    assertTrue(session.encryptionActive());
    assertTrue(sent.getLast().length > 1);
  }

  private static byte[] adapterInput(byte[] frame, BedrockProtocolLimits limits) {
    byte[] batch =
        new BedrockBatchCodec(limits)
            .encode(List.of(new BedrockPacketFrameCodec(limits).decode(frame)));
    byte[] compressed =
        new BedrockCompressionCodec(
                new CompressionSettings(
                    CompressionAlgorithm.ZLIB,
                    512,
                    limits.maximumConnectedPayloadBytes(),
                    limits.maximumDecompressedBatchBytes(),
                    limits.maximumCompressionRatio()))
            .compress(batch);
    byte[] connected = new byte[compressed.length + 1];
    connected[0] = (byte) 0xFE;
    System.arraycopy(compressed, 0, connected, 1, compressed.length);
    return connected;
  }

  private static byte[] copy(ByteBuffer value) {
    byte[] bytes = new byte[value.remaining()];
    value.duplicate().get(bytes);
    return bytes;
  }

  private static JavaSessionGateway gateway() {
    return new JavaSessionGateway() {
      private final JavaWorldState world = new JavaWorldState();

      @Override
      public List<BedrockPlayPacket> loginPackets() {
        return List.of();
      }

      @Override
      public List<BedrockPlayPacket> resourcePackFlowStart() {
        return List.of();
      }

      @Override
      public JavaWorldState worldState() {
        return world;
      }

      @Override
      public void awaitWorldReady() {}

      @Override
      public List<BedrockPlayPacket> pumpPlayOnce() throws JavaWireException {
        return List.of();
      }

      @Override
      public void close() {}
    };
  }
}
