package io.bedrockbridge.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.bedrockbridge.application.upstream.JavaUpstreamPacket;
import io.bedrockbridge.application.upstream.JavaUpstreamSession;
import io.bedrockbridge.application.upstream.JavaUpstreamState;
import io.bedrockbridge.bedrock.BedrockProtocol;
import io.bedrockbridge.bedrock.BedrockProtocolLimits;
import io.bedrockbridge.bedrock.auth.BedrockChainVerifier;
import io.bedrockbridge.bedrock.auth.InMemoryReplayGuard;
import io.bedrockbridge.bedrock.login.BedrockAuthMode;
import io.bedrockbridge.bedrock.login.BedrockServerLoginAuthenticator;
import io.bedrockbridge.bedrock.packet.play.LoginPacket;
import io.bedrockbridge.registry.generator.OfflineLoginMaterial;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BridgeVerticalSliceTest {
  @Test
  void mockBedrockLoginFlowsIntoMockJavaUpstreamWithoutTokens() throws Exception {
    Instant now = Instant.parse("2026-07-18T08:00:00Z");
    OfflineLoginMaterial.Generated generated = OfflineLoginMaterial.generate(19132, now);
    BedrockChainVerifier verifier =
        new BedrockChainVerifier(
            List.of(generated.identityKey()),
            new InMemoryReplayGuard(4),
            Clock.fixed(now, ZoneOffset.UTC),
            Duration.ofSeconds(5));
    var authenticator =
        new BedrockServerLoginAuthenticator(
            BedrockAuthMode.OFFLINE_ALLOW_SELF_SIGNED, verifier, BedrockProtocolLimits.defaults());
    var authenticated =
        authenticator.authenticate(
            new LoginPacket(BedrockProtocol.NETWORK_PROTOCOL_748, generated.connectionRequest()));

    List<JavaUpstreamPacket> sent = new ArrayList<>();
    JavaUpstreamSession java = new JavaUpstreamSession("127.0.0.1", 25565, sent::add);
    java.connect();
    java.beginLogin(authenticated.identity().displayName().substring(0, 16));
    java.enterConfiguration();
    java.acknowledgeConfiguration();

    assertEquals(JavaUpstreamState.PLAY, java.state());
    assertEquals(4, sent.size());
    assertEquals(new JavaUpstreamPacket.Handshake("127.0.0.1", 25565, 2), sent.getFirst());
    assertEquals(new JavaUpstreamPacket.LoginStart("BedrockBridgePro"), sent.get(1));
    assertEquals(new JavaUpstreamPacket.ConfigurationAcknowledged(), sent.get(2));
    assertEquals(new JavaUpstreamPacket.PlayReady(), sent.get(3));
  }
}
