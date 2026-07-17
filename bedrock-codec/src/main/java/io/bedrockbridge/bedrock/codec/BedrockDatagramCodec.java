package io.bedrockbridge.bedrock.codec;

import io.bedrockbridge.bedrock.BedrockPacketIds;
import io.bedrockbridge.bedrock.BedrockProtocol;
import io.bedrockbridge.bedrock.BedrockValidationException;
import io.bedrockbridge.protocol.Packet;
import io.bedrockbridge.protocol.PacketDirection;
import io.bedrockbridge.protocol.ProtocolState;
import io.bedrockbridge.protocol.registry.PacketRegistry;
import io.bedrockbridge.protocol.session.PacketProcessor;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/** Serializes and deserializes complete handshake datagrams including ID and offline magic. */
public final class BedrockDatagramCodec {
  private final PacketProcessor processor;
  private final BedrockPacketValidator validator;

  /** Creates a codec over the authoritative Bedrock catalog and semantic validator. */
  public BedrockDatagramCodec(PacketRegistry registry, BedrockPacketValidator validator) {
    Objects.requireNonNull(registry, "registry");
    this.validator = Objects.requireNonNull(validator, "validator");
    processor = new PacketProcessor(registry);
  }

  /** Decodes one complete handshake datagram in the expected direction. */
  public Packet decode(ByteBuffer datagram, PacketDirection direction) {
    ByteBuffer input = Objects.requireNonNull(datagram, "datagram").slice();
    if (!input.hasRemaining()) {
      throw new BedrockValidationException("Empty Bedrock datagram");
    }
    int packetId = Byte.toUnsignedInt(input.get());
    if (usesOfflineMagic(packetId)) {
      requireMagic(input);
    }
    Packet packet =
        processor.decode(
            BedrockProtocol.HANDSHAKE_VERSION, ProtocolState.HANDSHAKE, direction, packetId, input);
    validator.validate(packet);
    return packet;
  }

  /** Encodes one complete packet into the caller-owned datagram buffer. */
  public int encode(Packet packet, ByteBuffer output) {
    Objects.requireNonNull(output, "output");
    validator.validate(packet);
    int start = output.position();
    output.put((byte) packet.packetId());
    if (usesOfflineMagic(packet.packetId())) {
      output.put(BedrockProtocol.offlineMessageMagic());
    }
    processor.encode(packet, output);
    return output.position() - start;
  }

  private static boolean usesOfflineMagic(int packetId) {
    return packetId >= BedrockPacketIds.OPEN_CONNECTION_REQUEST_1
        && packetId <= BedrockPacketIds.OPEN_CONNECTION_REPLY_2;
  }

  private static void requireMagic(ByteBuffer input) {
    if (input.remaining() < BedrockProtocol.OFFLINE_MESSAGE_MAGIC_LENGTH) {
      throw new BedrockValidationException("Truncated offline message magic");
    }
    byte[] actual = new byte[BedrockProtocol.OFFLINE_MESSAGE_MAGIC_LENGTH];
    input.get(actual);
    if (!Arrays.equals(actual, BedrockProtocol.offlineMessageMagic())) {
      throw new BedrockValidationException("Invalid offline message magic");
    }
  }
}
