package io.bedrockbridge.bedrock.packet.play;

import io.bedrockbridge.bedrock.BedrockPacketIds;
import io.bedrockbridge.protocol.PacketDirection;
import java.util.Objects;

/** Server-selected compression and client-throttling settings for protocol 748. */
public record NetworkSettingsPacket(
    int compressionThreshold,
    NetworkCompressionAlgorithm compressionAlgorithm,
    boolean clientThrottleEnabled,
    int clientThrottleThreshold,
    float clientThrottleScalar)
    implements BedrockPlayPacket {
  /** Validates unsigned wire ranges and finite throttling values. */
  public NetworkSettingsPacket {
    Objects.requireNonNull(compressionAlgorithm, "compressionAlgorithm");
    if (compressionThreshold < 0
        || compressionThreshold > 0xFFFF
        || clientThrottleThreshold < 0
        || clientThrottleThreshold > 0xFF
        || !Float.isFinite(clientThrottleScalar)) {
      throw new IllegalArgumentException("Network settings contain an out-of-range value");
    }
  }

  @Override
  public int packetId() {
    return BedrockPacketIds.NETWORK_SETTINGS;
  }

  @Override
  public PacketDirection direction() {
    return PacketDirection.CLIENTBOUND;
  }
}
