package io.bedrockbridge.bedrock.packet;

import io.bedrockbridge.bedrock.BedrockPacketIds;
import io.bedrockbridge.protocol.PacketDirection;
import io.bedrockbridge.protocol.PacketReader;
import io.bedrockbridge.protocol.PacketWriter;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/** Final client confirmation that the connected transport is established. */
public final class NewIncomingConnection extends AbstractBedrockHandshakePacket {
    private InetSocketAddress serverAddress;
    private List<InetSocketAddress> systemAddresses;
    private long requestTime;
    private long acceptedTime;

    public NewIncomingConnection() {
        this(new InetSocketAddress(0), ConnectionRequestAccepted.unspecifiedAddresses(), 0, 0);
    }

    public NewIncomingConnection(
            InetSocketAddress serverAddress,
            List<InetSocketAddress> systemAddresses,
            long requestTime,
            long acceptedTime) {
        super(BedrockPacketIds.NEW_INCOMING_CONNECTION, PacketDirection.SERVERBOUND);
        if (systemAddresses.size() != 20) {
            throw new IllegalArgumentException("Exactly 20 system addresses are required");
        }
        this.serverAddress = serverAddress;
        this.systemAddresses = List.copyOf(systemAddresses);
        this.requestTime = requestTime;
        this.acceptedTime = acceptedTime;
    }

    public InetSocketAddress serverAddress() { return serverAddress; }
    public long requestTime() { return requestTime; }
    public long acceptedTime() { return acceptedTime; }

    @Override
    public void encode(PacketWriter writer) {
        BedrockAddressCodec.write(writer, serverAddress);
        systemAddresses.forEach(address -> BedrockAddressCodec.write(writer, address));
        writer.writeLong(requestTime);
        writer.writeLong(acceptedTime);
    }

    @Override
    public void decode(PacketReader reader) {
        serverAddress = BedrockAddressCodec.read(reader);
        List<InetSocketAddress> decoded = new ArrayList<>(20);
        for (int index = 0; index < 20; index++) {
            decoded.add(BedrockAddressCodec.read(reader));
        }
        systemAddresses = List.copyOf(decoded);
        requestTime = reader.readLong();
        acceptedTime = reader.readLong();
    }
}
