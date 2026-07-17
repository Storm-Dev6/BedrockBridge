package io.bedrockbridge.bedrock.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class BedrockAuthTest {
  @Test
  void strictJsonRejectsDuplicateKeys() {
    assertThrows(RuntimeException.class, () -> StrictJson.parseObject("{\"a\":1,\"a\":2}"));
  }

  @Test
  void replayGuardAdmitsProofExactlyOnce() {
    var guard = new InMemoryReplayGuard(2);
    Instant now = Instant.EPOCH;
    assertTrue(guard.admit("proof", now.plusSeconds(5), now));
    assertFalse(guard.admit("proof", now.plusSeconds(5), now));
  }

  @Test
  void jwtSignatureAccessIsDefensive() {
    String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[96]);
    JwtToken token = JwtToken.parse("eyJhbGciOiJFUzM4NCJ9.e30." + signature);
    byte[] exposed = token.signature();
    exposed[0] = 1;
    assertArrayEquals(new byte[96], token.signature());
  }
}
