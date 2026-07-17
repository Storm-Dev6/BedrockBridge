package io.bedrockbridge.bedrock.packet.play;

/** Protocol-748 PlayStatus values published by Mojang. */
public enum PlayStatus {
  LOGIN_SUCCESS(0),
  LOGIN_FAILED_CLIENT_OLD(1),
  LOGIN_FAILED_SERVER_OLD(2),
  PLAYER_SPAWN(3),
  LOGIN_FAILED_INVALID_TENANT(4),
  LOGIN_FAILED_EDU_TO_VANILLA(5),
  LOGIN_FAILED_VANILLA_TO_EDU(6),
  LOGIN_FAILED_SERVER_FULL_SUB_CLIENT(7),
  LOGIN_FAILED_EDITOR_TO_VANILLA(8),
  LOGIN_FAILED_VANILLA_TO_EDITOR(9);

  private final int wireValue;

  PlayStatus(int wireValue) {
    this.wireValue = wireValue;
  }

  /** Returns the big-endian integer wire value. */
  public int wireValue() {
    return wireValue;
  }

  /** Resolves an exact published wire value. */
  public static PlayStatus fromWireValue(int value) {
    for (PlayStatus status : values()) {
      if (status.wireValue == value) {
        return status;
      }
    }
    throw new IllegalArgumentException("Unknown Bedrock play status: " + value);
  }
}
