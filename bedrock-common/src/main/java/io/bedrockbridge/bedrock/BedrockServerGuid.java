package io.bedrockbridge.bedrock;

import java.security.SecureRandom;
import java.util.Objects;

/** Generates and formats the 64-bit server identifier used by RakNet discovery. */
public final class BedrockServerGuid {
  private BedrockServerGuid() {}

  /** Generates a nonzero identifier using the complete 64-bit value space. */
  public static long generate(SecureRandom random) {
    Objects.requireNonNull(random, "random");
    long serverGuid;
    do {
      serverGuid = random.nextLong();
    } while (serverGuid == 0);
    return serverGuid;
  }

  /** Formats the wire identifier as the unsigned decimal value required in the MOTD. */
  public static String formatAdvertisement(long serverGuid) {
    if (serverGuid == 0) {
      throw new IllegalArgumentException("serverGuid must be nonzero");
    }
    return Long.toUnsignedString(serverGuid);
  }
}
