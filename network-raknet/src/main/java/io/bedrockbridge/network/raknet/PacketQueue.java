package io.bedrockbridge.network.raknet;

import java.util.ArrayDeque;
import java.util.Objects;

/** Session-confined bounded priority queue for control and gameplay frames. */
public final class PacketQueue {
    /** Packet delivery priority. */
    public enum Priority {
        CONTROL,
        GAMEPLAY,
        BULK
    }

    private final int capacity;
    private final ArrayDeque<RakNetFrame>[] queues;
    private int size;

    /** Creates a queue with a strict total packet limit. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public PacketQueue(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        queues = new ArrayDeque[Priority.values().length];
        for (int index = 0; index < queues.length; index++) {
            queues[index] = new ArrayDeque<>();
        }
    }

    /** Enqueues a packet, returning false when backpressure must be applied. */
    public boolean offer(Priority priority, RakNetFrame frame) {
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(frame, "frame");
        if (size == capacity) {
            return false;
        }
        queues[priority.ordinal()].addLast(frame);
        size++;
        return true;
    }

    /** Removes the highest-priority oldest packet, or null when empty. */
    public RakNetFrame poll() {
        for (ArrayDeque<RakNetFrame> queue : queues) {
            RakNetFrame frame = queue.pollFirst();
            if (frame != null) {
                size--;
                return frame;
            }
        }
        return null;
    }
}
