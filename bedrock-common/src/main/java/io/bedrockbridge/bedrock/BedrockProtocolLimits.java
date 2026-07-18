package io.bedrockbridge.bedrock;

/** Immutable allocation, collection, and decompression budgets for untrusted play traffic. */
public record BedrockProtocolLimits(
    int maximumConnectedPayloadBytes,
    int maximumPacketBytes,
    int maximumPacketsPerBatch,
    int maximumDecompressedBatchBytes,
    int maximumCompressionRatio,
    int maximumLoginBytes,
    int maximumStringBytes,
    int maximumResourcePacks,
    int maximumResourcePackIdBytes,
    int maximumRegistryEntries) {
  private static final int KIBIBYTE = 1024;
  private static final int MEBIBYTE = KIBIBYTE * KIBIBYTE;

  /** Validates positive budgets and their required containment relationships. */
  public BedrockProtocolLimits {
    if (maximumConnectedPayloadBytes < 1
        || maximumPacketBytes < 1
        || maximumPacketsPerBatch < 1
        || maximumDecompressedBatchBytes < 1
        || maximumCompressionRatio < 1
        || maximumLoginBytes < 1
        || maximumStringBytes < 1
        || maximumResourcePacks < 1
        || maximumResourcePackIdBytes < 1
        || maximumRegistryEntries < 1) {
      throw new IllegalArgumentException("Bedrock protocol limits must be positive");
    }
    if (maximumPacketBytes > maximumDecompressedBatchBytes
        || maximumResourcePackIdBytes > maximumStringBytes) {
      throw new IllegalArgumentException("Bedrock protocol limit containment is invalid");
    }
  }

  /** Returns conservative production defaults for the protocol-748 vertical slice. */
  public static BedrockProtocolLimits defaults() {
    return new BedrockProtocolLimits(
        2 * MEBIBYTE,
        MEBIBYTE,
        512,
        4 * MEBIBYTE,
        64,
        MEBIBYTE,
        32 * KIBIBYTE,
        256,
        KIBIBYTE,
        65_536);
  }
}
