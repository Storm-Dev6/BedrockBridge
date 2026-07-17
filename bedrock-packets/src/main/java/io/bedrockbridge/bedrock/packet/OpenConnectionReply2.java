package io.bedrockbridge.bedrock.packet;

import io.bedrockbridge.bedrock.BedrockPacketIds;
import io.bedrockbridge.protocol.PacketDirection;
import io.bedrockbridge.protocol.PacketReader;
import io.bedrockbridge.protocol.PacketWriter;
import java.net.InetSocketAddress;

/** Second offline reply accepting the peer endpoint and negotiated MTU. */
public final class OpenConnectionReply2 extends AbstractBedrockHandshakePacket {
  private long serverGuid;
  private InetSocketAddress clientAddress;
  private int mtu;

  public OpenConnectionReply2() {
    this(0, new InetSocketAddress(0), 0);
  }

  public OpenConnectionReply2(long serverGuid, InetSocketAddress clientAddress, int mtu) {
    super(BedrockPacketIds.OPEN_CONNECTION_REPLY_2, PacketDirection.CLIENTBOUND);
    this.serverGuid = serverGuid;
    this.clientAddress = clientAddress;
    this.mtu = mtu;
  }

  public long serverGuid() {
    return serverGuid;
  }

  public InetSocketAddress clientAddress() {
    return clientAddress;
  }

  public int mtu() {
    return mtu;
  }

  @Override
  public void encode(PacketWriter writer) {
    writer.writeLong(serverGuid);
    BedrockAddressCodec.write(writer, clientAddress);
    writer.writeUnsignedShort(mtu);
    writer.writeBoolean(false);
  }

  @Override
  public void decode(PacketReader reader) {
    serverGuid = reader.readLong();
    clientAddress = BedrockAddressCodec.read(reader);
    mtu = reader.readUnsignedShort();
    if (reader.readBoolean()) {
      throw new IllegalArgumentException("Secure RakNet handshake is unsupported");
    }
  }
}
