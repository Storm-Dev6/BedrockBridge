package io.bedrockbridge.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class BedrockServerGuidTest {
  @Test
  void skipsZeroAndKeepsTheCompleteUnsignedValueSpace() {
    SecureRandom random =
        new SecureRandom() {
          private int invocation;

          @Override
          public long nextLong() {
            return invocation++ == 0 ? 0 : Long.MIN_VALUE;
          }
        };

    long generated = BedrockServerGuid.generate(random);

    assertEquals(Long.MIN_VALUE, generated);
    assertEquals("9223372036854775808", BedrockServerGuid.formatAdvertisement(generated));
  }

  @Test
  void refusesReservedZeroIdentifier() {
    assertThrows(IllegalArgumentException.class, () -> BedrockServerGuid.formatAdvertisement(0));
  }
}
