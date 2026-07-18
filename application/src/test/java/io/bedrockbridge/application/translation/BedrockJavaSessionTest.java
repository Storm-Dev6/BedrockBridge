package io.bedrockbridge.application.translation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.bedrockbridge.application.javawire.JavaWireException;
import io.bedrockbridge.application.javawire.JavaWorldState;
import io.bedrockbridge.bedrock.BedrockPlayState;
import io.bedrockbridge.bedrock.BedrockProtocol;
import io.bedrockbridge.bedrock.BedrockProtocolLimits;
import io.bedrockbridge.bedrock.auth.BedrockChainVerifier;
import io.bedrockbridge.bedrock.auth.InMemoryReplayGuard;
import io.bedrockbridge.bedrock.crypto.HandshakeJwtSigner;
import io.bedrockbridge.bedrock.login.BedrockAuthMode;
import io.bedrockbridge.bedrock.login.BedrockAuthenticationSession;
import io.bedrockbridge.bedrock.packet.play.BedrockPlayPacket;
import io.bedrockbridge.bedrock.packet.play.ClientToServerHandshakePacket;
import io.bedrockbridge.bedrock.packet.play.DisconnectPacket;
import io.bedrockbridge.bedrock.packet.play.LoginPacket;
import io.bedrockbridge.bedrock.packet.play.NetworkSettingsPacket;
import io.bedrockbridge.bedrock.packet.play.PlayStatus;
import io.bedrockbridge.bedrock.packet.play.PlayStatusPacket;
import io.bedrockbridge.bedrock.packet.play.RequestNetworkSettingsPacket;
import io.bedrockbridge.bedrock.packet.play.ResourcePackClientResponsePacket;
import io.bedrockbridge.bedrock.packet.play.ResourcePackResponse;
import io.bedrockbridge.bedrock.packet.play.ResourcePacksInfoPacket;
import io.bedrockbridge.bedrock.packet.play.ServerToClientHandshakePacket;
import io.bedrockbridge.registry.generator.OfflineLoginMaterial;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class BedrockJavaSessionTest {
  @Test
  void connectsBedrockLoginToJavaWorldBoundaryAndFailsClosedAtStartGameGate() throws Exception {
    Instant now = Instant.parse("2026-07-18T08:00:00Z");
    OfflineLoginMaterial.Generated generated = OfflineLoginMaterial.generate(19132, now);
    BedrockChainVerifier verifier =
        new BedrockChainVerifier(
            List.of(generated.identityKey()),
            new InMemoryReplayGuard(8),
            Clock.fixed(now, ZoneOffset.UTC),
            Duration.ofSeconds(5));
    AtomicBoolean worldReady = new AtomicBoolean();
    JavaSessionGateway gateway = gateway(worldReady);
    BedrockJavaSession session =
        new BedrockJavaSession(
            BedrockProtocolLimits.defaults(),
            new BedrockAuthenticationSession(
                verifier,
                new SecureRandom(),
                new HandshakeJwtSigner(),
                BedrockAuthMode.OFFLINE_ALLOW_SELF_SIGNED),
            ignored -> gateway,
            null);

    BedrockSessionOutput settings =
        session.receive(new RequestNetworkSettingsPacket(BedrockProtocol.NETWORK_PROTOCOL_748));
    assertEquals(BedrockPlayState.LOGIN, session.state());
    assertTrue(settings.packets().getFirst() instanceof NetworkSettingsPacket);

    BedrockSessionOutput login =
        session.receive(
            new LoginPacket(BedrockProtocol.NETWORK_PROTOCOL_748, generated.connectionRequest()));
    assertEquals(BedrockPlayState.AUTHENTICATING, session.state());
    assertTrue(worldReady.get());
    assertTrue(login.packets().stream().anyMatch(PlayStatusPacket.class::isInstance));
    assertTrue(login.packets().stream().anyMatch(ServerToClientHandshakePacket.class::isInstance));

    BedrockSessionOutput packs = session.receive(new ClientToServerHandshakePacket());
    assertEquals(BedrockPlayState.RESOURCE_PACKS, session.state());
    assertTrue(packs.packets().stream().anyMatch(ResourcePacksInfoPacket.class::isInstance));
    assertEquals(2, packs.packets().size());

    session.receive(
        new ResourcePackClientResponsePacket(ResourcePackResponse.DOWNLOADING_FINISHED, List.of()));
    BedrockSessionOutput blocked =
        session.receive(
            new ResourcePackClientResponsePacket(
                ResourcePackResponse.RESOURCE_PACK_STACK_FINISHED, List.of()));
    assertEquals(BedrockPlayState.DISCONNECTING, session.state());
    assertNull(blocked.startGameFrame());
    DisconnectPacket disconnect = (DisconnectPacket) blocked.packets().getFirst();
    assertEquals("BLOCKED_EXTERNAL_OFFICIAL_ARTIFACT", disconnect.message());
  }

  private static JavaSessionGateway gateway(AtomicBoolean worldReady) {
    return new JavaSessionGateway() {
      private final JavaWorldState world = new JavaWorldState();

      @Override
      public List<BedrockPlayPacket> loginPackets() {
        return List.of(new PlayStatusPacket(PlayStatus.LOGIN_SUCCESS));
      }

      @Override
      public List<BedrockPlayPacket> resourcePackFlowStart() {
        return List.of(new ResourcePacksInfoPacket(false, false, false, List.of()));
      }

      @Override
      public JavaWorldState worldState() {
        return world;
      }

      @Override
      public void awaitWorldReady() {
        worldReady.set(true);
      }

      @Override
      public List<BedrockPlayPacket> pumpPlayOnce() throws IOException, JavaWireException {
        return List.of();
      }

      @Override
      public void close() {}
    };
  }
}
