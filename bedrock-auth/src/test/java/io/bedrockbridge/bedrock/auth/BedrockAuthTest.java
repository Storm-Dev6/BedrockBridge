package io.bedrockbridge.bedrock.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
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
}
