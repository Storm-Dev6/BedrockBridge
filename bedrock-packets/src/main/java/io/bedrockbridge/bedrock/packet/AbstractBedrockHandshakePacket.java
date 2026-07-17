package io.bedrockbridge.bedrock.packet;

import io.bedrockbridge.bedrock.BedrockProtocol;
import io.bedrockbridge.protocol.Packet;
import io.bedrockbridge.protocol.PacketDirection;
import io.bedrockbridge.protocol.ProtocolState;
import io.bedrockbridge.protocol.ProtocolVersion;

/** Shared immutable metadata for Bedrock RakNet handshake packets. */
public abstract class AbstractBedrockHandshakePacket implements Packet {
  private final int packetId;
  private final PacketDirection direction;

  /** Creates packet metadata for one wire ID and direction. */
  protected AbstractBedrockHandshakePacket(int packetId, PacketDirection direction) {
    this.packetId = packetId;
    this.direction = direction;
  }

  @Override
  public final int packetId() {
    return packetId;
  }

  @Override
  public final ProtocolVersion protocolVersion() {
    return BedrockProtocol.HANDSHAKE_VERSION;
  }

  @Override
  public final ProtocolState state() {
    return ProtocolState.HANDSHAKE;
  }

  @Override
  public final PacketDirection direction() {
    return direction;
  }
}
