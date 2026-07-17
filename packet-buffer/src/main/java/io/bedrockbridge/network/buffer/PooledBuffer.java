package io.bedrockbridge.network.buffer;

import java.nio.ByteBuffer;

/** Exclusive, auto-closeable lease for a pooled byte buffer. */
public interface PooledBuffer extends AutoCloseable {
  /** Returns the exclusive buffer while this lease is open. */
  ByteBuffer buffer();

  /** Returns the buffer to its pool; repeated calls are harmless. */
  @Override
  void close();
}
