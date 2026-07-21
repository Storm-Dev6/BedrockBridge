package io.bedrockbridge.bedrock.packet;

import io.bedrockbridge.bedrock.BedrockPacketIds;
import io.bedrockbridge.bedrock.BedrockProtocol;
import io.bedrockbridge.bedrock.BedrockValidationException;
import io.bedrockbridge.protocol.PacketDirection;
import io.bedrockbridge.protocol.PacketReader;
import io.bedrockbridge.protocol.PacketWriter;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** RakNet offline discovery ping sent before the connection handshake. */
public final class UnconnectedPing extends AbstractBedrockHandshakePacket {
  private long pingTime;
  private long clientGuid;

  /** Creates an empty decoder target. */
  public UnconnectedPing() {
    this(0, 0);
  }

  /** Creates a discovery ping. */
  public UnconnectedPing(long pingTime, long clientGuid) {
    super(BedrockPacketIds.UNCONNECTED_PING, PacketDirection.SERVERBOUND);
    this.pingTime = pingTime;
    this.clientGuid = clientGuid;
  }

  /** Returns the client monotonic timestamp. */
  public long pingTime() {
    return pingTime;
  }

  /** Returns the client GUID. */
  public long clientGuid() {
    return clientGuid;
  }

  @Override
  public void encode(PacketWriter writer) {
    writer.writeLong(pingTime);
    writer.writeBytes(ByteBuffer.wrap(BedrockProtocol.offlineMessageMagic()));
    writer.writeLong(clientGuid);
  }

  @Override
  public void decode(PacketReader reader) {
    pingTime = reader.readLong();
    requireMagic(reader.readSlice(BedrockProtocol.OFFLINE_MESSAGE_MAGIC_LENGTH));
    clientGuid = reader.readLong();
  }

  private static void requireMagic(ByteBuffer actual) {
    byte[] bytes = new byte[actual.remaining()];
    actual.get(bytes);
    if (!Arrays.equals(bytes, BedrockProtocol.offlineMessageMagic())) {
      throw new BedrockValidationException("Invalid unconnected ping magic");
    }
  }
}
