package io.bedrockbridge.bedrock.packet.play;

/** Protocol-748 ResourcePackClientResponse values published by Mojang. */
public enum ResourcePackResponse {
  CANCEL(1),
  DOWNLOADING(2),
  DOWNLOADING_FINISHED(3),
  RESOURCE_PACK_STACK_FINISHED(4);

  private final int wireValue;

  ResourcePackResponse(int wireValue) {
    this.wireValue = wireValue;
  }

  /** Returns the unsigned-byte wire value. */
  public int wireValue() {
    return wireValue;
  }

  /** Resolves an exact published wire value. */
  public static ResourcePackResponse fromWireValue(int value) {
    for (ResourcePackResponse response : values()) {
      if (response.wireValue == value) {
        return response;
      }
    }
    throw new IllegalArgumentException("Unknown resource pack response: " + value);
  }
}
