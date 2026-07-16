package io.bedrockbridge.protocol.codec;

import io.bedrockbridge.network.buffer.PacketBufferPool;
import io.bedrockbridge.network.buffer.PooledBuffer;
import java.util.Objects;

/** Allocates bounded pooled buffers for packet encoding and framing. */
public final class PacketAllocator {
    private final PacketBufferPool pool;
    private final int maximumPacketSize;

    /** Creates an allocator with a strict maximum packet size. */
    public PacketAllocator(PacketBufferPool pool, int maximumPacketSize) {
        this.pool = Objects.requireNonNull(pool, "pool");
        if (maximumPacketSize < 1) {
            throw new IllegalArgumentException("maximumPacketSize must be positive");
        }
        this.maximumPacketSize = maximumPacketSize;
    }

    /** Allocates a lease when the requested capacity is within policy. */
    public PooledBuffer allocate(int capacity) {
        if (capacity < 1 || capacity > maximumPacketSize) {
            throw new IllegalArgumentException("Packet capacity exceeds configured limit");
        }
        return pool.acquire(capacity);
    }
}
