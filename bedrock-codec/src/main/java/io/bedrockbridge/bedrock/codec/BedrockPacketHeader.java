package io.bedrockbridge.bedrock.codec;

/** Decoded Bedrock game-packet header with ten-bit packet and two-bit sub-client fields. */
public record BedrockPacketHeader(int packetId, int senderSubClientId, int targetSubClientId) {
  private static final int MAXIMUM_PACKET_ID = 0x3FF;
  private static final int MAXIMUM_SUB_CLIENT_ID = 0x03;

  /** Validates each packed bit field before it reaches a wire encoder. */
  public BedrockPacketHeader {
    if (packetId < 0 || packetId > MAXIMUM_PACKET_ID) {
      throw new IllegalArgumentException("Bedrock packet ID must fit ten bits");
    }
    if (senderSubClientId < 0
        || senderSubClientId > MAXIMUM_SUB_CLIENT_ID
        || targetSubClientId < 0
        || targetSubClientId > MAXIMUM_SUB_CLIENT_ID) {
      throw new IllegalArgumentException("Bedrock sub-client ID must fit two bits");
    }
  }

  /** Packs the header fields into the unsigned-varint value documented by Mojang. */
  public int packedValue() {
    return packetId | senderSubClientId << 10 | targetSubClientId << 12;
  }

  /** Unpacks and validates a complete header value, rejecting reserved high bits. */
  public static BedrockPacketHeader fromPackedValue(long value) {
    if (value < 0 || value > 0x3FFF) {
      throw new IllegalArgumentException("Bedrock packet header contains reserved bits");
    }
    return new BedrockPacketHeader(
        (int) value & MAXIMUM_PACKET_ID,
        (int) (value >>> 10) & MAXIMUM_SUB_CLIENT_ID,
        (int) (value >>> 12) & MAXIMUM_SUB_CLIENT_ID);
  }
}
