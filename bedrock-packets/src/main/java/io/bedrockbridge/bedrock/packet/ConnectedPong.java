package io.bedrockbridge.bedrock.packet;

import io.bedrockbridge.bedrock.BedrockPacketIds;
import io.bedrockbridge.protocol.PacketDirection;
import io.bedrockbridge.protocol.PacketReader;
import io.bedrockbridge.protocol.PacketWriter;

/** Server keepalive response echoing client time and providing server time. */
public final class ConnectedPong extends AbstractBedrockHandshakePacket {
  private long pingTime;
  private long pongTime;

  /** Creates an empty decoder target. */
  public ConnectedPong() {
    this(0, 0);
  }

  /** Creates a pong with client and server timestamps. */
  public ConnectedPong(long pingTime, long pongTime) {
    super(BedrockPacketIds.CONNECTED_PONG, PacketDirection.CLIENTBOUND);
    this.pingTime = pingTime;
    this.pongTime = pongTime;
  }

  public long pingTime() {
    return pingTime;
  }

  public long pongTime() {
    return pongTime;
  }

  @Override
  public void encode(PacketWriter writer) {
    writer.writeLong(pingTime);
    writer.writeLong(pongTime);
  }

  @Override
  public void decode(PacketReader reader) {
    pingTime = reader.readLong();
    pongTime = reader.readLong();
  }
}
