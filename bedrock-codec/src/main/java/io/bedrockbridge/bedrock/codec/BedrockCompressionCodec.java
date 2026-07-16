package io.bedrockbridge.bedrock.codec;

import io.bedrockbridge.bedrock.BedrockValidationException;
import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** Bounded zlib/no-compression codec that rejects truncated streams and compression bombs. */
public final class BedrockCompressionCodec {
    private final CompressionSettings settings;

    /** Creates a codec for negotiated immutable settings. */
    public BedrockCompressionCodec(CompressionSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** Compresses payloads at or above threshold, otherwise returns a defensive copy. */
    public byte[] compress(byte[] payload) {
        if (payload.length > settings.maximumDecompressedBytes()) {
            throw new BedrockValidationException("Payload exceeds compression input limit");
        }
        if (settings.algorithm() == CompressionAlgorithm.NONE
                || payload.length < settings.threshold()) {
            return payload.clone();
        }
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        try {
            deflater.setInput(payload);
            deflater.finish();
            byte[] buffer = new byte[Math.min(8192, settings.maximumCompressedBytes())];
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            while (!deflater.finished()) {
                int written = deflater.deflate(buffer);
                if (written == 0 && deflater.needsInput()) break;
                if ((long) output.size() + written > settings.maximumCompressedBytes()) {
                    throw new BedrockValidationException("Compressed payload exceeds limit");
                }
                output.write(buffer, 0, written);
            }
            return output.toByteArray();
        } finally {
            deflater.end();
        }
    }

    /** Decompresses one complete zlib stream under absolute and ratio limits. */
    public byte[] decompress(byte[] compressed) {
        if (compressed.length == 0 || compressed.length > settings.maximumCompressedBytes()) {
            throw new BedrockValidationException("Compressed payload size is invalid");
        }
        if (settings.algorithm() == CompressionAlgorithm.NONE) return compressed.clone();
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed);
            byte[] buffer = new byte[Math.min(8192, settings.maximumDecompressedBytes())];
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            while (!inflater.finished()) {
                int written = inflater.inflate(buffer);
                if (written == 0) {
                    if (inflater.needsDictionary() || inflater.needsInput()) {
                        throw new BedrockValidationException("Compressed stream is incomplete");
                    }
                }
                long nextSize = (long) output.size() + written;
                if (nextSize > settings.maximumDecompressedBytes()
                        || nextSize > (long) compressed.length * settings.maximumRatio()) {
                    throw new BedrockValidationException("Compression ratio or output limit exceeded");
                }
                output.write(buffer, 0, written);
            }
            if (inflater.getRemaining() != 0) {
                throw new BedrockValidationException("Trailing compressed bytes are forbidden");
            }
            return output.toByteArray();
        } catch (DataFormatException invalid) {
            throw new BedrockValidationException("Invalid zlib stream");
        } finally {
            inflater.end();
        }
    }
}
