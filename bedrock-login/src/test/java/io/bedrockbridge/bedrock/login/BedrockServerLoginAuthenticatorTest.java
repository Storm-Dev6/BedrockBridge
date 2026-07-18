package io.bedrockbridge.bedrock.login;

import static org.junit.jupiter.api.Assertions.assertThrows;

import io.bedrockbridge.bedrock.BedrockProtocolLimits;
import io.bedrockbridge.bedrock.auth.BedrockChainVerifier;
import io.bedrockbridge.bedrock.auth.InMemoryReplayGuard;
import io.bedrockbridge.bedrock.packet.play.LoginPacket;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class BedrockServerLoginAuthenticatorTest {
  @Test
  void offlineDenyFailsClosedBeforeAcceptingPayload() throws Exception {
    var generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp384r1"));
    var verifier =
        new BedrockChainVerifier(
            List.of(generator.generateKeyPair().getPublic()),
            new InMemoryReplayGuard(1),
            Clock.systemUTC(),
            Duration.ofSeconds(5));
    assertThrows(
        RuntimeException.class,
        () ->
            new BedrockServerLoginAuthenticator(
                    BedrockAuthMode.OFFLINE_DENY, verifier, BedrockProtocolLimits.defaults())
                .authenticate(new LoginPacket(748, new byte[] {1, 2, 3})));
  }
}
