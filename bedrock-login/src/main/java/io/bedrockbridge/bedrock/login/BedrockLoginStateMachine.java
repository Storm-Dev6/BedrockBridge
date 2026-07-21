package io.bedrockbridge.bedrock.login;

import io.bedrockbridge.bedrock.BedrockProtocol;
import io.bedrockbridge.bedrock.BedrockValidationException;
import io.bedrockbridge.bedrock.packet.ConnectedPing;
import io.bedrockbridge.bedrock.packet.ConnectedPong;
import io.bedrockbridge.bedrock.packet.ConnectionRequest;
import io.bedrockbridge.bedrock.packet.ConnectionRequestAccepted;
import io.bedrockbridge.bedrock.packet.DisconnectNotification;
import io.bedrockbridge.bedrock.packet.NewIncomingConnection;
import io.bedrockbridge.bedrock.packet.OpenConnectionReply1;
import io.bedrockbridge.bedrock.packet.OpenConnectionReply2;
import io.bedrockbridge.bedrock.packet.OpenConnectionRequest1;
import io.bedrockbridge.bedrock.packet.OpenConnectionRequest2;
import io.bedrockbridge.bedrock.packet.UnconnectedPing;
import io.bedrockbridge.bedrock.packet.UnconnectedPong;
import io.bedrockbridge.protocol.Packet;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Optional;

/** Session-confined state machine for the RakNet portion of a Bedrock connection. */
public final class BedrockLoginStateMachine {
  private final long serverGuid;
  private final InetSocketAddress clientAddress;
  private final ProtocolVersionNegotiator versions;
  private BedrockLoginState state = BedrockLoginState.NEW;
  private int mtu;
  private long clientGuid;

  /** Creates a handshake machine for one peer endpoint. */
  public BedrockLoginStateMachine(
      long serverGuid, InetSocketAddress clientAddress, ProtocolVersionNegotiator versions) {
    this.serverGuid = serverGuid;
    this.clientAddress = java.util.Objects.requireNonNull(clientAddress, "clientAddress");
    this.versions = java.util.Objects.requireNonNull(versions, "versions");
  }

  /** Applies one legal serverbound packet and returns the immediate reply when required. */
  public Optional<Packet> handle(Packet packet, Instant now) {
    if (packet instanceof UnconnectedPing ping) {
      require(BedrockLoginState.NEW);
      return Optional.of(
          new UnconnectedPong(
              ping.pingTime(),
              serverGuid,
              "MCPE;BedrockBridge;"
                  + BedrockProtocol.PREFERRED_PLAY_VERSION.protocolId()
                  + ';'
                  + BedrockProtocol.PREFERRED_PLAY_VERSION.name()
                  + ";0;100;"
                  + serverGuid
                  + ";BedrockBridge;Survival;1;19132;19133;"));
    }
    if (packet instanceof OpenConnectionRequest1 request) {
      require(BedrockLoginState.NEW);
      versions.negotiate(request.rakNetVersion());
      mtu = request.mtu();
      state = BedrockLoginState.MTU_NEGOTIATED;
      return Optional.of(new OpenConnectionReply1(serverGuid, mtu));
    }
    if (packet instanceof OpenConnectionRequest2 request) {
      require(BedrockLoginState.MTU_NEGOTIATED);
      if (request.mtu() > mtu) {
        throw new BedrockValidationException("Second request attempted to increase MTU");
      }
      mtu = request.mtu();
      clientGuid = request.clientGuid();
      state = BedrockLoginState.OFFLINE_ACCEPTED;
      return Optional.of(new OpenConnectionReply2(serverGuid, clientAddress, mtu));
    }
    if (packet instanceof ConnectionRequest request) {
      require(BedrockLoginState.OFFLINE_ACCEPTED);
      if (request.clientGuid() != clientGuid) {
        throw new BedrockValidationException("Connection request GUID changed");
      }
      state = BedrockLoginState.CONNECTION_REQUESTED;
      return Optional.of(
          new ConnectionRequestAccepted(
              clientAddress,
              0,
              ConnectionRequestAccepted.unspecifiedAddresses(),
              request.requestTime(),
              now.toEpochMilli()));
    }
    if (packet instanceof NewIncomingConnection) {
      require(BedrockLoginState.CONNECTION_REQUESTED);
      state = BedrockLoginState.CONNECTED;
      return Optional.empty();
    }
    if (packet instanceof ConnectedPing ping) {
      require(BedrockLoginState.CONNECTED);
      return Optional.of(new ConnectedPong(ping.pingTime(), now.toEpochMilli()));
    }
    if (packet instanceof DisconnectNotification) {
      state = BedrockLoginState.DISCONNECTED;
      return Optional.empty();
    }
    throw new BedrockValidationException("Packet is not valid for login state: " + state);
  }

  /** Marks this machine disconnected regardless of its current state. */
  public void disconnect() {
    state = BedrockLoginState.DISCONNECTED;
  }

  /** Returns the current state. */
  public BedrockLoginState state() {
    return state;
  }

  /** Returns the negotiated MTU after request 1. */
  public int mtu() {
    return mtu;
  }

  private void require(BedrockLoginState required) {
    if (state != required) {
      throw new BedrockValidationException(
          "Expected login state " + required + " but was " + state);
    }
  }
}
