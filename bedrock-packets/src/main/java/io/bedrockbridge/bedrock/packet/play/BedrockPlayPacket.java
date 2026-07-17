package io.bedrockbridge.bedrock.packet.play;

import io.bedrockbridge.protocol.PacketDirection;
import io.bedrockbridge.protocol.ProtocolVersion;

/** Immutable packet metadata shared by typed Minecraft Bedrock play packets. */
public interface BedrockPlayPacket {
  /** Returns the ten-bit protocol packet identifier. */
  int packetId();

  /** Returns the exact Bedrock wire version owning this layout. */
  ProtocolVersion protocolVersion();

  /** Returns the packet direction relative to the bridge server. */
  PacketDirection direction();
}
