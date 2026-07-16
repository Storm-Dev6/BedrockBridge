package io.bedrockbridge.network.buffer;

import io.bedrockbridge.common.Checks;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Lock-minimal direct-buffer pool with a strict retained-memory bound. */
public final class DirectPacketBufferPool implements PacketBufferPool {
    private final int bufferCapacity;
    private final int maximumRetained;
    private final ConcurrentLinkedQueue<ByteBuffer> available = new ConcurrentLinkedQueue<>();
    private final AtomicInteger retained = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();

    /** Creates a pool for fixed-size direct buffers. Oversized requests are not retained. */
    public DirectPacketBufferPool(int bufferCapacity, int maximumRetained) {
        this.bufferCapacity = Checks.inRange(bufferCapacity, 512, 1 << 20, "bufferCapacity");
        this.maximumRetained = Checks.inRange(maximumRetained, 0, 65_536, "maximumRetained");
    }

    @Override
    public PooledBuffer acquire(int minimumCapacity) {
        Checks.inRange(minimumCapacity, 1, 16 << 20, "minimumCapacity");
        ByteBuffer selected;
        boolean reusable = minimumCapacity <= bufferCapacity;
        if (reusable && (selected = available.poll()) != null) {
            retained.decrementAndGet();
            selected.clear();
        } else {
            selected = ByteBuffer.allocateDirect(reusable ? bufferCapacity : minimumCapacity);
        }
        return new Lease(selected, reusable);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            available.clear();
            retained.set(0);
        }
    }

    private void release(ByteBuffer buffer, boolean reusable) {
        if (!reusable || closed.get()) {
            return;
        }
        int count = retained.incrementAndGet();
        if (count <= maximumRetained) {
            buffer.clear();
            available.offer(buffer);
        } else {
            retained.decrementAndGet();
        }
    }

    private final class Lease implements PooledBuffer {
        private final ByteBuffer buffer;
        private final boolean reusable;
        private final AtomicBoolean open = new AtomicBoolean(true);

        private Lease(ByteBuffer buffer, boolean reusable) {
            this.buffer = buffer;
            this.reusable = reusable;
        }

        @Override
        public ByteBuffer buffer() {
            if (!open.get()) {
                throw new IllegalStateException("Buffer lease is closed");
            }
            return buffer;
        }

        @Override
        public void close() {
            if (open.compareAndSet(true, false)) {
                release(buffer, reusable);
            }
        }
    }
}
