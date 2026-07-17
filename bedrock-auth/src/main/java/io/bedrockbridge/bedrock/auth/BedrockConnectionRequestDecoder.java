package io.bedrockbridge.bedrock.auth;

import io.bedrockbridge.bedrock.BedrockValidationException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Decodes the two little-endian length-delimited strings inside a Bedrock Login request. */
public final class BedrockConnectionRequestDecoder {
  private final int maximumBytes;
  private final BedrockLoginPayloadParser payloadParser;

  /** Creates a decoder with an explicit aggregate byte limit. */
  public BedrockConnectionRequestDecoder(int maximumBytes) {
    if (maximumBytes < 1) {
      throw new IllegalArgumentException("maximumBytes must be positive");
    }
    this.maximumBytes = maximumBytes;
    payloadParser = new BedrockLoginPayloadParser();
  }

  /** Decodes the complete request and rejects trailing, truncated, oversized, or invalid UTF-8. */
  public BedrockLoginPayload decode(ByteBuffer request) {
    ByteBuffer input =
        Objects.requireNonNull(request, "request").slice().order(ByteOrder.LITTLE_ENDIAN);
    if (input.remaining() > maximumBytes) {
      throw malformed("Connection request exceeds configured limit");
    }
    String chainJson = readLengthDelimitedUtf8(input, "login chain");
    String clientDataJwt = readLengthDelimitedUtf8(input, "client data JWT");
    if (input.hasRemaining()) {
      throw malformed("Connection request contains trailing bytes");
    }
    return payloadParser.parse(chainJson, clientDataJwt);
  }

  private String readLengthDelimitedUtf8(ByteBuffer input, String field) {
    if (input.remaining() < Integer.BYTES) {
      throw malformed("Connection request is truncated before " + field + " length");
    }
    int length = input.getInt();
    if (length < 0 || length > maximumBytes || length > input.remaining()) {
      throw malformed("Invalid " + field + " length");
    }
    ByteBuffer bytes = input.slice(input.position(), length);
    input.position(input.position() + length);
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(bytes)
          .toString();
    } catch (CharacterCodingException invalid) {
      throw malformed(field + " contains invalid UTF-8");
    }
  }

  private static BedrockValidationException malformed(String message) {
    return new BedrockValidationException(message);
  }
}
