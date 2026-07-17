package io.bedrockbridge.bedrock.packet;

import io.bedrockbridge.bedrock.BedrockPacketIds;
import io.bedrockbridge.protocol.PacketDirection;
import io.bedrockbridge.protocol.PacketReader;
import io.bedrockbridge.protocol.PacketWriter;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

/** Server acceptance containing peer/system addresses and synchronized timestamps. */
public final class ConnectionRequestAccepted extends AbstractBedrockHandshakePacket {
  private static final int SYSTEM_ADDRESS_COUNT = 20;
  private InetSocketAddress clientAddress;
  private int systemIndex;
  private List<InetSocketAddress> systemAddresses;
  private long requestTime;
  private long acceptedTime;

  public ConnectionRequestAccepted() {
    this(new InetSocketAddress(0), 0, unspecifiedAddresses(), 0, 0);
  }

  public ConnectionRequestAccepted(
      InetSocketAddress clientAddress,
      int systemIndex,
      List<InetSocketAddress> systemAddresses,
      long requestTime,
      long acceptedTime) {
    super(BedrockPacketIds.CONNECTION_REQUEST_ACCEPTED, PacketDirection.CLIENTBOUND);
    this.clientAddress = clientAddress;
    this.systemIndex = systemIndex;
    this.systemAddresses = normalized(systemAddresses);
    this.requestTime = requestTime;
    this.acceptedTime = acceptedTime;
  }

  public InetSocketAddress clientAddress() {
    return clientAddress;
  }

  public List<InetSocketAddress> systemAddresses() {
    return systemAddresses;
  }

  public long requestTime() {
    return requestTime;
  }

  public long acceptedTime() {
    return acceptedTime;
  }

  @Override
  public void encode(PacketWriter writer) {
    BedrockAddressCodec.write(writer, clientAddress);
    writer.writeUnsignedShort(systemIndex);
    systemAddresses.forEach(address -> BedrockAddressCodec.write(writer, address));
    writer.writeLong(requestTime);
    writer.writeLong(acceptedTime);
  }

  @Override
  public void decode(PacketReader reader) {
    clientAddress = BedrockAddressCodec.read(reader);
    systemIndex = reader.readUnsignedShort();
    List<InetSocketAddress> decoded = new ArrayList<>(SYSTEM_ADDRESS_COUNT);
    for (int index = 0; index < SYSTEM_ADDRESS_COUNT; index++) {
      decoded.add(BedrockAddressCodec.read(reader));
    }
    systemAddresses = List.copyOf(decoded);
    requestTime = reader.readLong();
    acceptedTime = reader.readLong();
  }

  private static List<InetSocketAddress> normalized(List<InetSocketAddress> addresses) {
    if (addresses.size() != SYSTEM_ADDRESS_COUNT) {
      throw new IllegalArgumentException("Exactly 20 system addresses are required");
    }
    return List.copyOf(addresses);
  }

  /** Returns the required 20-entry unspecified system-address list. */
  public static List<InetSocketAddress> unspecifiedAddresses() {
    return java.util.Collections.nCopies(SYSTEM_ADDRESS_COUNT, new InetSocketAddress(0));
  }
}
