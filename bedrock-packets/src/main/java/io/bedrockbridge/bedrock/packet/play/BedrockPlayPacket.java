package io.bedrockbridge.bedrock.packet.play;

import io.bedrockbridge.protocol.PacketDirection;

/** Version-neutral packet data whose exact wire schema is selected by the session registry. */
public interface BedrockPlayPacket {
  /** Returns the ten-bit protocol packet identifier. */
  int packetId();

  /** Returns the packet direction relative to the bridge server. */
  PacketDirection direction();
}
