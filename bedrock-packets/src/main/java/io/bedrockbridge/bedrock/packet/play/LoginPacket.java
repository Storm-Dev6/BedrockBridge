package io.bedrockbridge.bedrock.packet.play;

import io.bedrockbridge.bedrock.BedrockPacketIds;
import io.bedrockbridge.protocol.PacketDirection;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/** Login network version and length-delimited binary connection request. */
public final class LoginPacket implements BedrockPlayPacket {
  private final int clientNetworkVersion;
  private final byte[] connectionRequest;

  /** Copies the authentication-bearing connection request at the packet boundary. */
  public LoginPacket(int clientNetworkVersion, byte[] connectionRequest) {
    this.clientNetworkVersion = clientNetworkVersion;
    this.connectionRequest = Objects.requireNonNull(connectionRequest, "connectionRequest").clone();
  }

  /** Returns the big-endian network version. */
  public int clientNetworkVersion() {
    return clientNetworkVersion;
  }

  /** Returns a new read-only connection request view. */
  public ByteBuffer connectionRequest() {
    return ByteBuffer.wrap(connectionRequest).asReadOnlyBuffer();
  }

  /** Returns the connection request byte count. */
  public int connectionRequestLength() {
    return connectionRequest.length;
  }

  @Override
  public int packetId() {
    return BedrockPacketIds.LOGIN;
  }

  @Override
  public PacketDirection direction() {
    return PacketDirection.SERVERBOUND;
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || (other instanceof LoginPacket packet
            && clientNetworkVersion == packet.clientNetworkVersion
            && Arrays.equals(connectionRequest, packet.connectionRequest));
  }

  @Override
  public int hashCode() {
    return 31 * Integer.hashCode(clientNetworkVersion) + Arrays.hashCode(connectionRequest);
  }

  @Override
  public String toString() {
    return "LoginPacket[clientNetworkVersion="
        + clientNetworkVersion
        + ", connectionRequestLength="
        + connectionRequest.length
        + ']';
  }
}
