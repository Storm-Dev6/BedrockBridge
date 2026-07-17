package io.bedrockbridge.bedrock.packet;

import io.bedrockbridge.bedrock.BedrockPacketIds;
import io.bedrockbridge.protocol.PacketDirection;
import io.bedrockbridge.protocol.PacketReader;
import io.bedrockbridge.protocol.PacketWriter;

/** Client keepalive request carrying its monotonic timestamp. */
public final class ConnectedPing extends AbstractBedrockHandshakePacket {
  private long pingTime;

  /** Creates an empty decoder target. */
  public ConnectedPing() {
    this(0);
  }

  /** Creates a ping with the supplied client timestamp. */
  public ConnectedPing(long pingTime) {
    super(BedrockPacketIds.CONNECTED_PING, PacketDirection.SERVERBOUND);
    this.pingTime = pingTime;
  }

  /** Returns the client timestamp. */
  public long pingTime() {
    return pingTime;
  }

  @Override
  public void encode(PacketWriter writer) {
    writer.writeLong(pingTime);
  }

  @Override
  public void decode(PacketReader reader) {
    pingTime = reader.readLong();
  }
}
