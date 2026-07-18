package io.bedrockbridge.registry.generator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.bedrockbridge.bedrock.auth.BedrockConnectionRequestDecoder;
import io.bedrockbridge.bedrock.auth.JwtToken;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class OfflineLoginMaterialTest {
  @Test
  void generatedMaterialPassesOwnVerifierAndUsesExpectedWireLengths() throws Exception {
    Instant now = Instant.parse("2026-07-18T08:00:00Z");
    OfflineLoginMaterial.Generated generated = OfflineLoginMaterial.generate(19142, now);

    OfflineLoginMaterial.verify(generated, now);
    byte[] request = generated.connectionRequest();
    ByteBuffer input = ByteBuffer.wrap(request).order(ByteOrder.LITTLE_ENDIAN);
    int chainLength = input.getInt();
    assertTrue(chainLength > 0 && chainLength < request.length);
    input.position(input.position() + chainLength);
    int clientLength = input.getInt();
    assertEquals(clientLength, input.remaining());

    var payload = new BedrockConnectionRequestDecoder(1_048_576).decode(ByteBuffer.wrap(request));
    assertEquals(1, payload.chain().size());
    assertEquals(generated.chainToken(), payload.chain().getFirst());
    assertEquals(generated.clientToken(), payload.clientDataJwt());
  }

  @Test
  void generatedJwtHasBase64UrlSegmentsAndLinkedSyntheticKey() throws Exception {
    OfflineLoginMaterial.Generated generated =
        OfflineLoginMaterial.generate(19142, Instant.parse("2026-07-18T08:00:00Z"));
    JwtToken chain = JwtToken.parse(generated.chainToken());
    JwtToken client = JwtToken.parse(generated.clientToken());
    String encodedKey = Base64.getEncoder().encodeToString(generated.identityKey().getEncoded());
    assertEquals("ES384", chain.header().get("alg"));
    assertEquals(encodedKey, chain.header().get("x5u"));
    assertEquals(encodedKey, client.header().get("x5u"));
    assertEquals(encodedKey, chain.claims().get("identityPublicKey"));
    assertTrue(Boolean.TRUE.equals(chain.claims().get("certificateAuthority")));
    assertEquals(96, chain.signature().length);
    assertEquals(96, client.signature().length);
    for (String token : new String[] {generated.chainToken(), generated.clientToken()}) {
      String[] segments = token.split("\\.", -1);
      assertEquals(3, segments.length);
      assertFalse(segments[0].contains("="));
      assertFalse(segments[1].contains("="));
      assertFalse(segments[2].contains("="));
      Base64.getUrlDecoder().decode(segments[0]);
      Base64.getUrlDecoder().decode(segments[1]);
      Base64.getUrlDecoder().decode(segments[2]);
    }
  }

  @Test
  void generatedRequestAndTokensAreDefensiveCopies() throws Exception {
    OfflineLoginMaterial.Generated generated =
        OfflineLoginMaterial.generate(19142, Instant.parse("2026-07-18T08:00:00Z"));
    byte[] first = generated.connectionRequest();
    byte[] second = generated.connectionRequest();
    assertNotSame(first, second);
    first[0] ^= 0x01;
    assertArrayEquals(second, generated.connectionRequest());
  }
}
