package io.bedrockbridge.bedrock.packet;

import io.bedrockbridge.bedrock.BedrockPacketIds;
import io.bedrockbridge.bedrock.BedrockProtocol;
import io.bedrockbridge.bedrock.BedrockValidationException;
import io.bedrockbridge.protocol.PacketDirection;
import io.bedrockbridge.protocol.PacketReader;
import io.bedrockbridge.protocol.PacketWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** RakNet offline discovery response containing the Bedrock server advertisement. */
public final class UnconnectedPong extends AbstractBedrockHandshakePacket {
  private static final int MAX_MOTD_BYTES = 2_048;
  private long pingTime;
  private long serverGuid;
  private String motd;

  /** Creates an empty decoder target. */
  public UnconnectedPong() {
    this(0, 0, "");
  }

  /** Creates a discovery response with a bounded server advertisement. */
  public UnconnectedPong(long pingTime, long serverGuid, String motd) {
    super(BedrockPacketIds.UNCONNECTED_PONG, PacketDirection.CLIENTBOUND);
    this.pingTime = pingTime;
    this.serverGuid = serverGuid;
    this.motd = java.util.Objects.requireNonNull(motd, "motd");
  }

  /** Returns the echoed client timestamp. */
  public long pingTime() {
    return pingTime;
  }

  /** Returns the server GUID. */
  public long serverGuid() {
    return serverGuid;
  }

  /** Returns the semicolon-delimited Bedrock server advertisement. */
  public String motd() {
    return motd;
  }

  @Override
  public void encode(PacketWriter writer) {
    byte[] encodedMotd = motd.getBytes(StandardCharsets.UTF_8);
    if (encodedMotd.length > MAX_MOTD_BYTES) {
      throw new BedrockValidationException("Unconnected pong MOTD exceeds configured limit");
    }
    writer.writeLong(pingTime);
    writer.writeLong(serverGuid);
    writer.writeBytes(ByteBuffer.wrap(BedrockProtocol.offlineMessageMagic()));
    writer.writeUnsignedShort(encodedMotd.length);
    writer.writeBytes(ByteBuffer.wrap(encodedMotd));
  }

  @Override
  public void decode(PacketReader reader) {
    pingTime = reader.readLong();
    serverGuid = reader.readLong();
    ByteBuffer magic = reader.readSlice(BedrockProtocol.OFFLINE_MESSAGE_MAGIC_LENGTH);
    byte[] bytes = new byte[magic.remaining()];
    magic.get(bytes);
    if (!Arrays.equals(bytes, BedrockProtocol.offlineMessageMagic())) {
      throw new BedrockValidationException("Invalid unconnected pong magic");
    }
    int motdLength = reader.readUnsignedShort();
    if (motdLength > MAX_MOTD_BYTES) {
      throw new BedrockValidationException("Unconnected pong MOTD exceeds configured limit");
    }
    ByteBuffer encodedMotd = reader.readSlice(motdLength);
    byte[] motdBytes = new byte[motdLength];
    encodedMotd.get(motdBytes);
    motd = new String(motdBytes, StandardCharsets.UTF_8);
  }
}
