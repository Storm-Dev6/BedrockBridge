package io.bedrockbridge.bedrock.packet.play;

/** Compression algorithm wire values published for protocol 748 NetworkSettings. */
public enum NetworkCompressionAlgorithm {
  ZLIB(0),
  SNAPPY(1),
  NONE(0xFFFF);

  private final int wireValue;

  NetworkCompressionAlgorithm(int wireValue) {
    this.wireValue = wireValue;
  }

  /** Returns the unsigned-short wire value. */
  public int wireValue() {
    return wireValue;
  }

  /** Resolves an exact wire value without accepting aliases. */
  public static NetworkCompressionAlgorithm fromWireValue(int value) {
    for (NetworkCompressionAlgorithm algorithm : values()) {
      if (algorithm.wireValue == value) {
        return algorithm;
      }
    }
    throw new IllegalArgumentException("Unknown Bedrock compression algorithm: " + value);
  }
}
