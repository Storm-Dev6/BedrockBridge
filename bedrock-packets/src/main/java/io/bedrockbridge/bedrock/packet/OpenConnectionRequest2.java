package io.bedrockbridge.bedrock.packet;

import io.bedrockbridge.bedrock.BedrockPacketIds;
import io.bedrockbridge.protocol.PacketDirection;
import io.bedrockbridge.protocol.PacketReader;
import io.bedrockbridge.protocol.PacketWriter;
import java.net.InetSocketAddress;

/** Second offline request proving server address and client identity. */
public final class OpenConnectionRequest2 extends AbstractBedrockHandshakePacket {
    private InetSocketAddress serverAddress;
    private int mtu;
    private long clientGuid;

    public OpenConnectionRequest2() {
        this(new InetSocketAddress(0), 0, 0);
    }
    public OpenConnectionRequest2(InetSocketAddress serverAddress, int mtu, long clientGuid) {
        super(BedrockPacketIds.OPEN_CONNECTION_REQUEST_2, PacketDirection.SERVERBOUND);
        this.serverAddress = serverAddress;
        this.mtu = mtu;
        this.clientGuid = clientGuid;
    }
    public InetSocketAddress serverAddress() { return serverAddress; }
    public int mtu() { return mtu; }
    public long clientGuid() { return clientGuid; }
    @Override
    public void encode(PacketWriter writer) {
        BedrockAddressCodec.write(writer, serverAddress);
        writer.writeUnsignedShort(mtu);
        writer.writeLong(clientGuid);
    }

    @Override
    public void decode(PacketReader reader) {
        serverAddress = BedrockAddressCodec.read(reader);
        mtu = reader.readUnsignedShort();
        clientGuid = reader.readLong();
    }
}
