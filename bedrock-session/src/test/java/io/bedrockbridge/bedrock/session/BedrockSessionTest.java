package io.bedrockbridge.bedrock.session;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.bedrockbridge.bedrock.codec.BedrockDatagramCodec;
import io.bedrockbridge.bedrock.codec.BedrockPacketRegistry;
import io.bedrockbridge.bedrock.codec.BedrockPacketValidator;
import io.bedrockbridge.bedrock.login.BedrockLoginState;
import io.bedrockbridge.bedrock.login.BedrockLoginStateMachine;
import io.bedrockbridge.bedrock.login.ProtocolVersionNegotiator;
import io.bedrockbridge.bedrock.packet.ConnectionRequest;
import io.bedrockbridge.bedrock.packet.NewIncomingConnection;
import io.bedrockbridge.bedrock.packet.OpenConnectionRequest1;
import io.bedrockbridge.bedrock.packet.OpenConnectionRequest2;
import io.bedrockbridge.bedrock.packet.UnconnectedPing;
import io.bedrockbridge.bedrock.packet.UnconnectedPong;
import io.bedrockbridge.network.core.DatagramHandler;
import io.bedrockbridge.network.core.UdpTransport;
import io.bedrockbridge.network.raknet.MtuPolicy;
import io.bedrockbridge.network.raknet.RakNetFrame;
import io.bedrockbridge.network.raknet.RakNetFrameCodec;
import io.bedrockbridge.network.raknet.Reliability;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BedrockSessionTest {
  @Test
  void bootstrapsFirstRequestAndTimesOut() {
    var codec =
        new BedrockDatagramCodec(
            BedrockPacketRegistry.create(),
            new BedrockPacketValidator(new MtuPolicy(576, 1492, 1492)));
    InetSocketAddress remote = new InetSocketAddress(InetAddress.getLoopbackAddress(), 19132);
    List<ByteBuffer> sent = new ArrayList<>();
    var session =
        new BedrockSession(
            remote,
            codec,
            new BedrockLoginStateMachine(7, remote, new ProtocolVersionNegotiator()),
            Duration.ofSeconds(10),
            sent::add,
            Instant.EPOCH);
    ByteBuffer request = ByteBuffer.allocate(1200);
    codec.encode(new OpenConnectionRequest1(11, 1200), request);
    request.flip();
    session.receive(request, Instant.EPOCH);
    assertFalse(sent.isEmpty());
    assertEquals(BedrockLoginState.MTU_NEGOTIATED, session.state());
    session.tick(Instant.EPOCH.plusSeconds(10));
    assertEquals(BedrockLoginState.DISCONNECTED, session.state());
  }

  @Test
  void repliesToUnconnectedDiscoveryPingWithoutAdvancingLoginState() {
    var codec =
        new BedrockDatagramCodec(
            BedrockPacketRegistry.create(),
            new BedrockPacketValidator(new MtuPolicy(576, 1492, 1492)));
    InetSocketAddress remote = new InetSocketAddress(InetAddress.getLoopbackAddress(), 19132);
    List<ByteBuffer> sent = new ArrayList<>();
    var session =
        new BedrockSession(
            remote,
            codec,
            new BedrockLoginStateMachine(Long.MIN_VALUE, remote, new ProtocolVersionNegotiator()),
            Duration.ofSeconds(10),
            sent::add,
            Instant.EPOCH);

    ByteBuffer request = ByteBuffer.allocate(128);
    codec.encode(new UnconnectedPing(1234, 55), request);
    request.flip();
    session.receive(request, Instant.EPOCH);

    assertEquals(BedrockLoginState.NEW, session.state());
    assertEquals(1, sent.size());
    ByteBuffer response = sent.getFirst().duplicate();
    assertEquals(0x1C, Byte.toUnsignedInt(response.get(response.position())));
    UnconnectedPong pong =
        (UnconnectedPong)
            codec.decode(response, io.bedrockbridge.protocol.PacketDirection.CLIENTBOUND);
    assertEquals(1234, pong.pingTime());
    assertEquals(Long.MIN_VALUE, pong.serverGuid());
    assertEquals(
        "MCPE;BedrockBridge;1001;1.26.33;0;100;9223372036854775808;"
            + "BedrockBridge;Survival;1;19132;19133;0;1;0;",
        pong.motd());
  }

  @Test
  void routesConnectedRakNetDataToPlayHandlerAndFlushesAck() {
    var codec =
        new BedrockDatagramCodec(
            BedrockPacketRegistry.create(),
            new BedrockPacketValidator(new MtuPolicy(576, 1492, 1492)));
    InetSocketAddress remote = new InetSocketAddress(InetAddress.getLoopbackAddress(), 19132);
    FakeTransport transport = new FakeTransport();
    List<byte[]> received = new ArrayList<>();
    var session =
        new BedrockSession(
            remote,
            codec,
            new BedrockLoginStateMachine(7, remote, new ProtocolVersionNegotiator()),
            Duration.ofSeconds(10),
            payload -> {},
            transport,
            (payload, outbound) -> {
              byte[] bytes = new byte[payload.remaining()];
              payload.duplicate().get(bytes);
              received.add(bytes);
              outbound.accept(ByteBuffer.wrap(new byte[3_000]));
            },
            Instant.EPOCH);

    receive(session, codec, new OpenConnectionRequest1(11, 1200));
    receive(
        session,
        codec,
        new OpenConnectionRequest2(
            new InetSocketAddress(InetAddress.getLoopbackAddress(), 19132), 1200, 55));
    receive(session, codec, new ConnectionRequest(55, 1, false));
    receive(
        session,
        codec,
        new NewIncomingConnection(remote, NewIncomingConnectionTestAddresses.addresses(), 1, 2));

    ByteBuffer datagram = ByteBuffer.allocate(128);
    datagram.put((byte) 0x80);
    RakNetFrameCodec.putTriad(datagram, 0);
    new RakNetFrameCodec()
        .encode(
            new RakNetFrame(
                Reliability.RELIABLE_ORDERED,
                0,
                0,
                0,
                0,
                null,
                ByteBuffer.wrap(new byte[] {(byte) 0xFE, 0x01, 0x02})),
            datagram);
    datagram.flip();
    session.receive(datagram, Instant.EPOCH.plusMillis(1));

    assertEquals(BedrockLoginState.CONNECTED, session.state());
    assertEquals(1, received.size());
    assertArrayEquals(new byte[] {(byte) 0xFE, 0x01, 0x02}, received.getFirst());
    assertFalse(transport.sent.isEmpty());
    assertTrue(transport.sent.stream().anyMatch(value -> Byte.toUnsignedInt(value[0]) == 0xC0));
    assertTrue(
        transport.sent.stream().filter(value -> Byte.toUnsignedInt(value[0]) == 0x80).count() >= 3);
  }

  @Test
  void acceptsConnectionHandshakeInsideRakNetDataBeforePlayState() {
    var codec =
        new BedrockDatagramCodec(
            BedrockPacketRegistry.create(),
            new BedrockPacketValidator(new MtuPolicy(576, 1492, 1492)));
    InetSocketAddress remote = new InetSocketAddress(InetAddress.getLoopbackAddress(), 19132);
    FakeTransport transport = new FakeTransport();
    List<byte[]> received = new ArrayList<>();
    var session =
        new BedrockSession(
            remote,
            codec,
            new BedrockLoginStateMachine(7, remote, new ProtocolVersionNegotiator()),
            Duration.ofSeconds(10),
            payload -> {},
            transport,
            (payload, outbound) -> {
              byte[] bytes = new byte[payload.remaining()];
              payload.duplicate().get(bytes);
              received.add(bytes);
            },
            Instant.EPOCH);

    receive(session, codec, new OpenConnectionRequest1(11, 1200));
    receive(
        session,
        codec,
        new OpenConnectionRequest2(
            new InetSocketAddress(InetAddress.getLoopbackAddress(), 19132), 1200, 55));
    session.receive(
        framed(codec, new ConnectionRequest(55, 1, false), 0, 0), Instant.EPOCH.plusMillis(1));
    assertEquals(BedrockLoginState.CONNECTION_REQUESTED, session.state());
    assertTrue(transport.sent.stream().anyMatch(value -> Byte.toUnsignedInt(value[0]) == 0x80));

    session.receive(
        framed(
            codec,
            new NewIncomingConnection(remote, NewIncomingConnectionTestAddresses.addresses(), 1, 2),
            1,
            1),
        Instant.EPOCH.plusMillis(2));
    assertEquals(BedrockLoginState.CONNECTED, session.state());

    ByteBuffer play = ByteBuffer.allocate(128);
    play.put((byte) 0x80);
    RakNetFrameCodec.putTriad(play, 2);
    new RakNetFrameCodec()
        .encode(
            new RakNetFrame(
                Reliability.RELIABLE_ORDERED,
                2,
                0,
                2,
                0,
                null,
                ByteBuffer.wrap(new byte[] {(byte) 0xFE, 0x01})),
            play);
    play.flip();
    session.receive(play, Instant.EPOCH.plusMillis(3));
    assertEquals(1, received.size());
    assertArrayEquals(new byte[] {(byte) 0xFE, 0x01}, received.getFirst());
  }

  private static void receive(BedrockSession session, BedrockDatagramCodec codec, Object packet) {
    ByteBuffer buffer = ByteBuffer.allocate(1500);
    codec.encode((io.bedrockbridge.protocol.Packet) packet, buffer);
    buffer.flip();
    session.receive(buffer, Instant.EPOCH);
  }

  private static ByteBuffer framed(
      BedrockDatagramCodec codec, Object packet, int datagramSequence, int frameIndex) {
    ByteBuffer payload = ByteBuffer.allocate(1500);
    codec.encode((io.bedrockbridge.protocol.Packet) packet, payload);
    payload.flip();
    ByteBuffer datagram = ByteBuffer.allocate(1500);
    datagram.put((byte) 0x80);
    RakNetFrameCodec.putTriad(datagram, datagramSequence);
    new RakNetFrameCodec()
        .encode(
            new RakNetFrame(
                Reliability.RELIABLE_ORDERED, frameIndex, 0, frameIndex, 0, null, payload),
            datagram);
    datagram.flip();
    return datagram;
  }

  private static final class NewIncomingConnectionTestAddresses {
    private static List<InetSocketAddress> addresses() {
      List<InetSocketAddress> addresses = new ArrayList<>();
      for (int index = 0; index < 20; index++) {
        addresses.add(new InetSocketAddress(InetAddress.getLoopbackAddress(), 19132));
      }
      return addresses;
    }
  }

  private static final class FakeTransport implements UdpTransport {
    private final List<byte[]> sent = new ArrayList<>();

    @Override
    public void start(DatagramHandler handler) {}

    @Override
    public boolean send(InetSocketAddress remoteAddress, ByteBuffer payload) {
      byte[] bytes = new byte[payload.remaining()];
      payload.duplicate().get(bytes);
      sent.add(bytes);
      return true;
    }

    @Override
    public InetSocketAddress localAddress() {
      return new InetSocketAddress(InetAddress.getLoopbackAddress(), 19133);
    }

    @Override
    public void close() {}
  }
}
