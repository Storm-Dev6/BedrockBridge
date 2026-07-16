package io.bedrockbridge.bedrock.packet;

import io.bedrockbridge.bedrock.BedrockPacketIds;
import io.bedrockbridge.protocol.PacketDirection;
import io.bedrockbridge.protocol.PacketReader;
import io.bedrockbridge.protocol.PacketWriter;

/** First offline server reply selecting MTU and advertising no transport security. */
public final class OpenConnectionReply1 extends AbstractBedrockHandshakePacket {
    private long serverGuid;
    private int mtu;

    public OpenConnectionReply1() {
        this(0, 0);
    }
    public OpenConnectionReply1(long serverGuid, int mtu) {
        super(BedrockPacketIds.OPEN_CONNECTION_REPLY_1, PacketDirection.CLIENTBOUND);
        this.serverGuid = serverGuid;
        this.mtu = mtu;
    }
    public long serverGuid() { return serverGuid; }
    public int mtu() { return mtu; }
    @Override
    public void encode(PacketWriter writer) {
        writer.writeLong(serverGuid);
        writer.writeBoolean(false);
        writer.writeUnsignedShort(mtu);
    }

    @Override
    public void decode(PacketReader reader) {
        serverGuid = reader.readLong();
        if (reader.readBoolean()) {
            throw new IllegalArgumentException("Secure RakNet handshake is unsupported");
        }
        mtu = reader.readUnsignedShort();
    }
}
