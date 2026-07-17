package io.bedrockbridge.network.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.bedrockbridge.common.ExecutorTaskScheduler;
import io.bedrockbridge.network.core.Datagram;
import io.bedrockbridge.network.core.DatagramHandler;
import io.bedrockbridge.network.core.UdpTransport;
import io.bedrockbridge.network.raknet.OrderingChannels;
import io.bedrockbridge.network.raknet.PacketQueue;
import io.bedrockbridge.network.raknet.ReceiveWindow;
import io.bedrockbridge.network.raknet.RecoveryQueue;
import io.bedrockbridge.network.raknet.RttEstimator;
import io.bedrockbridge.network.raknet.SplitPacketAssembler;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionManagerTest {
  @Test
  void createsSessionAndTimesItOut() throws InterruptedException {
    FakeTransport transport = new FakeTransport();
    try (var scheduler = new ExecutorTaskScheduler(1, "session-test");
        var manager =
            new SessionManager(
                transport,
                (address, now) -> session(address, transport, now),
                Clock.systemUTC(),
                1000,
                scheduler,
                Duration.ofMillis(10))) {
      InetSocketAddress remote = new InetSocketAddress(InetAddress.getLoopbackAddress(), 19132);
      transport.receive(remote, ByteBuffer.wrap(new byte[] {(byte) 0x80, 0, 0, 0}));
      assertEquals(1, manager.sessions().size());
      Thread.sleep(100);
      assertTrue(manager.sessions().isEmpty());
    }
  }

  private static RakNetSession session(
      InetSocketAddress address, UdpTransport transport, Instant now) {
    return new RakNetSession(
        SessionId.create(),
        address,
        transport,
        Duration.ofMillis(30),
        Duration.ofMillis(10),
        new RecoveryQueue(16, 3, new RttEstimator(Duration.ofMillis(10), Duration.ofSeconds(1))),
        new ReceiveWindow(64, 0),
        new SplitPacketAssembler(4, 4096, Duration.ofSeconds(1)),
        new OrderingChannels(16),
        new PacketQueue(16),
        ignored -> {},
        now);
  }

  private static final class FakeTransport implements UdpTransport {
    private final List<byte[]> sent = new ArrayList<>();
    private DatagramHandler handler;

    @Override
    public void start(DatagramHandler handler) {
      this.handler = handler;
    }

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

    private void receive(InetSocketAddress remote, ByteBuffer payload) {
      handler.handle(new Datagram(remote, payload, Instant.now()));
    }
  }
}
