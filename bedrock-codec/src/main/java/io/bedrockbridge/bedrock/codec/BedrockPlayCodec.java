package io.bedrockbridge.bedrock.codec;

import io.bedrockbridge.bedrock.BedrockPlayState;
import io.bedrockbridge.bedrock.BedrockProtocolLimits;
import io.bedrockbridge.bedrock.BedrockValidationException;
import io.bedrockbridge.bedrock.packet.play.BedrockPlayPacket;
import io.bedrockbridge.protocol.PacketDirection;
import io.bedrockbridge.protocol.ProtocolVersion;
import java.util.Objects;

/** Registry-driven typed packet codec with exact version, state, and direction validation. */
public final class BedrockPlayCodec {
  private final ProtocolVersion version;
  private final BedrockProtocolLimits limits;
  private final BedrockPlayPacketRegistry registry;
  private final BedrockPacketFrameCodec frames;

  /** Creates a play codec for exactly one Bedrock wire version. */
  public BedrockPlayCodec(
      ProtocolVersion version, BedrockProtocolLimits limits, BedrockPlayPacketRegistry registry) {
    this.version = Objects.requireNonNull(version, "version");
    this.limits = Objects.requireNonNull(limits, "limits");
    this.registry = Objects.requireNonNull(registry, "registry");
    frames = new BedrockPacketFrameCodec(limits);
  }

  /** Encodes a typed packet and its sub-client routing header. */
  public byte[] encode(
      BedrockPlayPacket packet,
      BedrockPlayState state,
      int senderSubClientId,
      int targetSubClientId) {
    Objects.requireNonNull(packet, "packet");
    BedrockPlayPacketRegistration<?> registration =
        registry
            .find(version, packet.getClass())
            .orElseThrow(() -> malformed("Unknown Bedrock packet type"));
    if (!registration.states().contains(state)
        || packet.packetId() != registration.packetId()
        || packet.direction() != registration.direction()) {
      throw malformed("Bedrock packet metadata is invalid for the current state");
    }
    byte[] payload = encodeRegistered(registration, packet);
    return frames.encode(
        new BedrockPacketFrame(
            new BedrockPacketHeader(packet.packetId(), senderSubClientId, targetSubClientId),
            payload));
  }

  /** Decodes one complete packet under the expected state and direction. */
  public DecodedBedrockPacket decode(
      byte[] encoded, BedrockPlayState state, PacketDirection direction) {
    BedrockPacketFrame frame = frames.decode(encoded);
    BedrockPlayPacketRegistration<?> registration =
        registry
            .find(version, state, direction, frame.header().packetId())
            .orElseThrow(() -> malformed("Unknown or state-invalid Bedrock packet"));
    BedrockPlayPacket packet = decodeRegistered(registration, frame.copyPayload());
    if (packet.packetId() != registration.packetId()
        || packet.direction() != registration.direction()) {
      throw malformed("Decoded Bedrock packet metadata differs from its registration");
    }
    return new DecodedBedrockPacket(frame.header(), packet);
  }

  private byte[] encodeRegistered(
      BedrockPlayPacketRegistration<?> registration, BedrockPlayPacket packet) {
    return encodeTyped(registration, packet, limits.maximumPacketBytes());
  }

  private static <T extends BedrockPlayPacket> byte[] encodeTyped(
      BedrockPlayPacketRegistration<T> registration, BedrockPlayPacket packet, int maximumBytes) {
    T typed = registration.packetType().cast(packet);
    BedrockBinaryWriter writer = new BedrockBinaryWriter(maximumBytes);
    registration.codec().encode(typed, writer);
    return writer.toByteArray();
  }

  private BedrockPlayPacket decodeRegistered(
      BedrockPlayPacketRegistration<?> registration, byte[] payload) {
    return decodeTyped(registration, payload, limits);
  }

  private static <T extends BedrockPlayPacket> T decodeTyped(
      BedrockPlayPacketRegistration<T> registration, byte[] payload, BedrockProtocolLimits limits) {
    BedrockBinaryReader reader = new BedrockBinaryReader(payload, limits);
    T packet = registration.codec().decode(reader);
    if (reader.remaining() != 0) {
      throw malformed("Bedrock packet decoder left trailing bytes");
    }
    return Objects.requireNonNull(packet, "decoded packet");
  }

  private static BedrockValidationException malformed(String message) {
    return new BedrockValidationException(message);
  }
}
