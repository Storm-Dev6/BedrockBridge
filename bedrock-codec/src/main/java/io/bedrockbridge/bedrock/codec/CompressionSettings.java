package io.bedrockbridge.bedrock.codec;

import java.util.Objects;

/** Negotiated compression algorithm, threshold, and decompression safety limits. */
public record CompressionSettings(
        CompressionAlgorithm algorithm,
        int threshold,
        int maximumCompressedBytes,
        int maximumDecompressedBytes,
        int maximumRatio) {
    /** Validates all compression budgets. */
    public CompressionSettings {
        Objects.requireNonNull(algorithm, "algorithm");
        if (threshold < 0
                || maximumCompressedBytes < 1
                || maximumDecompressedBytes < 1
                || maximumRatio < 1) {
            throw new IllegalArgumentException("Invalid compression settings");
        }
    }
}
