package io.bedrockbridge.registry.generator;

import io.bedrockbridge.bedrock.BedrockPacketIds;
import io.bedrockbridge.bedrock.BedrockProtocolLimits;
import io.bedrockbridge.bedrock.codec.BedrockPacketFrame;
import io.bedrockbridge.bedrock.codec.BedrockPacketFrameCodec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Extracts only the protocol-748 StartGame item-list fields from an observed frame. */
public final class StartGameItemListExtractor {
  private static final int MAX_NAME_BYTES = 512;
  private static final int MIN_TAIL_BYTES = 8 + 16 + 3;

  private StartGameItemListExtractor() {}

  /** Finds the uniquely identifiable vanilla item-list sequence without retaining other fields. */
  public static List<ObservedItem> extract(byte[] encodedFrame, BedrockProtocolLimits limits) {
    BedrockPacketFrame frame = new BedrockPacketFrameCodec(limits).decode(encodedFrame);
    if (frame.header().packetId() != BedrockPacketIds.START_GAME) {
      throw new IllegalArgumentException("Observed frame is not StartGame packet 11");
    }
    ByteBuffer payloadView = frame.payload();
    byte[] payload = new byte[payloadView.remaining()];
    payloadView.get(payload);
    Candidate best = null;
    for (int offset = 0; offset < payload.length; offset++) {
      Candidate candidate = tryCandidate(payload, offset, limits.maximumRegistryEntries());
      if (candidate != null && (best == null || candidate.items().size() > best.items().size())) {
        best = candidate;
      }
    }
    if (best == null) {
      throw new IllegalArgumentException("StartGame item list was not found in observed frame");
    }
    return ItemRegistryArtifact.validate(best.items());
  }

  private static Candidate tryCandidate(byte[] payload, int offset, int maximumItems) {
    Cursor cursor = new Cursor(payload, offset);
    try {
      int count = cursor.readUnsignedVarInt(maximumItems);
      if (count < 1) {
        return null;
      }
      List<ObservedItem> items = new ArrayList<>(count);
      for (int index = 0; index < count; index++) {
        String name = cursor.readString(MAX_NAME_BYTES);
        if (!name.startsWith("minecraft:") || name.length() < 10) {
          return null;
        }
        short itemId = cursor.readShortLE();
        int component = cursor.readUnsignedByte();
        if (component > 1) {
          return null;
        }
        items.add(new ObservedItem(name, itemId, component == 1));
      }
      String correlationId = cursor.readString(128);
      if (correlationId.isEmpty()) {
        return null;
      }
      cursor.readBoolean();
      String serverVersion = cursor.readString(128);
      if (serverVersion.isEmpty() || cursor.remaining() < MIN_TAIL_BYTES) {
        return null;
      }
      return new Candidate(items);
    } catch (RuntimeException invalid) {
      return null;
    }
  }

  private record Candidate(List<ObservedItem> items) {}

  private static final class Cursor {
    private final ByteBuffer input;

    private Cursor(byte[] bytes, int offset) {
      input = ByteBuffer.wrap(bytes, offset, bytes.length - offset).order(ByteOrder.LITTLE_ENDIAN);
    }

    private int readUnsignedByte() {
      require(1);
      return Byte.toUnsignedInt(input.get());
    }

    private boolean readBoolean() {
      int value = readUnsignedByte();
      if (value > 1) {
        throw new IllegalArgumentException("invalid boolean");
      }
      return value == 1;
    }

    private short readShortLE() {
      require(Short.BYTES);
      return input.getShort();
    }

    private int readUnsignedVarInt(int maximum) {
      long result = 0;
      for (int index = 0; index < 5; index++) {
        int value = readUnsignedByte();
        if (index == 4 && (value & 0xF0) != 0) {
          throw new IllegalArgumentException("varint overflow");
        }
        result |= (long) (value & 0x7F) << (index * 7);
        if ((value & 0x80) == 0) {
          if (result > maximum) {
            throw new IllegalArgumentException("count exceeds limit");
          }
          return (int) result;
        }
      }
      throw new IllegalArgumentException("varint overflow");
    }

    private String readString(int maximumBytes) {
      int length = readUnsignedVarInt(maximumBytes);
      require(length);
      ByteBuffer bytes = input.slice(input.position(), length).asReadOnlyBuffer();
      input.position(input.position() + length);
      try {
        return StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(bytes)
            .toString();
      } catch (CharacterCodingException invalid) {
        throw new IllegalArgumentException("invalid UTF-8", invalid);
      }
    }

    private int remaining() {
      return input.remaining();
    }

    private void require(int length) {
      if (length < 0 || input.remaining() < length) {
        throw new IllegalArgumentException("truncated StartGame candidate");
      }
    }
  }
}
