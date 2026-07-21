package io.bedrockbridge.bedrock.codec;

import io.bedrockbridge.bedrock.BedrockValidationException;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** Bounded zlib/no-compression codec that rejects truncated streams and compression bombs. */
public final class BedrockCompressionCodec {
  private static final int ZLIB_PACKET = 0x00;
  private static final int UNCOMPRESSED_PACKET = 0xFF;
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
        if (written == 0 && deflater.needsInput()) {
          break;
        }
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

  /** Encodes one post-negotiation batch with its per-packet compression algorithm marker. */
  public byte[] compressPacket(byte[] payload) {
    byte[] clear = Objects.requireNonNull(payload, "payload");
    boolean useZlib =
        settings.algorithm() == CompressionAlgorithm.ZLIB && clear.length >= settings.threshold();
    byte[] body = useZlib ? compress(clear) : clear.clone();
    if (body.length > settings.maximumCompressedBytes()) {
      throw new BedrockValidationException("Packet payload exceeds compression limit");
    }
    byte[] encoded = new byte[body.length + 1];
    encoded[0] = (byte) (useZlib ? ZLIB_PACKET : UNCOMPRESSED_PACKET);
    System.arraycopy(body, 0, encoded, 1, body.length);
    return encoded;
  }

  /** Decompresses one complete zlib stream under absolute and ratio limits. */
  public byte[] decompress(byte[] compressed) {
    if (compressed.length == 0 || compressed.length > settings.maximumCompressedBytes()) {
      throw new BedrockValidationException("Compressed payload size is invalid");
    }
    if (settings.algorithm() == CompressionAlgorithm.NONE) {
      return compressed.clone();
    }
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

  /** Decodes one post-negotiation batch selected by its per-packet algorithm marker. */
  public byte[] decompressPacket(byte[] encoded) {
    byte[] packet = Objects.requireNonNull(encoded, "encoded");
    if (packet.length < 2 || packet.length > settings.maximumCompressedBytes() + 1L) {
      throw new BedrockValidationException("Compressed packet size is invalid");
    }
    int algorithm = Byte.toUnsignedInt(packet[0]);
    byte[] body = Arrays.copyOfRange(packet, 1, packet.length);
    if (algorithm == ZLIB_PACKET) {
      if (settings.algorithm() != CompressionAlgorithm.ZLIB) {
        throw new BedrockValidationException("Zlib packet was not negotiated");
      }
      return decompress(body);
    }
    if (algorithm == UNCOMPRESSED_PACKET) {
      if (body.length > settings.maximumDecompressedBytes()) {
        throw new BedrockValidationException("Uncompressed packet exceeds limit");
      }
      return body;
    }
    throw new BedrockValidationException("Unsupported packet compression algorithm: " + algorithm);
  }
}
