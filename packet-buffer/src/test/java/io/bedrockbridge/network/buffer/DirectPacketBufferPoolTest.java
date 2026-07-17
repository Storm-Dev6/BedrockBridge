package io.bedrockbridge.network.buffer;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class DirectPacketBufferPoolTest {
  @Test
  void reusesClosedFixedSizeLease() {
    try (var pool = new DirectPacketBufferPool(2048, 2)) {
      PooledBuffer first = pool.acquire(100);
      ByteBuffer buffer = first.buffer();
      first.close();
      try (PooledBuffer second = pool.acquire(100)) {
        assertSame(buffer, second.buffer());
      }
      assertThrows(IllegalStateException.class, first::buffer);
    }
  }
}
