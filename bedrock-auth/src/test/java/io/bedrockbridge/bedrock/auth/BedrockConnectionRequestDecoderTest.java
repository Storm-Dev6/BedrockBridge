package io.bedrockbridge.bedrock.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.bedrockbridge.bedrock.BedrockValidationException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class BedrockConnectionRequestDecoderTest {
  private final BedrockConnectionRequestDecoder decoder = new BedrockConnectionRequestDecoder(128);

  @Test
  void decodesThePublishedTwoLittleEndianLengthFields() {
    byte[] chain = "{\"chain\":[\"first.jwt\",\"second.jwt\"]}".getBytes(StandardCharsets.UTF_8);
    byte[] client = "client.jwt".getBytes(StandardCharsets.UTF_8);

    BedrockLoginPayload payload = decoder.decode(ByteBuffer.wrap(request(chain, client)));

    assertEquals(List.of("first.jwt", "second.jwt"), payload.chain());
    assertEquals("client.jwt", payload.clientDataJwt());
  }

  @Test
  void rejectsNegativeOversizedTruncatedAndTrailingLengths() {
    assertThrows(
        BedrockValidationException.class,
        () -> decoder.decode(ByteBuffer.wrap(new byte[] {-1, -1, -1, -1})));
    assertThrows(
        BedrockValidationException.class,
        () -> decoder.decode(ByteBuffer.wrap(new byte[] {-127, 0, 0, 0})));
    assertThrows(
        BedrockValidationException.class,
        () -> decoder.decode(ByteBuffer.wrap(new byte[] {2, 0, 0, 0, 1})));

    byte[] valid =
        request(
            "{\"chain\":[\"jwt\"]}".getBytes(StandardCharsets.UTF_8),
            "client".getBytes(StandardCharsets.UTF_8));
    byte[] trailing = java.util.Arrays.copyOf(valid, valid.length + 1);
    assertThrows(BedrockValidationException.class, () -> decoder.decode(ByteBuffer.wrap(trailing)));
  }

  @Test
  void rejectsInvalidUtf8BeforeJsonOrJwtParsing() {
    byte[] invalidChain = request(new byte[] {(byte) 0xC3, 0x28}, new byte[] {1});
    byte[] invalidClient =
        request(
            "{\"chain\":[\"jwt\"]}".getBytes(StandardCharsets.UTF_8),
            new byte[] {(byte) 0xC3, 0x28});

    assertThrows(
        BedrockValidationException.class, () -> decoder.decode(ByteBuffer.wrap(invalidChain)));
    assertThrows(
        BedrockValidationException.class, () -> decoder.decode(ByteBuffer.wrap(invalidClient)));
  }

  @Test
  void readsFromAnIsolatedBufferViewWithoutChangingTheCallerPosition() {
    byte[] encoded =
        request(
            "{\"chain\":[\"jwt\"]}".getBytes(StandardCharsets.UTF_8),
            "client".getBytes(StandardCharsets.UTF_8));
    ByteBuffer caller = ByteBuffer.allocate(encoded.length + 2);
    caller.put((byte) 9).put(encoded).put((byte) 8).flip();
    caller.position(1);
    caller.limit(1 + encoded.length);

    decoder.decode(caller);

    assertEquals(1, caller.position());
  }

  private static byte[] request(byte[] chain, byte[] client) {
    return ByteBuffer.allocate(Integer.BYTES * 2 + chain.length + client.length)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putInt(chain.length)
        .put(chain)
        .putInt(client.length)
        .put(client)
        .array();
  }
}
