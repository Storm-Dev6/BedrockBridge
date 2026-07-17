package io.bedrockbridge.network.buffer;

/** Provides bounded, reusable direct buffers for the network pipeline. */
public interface PacketBufferPool extends AutoCloseable {
  /** Borrows a buffer with at least the requested capacity. */
  PooledBuffer acquire(int minimumCapacity);

  /** Releases all cached buffers; outstanding leases remain valid until closed. */
  @Override
  void close();
}
