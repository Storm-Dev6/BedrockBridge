package io.bedrockbridge.network.udp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.bedrockbridge.network.buffer.DirectPacketBufferPool;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class NioUdpTransportTest {
  @Test
  void sendsBetweenTwoLoopbackTransports() throws InterruptedException {
    var pool = new DirectPacketBufferPool(2048, 4);
    try (var receiver = transport(pool);
        var sender = transport(pool)) {
      CountDownLatch received = new CountDownLatch(1);
      AtomicReference<byte[]> bytes = new AtomicReference<>();
      receiver.start(
          datagram -> {
            byte[] copy = new byte[datagram.payload().remaining()];
            datagram.payload().get(copy);
            bytes.set(copy);
            received.countDown();
          });
      sender.start(ignored -> {});
      byte[] payload = {1, 2, 3};
      assertTrue(sender.send(receiver.localAddress(), ByteBuffer.wrap(payload)));
      payload[0] = 9;
      assertTrue(received.await(2, TimeUnit.SECONDS));
      assertArrayEquals(new byte[] {1, 2, 3}, bytes.get());
    } finally {
      pool.close();
    }
  }

  private static NioUdpTransport transport(DirectPacketBufferPool pool) {
    return new NioUdpTransport(
        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
        2048,
        16,
        pool,
        Clock.systemUTC());
  }
}
