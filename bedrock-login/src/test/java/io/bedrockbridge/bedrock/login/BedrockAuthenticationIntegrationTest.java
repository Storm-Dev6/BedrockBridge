package io.bedrockbridge.bedrock.login;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.bedrockbridge.bedrock.auth.BedrockChainVerifier;
import io.bedrockbridge.bedrock.auth.BedrockLoginPayload;
import io.bedrockbridge.bedrock.auth.EcdsaSignatureCodec;
import io.bedrockbridge.bedrock.auth.InMemoryReplayGuard;
import io.bedrockbridge.bedrock.auth.JwtToken;
import io.bedrockbridge.bedrock.crypto.BedrockKeyAgreement;
import io.bedrockbridge.bedrock.crypto.BedrockSessionCipher;
import io.bedrockbridge.bedrock.crypto.HandshakeJwtSigner;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class BedrockAuthenticationIntegrationTest {
  @Test
  void verifiesChainAndActivatesBidirectionalCipher() throws Exception {
    SecureRandom random = new SecureRandom();
    KeyPair root = BedrockKeyAgreement.generate(random);
    KeyPair client = BedrockKeyAgreement.generate(random);
    Instant now = Instant.parse("2026-07-16T00:00:00Z");
    String clientKey = Base64.getEncoder().encodeToString(client.getPublic().getEncoded());
    String claims =
        "{\"nbf\":"
            + now.minusSeconds(5).getEpochSecond()
            + ",\"exp\":"
            + now.plusSeconds(60).getEpochSecond()
            + ",\"identityPublicKey\":\""
            + clientKey
            + "\",\"extraData\":{\"identity\":\"00000000-0000-0000-0000-000000000001\""
            + ",\"displayName\":\"Player\",\"XUID\":\"1\",\"titleId\":\"2\"}}";
    String chain = sign(root, claims, root.getPublic());
    String clientData = sign(client, "{\"DeviceOS\":1}", client.getPublic());
    var verifier =
        new BedrockChainVerifier(
            List.of(root.getPublic()),
            new InMemoryReplayGuard(16),
            Clock.fixed(now, ZoneOffset.UTC),
            Duration.ofSeconds(2));
    var authentication =
        new BedrockAuthenticationSession(verifier, random, new HandshakeJwtSigner());
    AuthenticationChallenge challenge =
        authentication.authenticate(new BedrockLoginPayload(List.of(chain), clientData));
    assertEquals(AuthenticationState.AWAITING_CLIENT_HANDSHAKE, authentication.state());

    JwtToken handshake = JwtToken.parse(challenge.handshakeJwt());
    var serverPublic =
        KeyFactory.getInstance("EC")
            .generatePublic(
                new X509EncodedKeySpec(
                    Base64.getDecoder().decode((String) handshake.header().get("x5u"))));
    byte[] salt = Base64.getDecoder().decode((String) handshake.claims().get("salt"));
    byte[] secret = BedrockKeyAgreement.derive(client.getPrivate(), serverPublic);
    var clientCipher = new BedrockSessionCipher(salt, secret);
    authentication.confirmClientHandshake();
    byte[] encrypted = authentication.encrypt(new byte[] {4, 5, 6});
    assertArrayEquals(new byte[] {4, 5, 6}, clientCipher.decrypt(encrypted));
  }

  private static String sign(KeyPair signer, String claims, java.security.PublicKey headerKey)
      throws Exception {
    Base64.Encoder url = Base64.getUrlEncoder().withoutPadding();
    String header =
        "{\"alg\":\"ES384\",\"x5u\":\""
            + Base64.getEncoder().encodeToString(headerKey.getEncoded())
            + "\"}";
    String first = url.encodeToString(header.getBytes(StandardCharsets.UTF_8));
    String second = url.encodeToString(claims.getBytes(StandardCharsets.UTF_8));
    String input = first + '.' + second;
    Signature signature = Signature.getInstance("SHA384withECDSA");
    signature.initSign(signer.getPrivate());
    signature.update(input.getBytes(StandardCharsets.US_ASCII));
    return input + '.' + url.encodeToString(EcdsaSignatureCodec.derToP1363(signature.sign()));
  }
}
