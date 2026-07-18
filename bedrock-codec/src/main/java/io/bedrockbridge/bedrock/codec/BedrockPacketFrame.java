package io.bedrockbridge.bedrock.codec;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/** Immutable ownership boundary for one packet header and its encoded field payload. */
public final class BedrockPacketFrame {
  private final BedrockPacketHeader header;
  private final byte[] payload;

  /** Copies the caller-owned payload into an immutable frame. */
  public BedrockPacketFrame(BedrockPacketHeader header, byte[] payload) {
    this.header = Objects.requireNonNull(header, "header");
    this.payload = Objects.requireNonNull(payload, "payload").clone();
  }

  /** Returns the decoded packet header. */
  public BedrockPacketHeader header() {
    return header;
  }

  /** Returns a new read-only payload view. */
  public ByteBuffer payload() {
    return ByteBuffer.wrap(payload).asReadOnlyBuffer();
  }

  /** Returns the packet field byte count, excluding the header. */
  public int payloadLength() {
    return payload.length;
  }

  byte[] copyPayload() {
    return payload.clone();
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || (other instanceof BedrockPacketFrame frame
            && header.equals(frame.header)
            && Arrays.equals(payload, frame.payload));
  }

  @Override
  public int hashCode() {
    return 31 * header.hashCode() + Arrays.hashCode(payload);
  }

  @Override
  public String toString() {
    return "BedrockPacketFrame[header=" + header + ", payloadLength=" + payload.length + ']';
  }
}
