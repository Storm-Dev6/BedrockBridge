package io.bedrockbridge.bedrock.login;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.bedrockbridge.bedrock.packet.ConnectionRequest;
import io.bedrockbridge.bedrock.packet.ConnectionRequestAccepted;
import io.bedrockbridge.bedrock.packet.NewIncomingConnection;
import io.bedrockbridge.bedrock.packet.OpenConnectionReply1;
import io.bedrockbridge.bedrock.packet.OpenConnectionReply2;
import io.bedrockbridge.bedrock.packet.OpenConnectionRequest1;
import io.bedrockbridge.bedrock.packet.OpenConnectionRequest2;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BedrockLoginStateMachineTest {
  @Test
  void completesTransportHandshakeInOrder() {
    var machine =
        new BedrockLoginStateMachine(
            7,
            new InetSocketAddress(InetAddress.getLoopbackAddress(), 19132),
            new ProtocolVersionNegotiator());
    assertInstanceOf(
        OpenConnectionReply1.class,
        machine.handle(new OpenConnectionRequest1(11, 1200), Instant.EPOCH).orElseThrow());
    assertInstanceOf(
        OpenConnectionReply2.class,
        machine
            .handle(
                new OpenConnectionRequest2(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 19132), 1200, 9),
                Instant.EPOCH)
            .orElseThrow());
    assertInstanceOf(
        ConnectionRequestAccepted.class,
        machine.handle(new ConnectionRequest(9, 10, false), Instant.EPOCH).orElseThrow());
    machine.handle(new NewIncomingConnection(), Instant.EPOCH);
    assertEquals(BedrockLoginState.CONNECTED, machine.state());
  }
}
