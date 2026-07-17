package io.bedrockbridge.bedrock.packet;

import io.bedrockbridge.bedrock.BedrockPacketIds;
import io.bedrockbridge.protocol.PacketDirection;
import io.bedrockbridge.protocol.PacketReader;
import io.bedrockbridge.protocol.PacketWriter;

/** First offline connection request negotiating RakNet version and observed MTU. */
public final class OpenConnectionRequest1 extends AbstractBedrockHandshakePacket {
  private int rakNetVersion;
  private int mtu;

  /** Creates an empty decoder target. */
  public OpenConnectionRequest1() {
    this(0, 0);
  }

  /** Creates a request with protocol version and datagram-derived MTU. */
  public OpenConnectionRequest1(int rakNetVersion, int mtu) {
    super(BedrockPacketIds.OPEN_CONNECTION_REQUEST_1, PacketDirection.SERVERBOUND);
    this.rakNetVersion = rakNetVersion;
    this.mtu = mtu;
  }

  public int rakNetVersion() {
    return rakNetVersion;
  }

  public int mtu() {
    return mtu;
  }

  @Override
  public void encode(PacketWriter writer) {
    writer.writeByte((byte) rakNetVersion);
    int padding = mtu - 18;
    if (padding < 0) {
      throw new IllegalArgumentException("MTU is below request header size");
    }
    writer.writeBytes(java.nio.ByteBuffer.allocate(padding));
  }

  @Override
  public void decode(PacketReader reader) {
    rakNetVersion = Byte.toUnsignedInt(reader.readByte());
    mtu = reader.remaining() + 18;
    reader.readSlice(reader.remaining());
  }
}
