package io.bedrockbridge.bedrock.packet;

import io.bedrockbridge.bedrock.BedrockPacketIds;
import io.bedrockbridge.protocol.PacketDirection;
import io.bedrockbridge.protocol.PacketReader;
import io.bedrockbridge.protocol.PacketWriter;

/** Connected client request completing identity and timestamp exchange. */
public final class ConnectionRequest extends AbstractBedrockHandshakePacket {
  private long clientGuid;
  private long requestTime;
  private boolean security;

  public ConnectionRequest() {
    this(0, 0, false);
  }

  public ConnectionRequest(long clientGuid, long requestTime, boolean security) {
    super(BedrockPacketIds.CONNECTION_REQUEST, PacketDirection.SERVERBOUND);
    this.clientGuid = clientGuid;
    this.requestTime = requestTime;
    this.security = security;
  }

  public long clientGuid() {
    return clientGuid;
  }

  public long requestTime() {
    return requestTime;
  }

  public boolean security() {
    return security;
  }

  @Override
  public void encode(PacketWriter writer) {
    writer.writeLong(clientGuid);
    writer.writeLong(requestTime);
    writer.writeBoolean(security);
  }

  @Override
  public void decode(PacketReader reader) {
    clientGuid = reader.readLong();
    requestTime = reader.readLong();
    security = reader.readBoolean();
  }
}
