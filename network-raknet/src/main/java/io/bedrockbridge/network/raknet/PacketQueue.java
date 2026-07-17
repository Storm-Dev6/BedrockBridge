package io.bedrockbridge.network.raknet;

import java.util.ArrayDeque;
import java.util.EnumMap;
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
  private final EnumMap<Priority, ArrayDeque<RakNetFrame>> queues = new EnumMap<>(Priority.class);
  private int size;

  /** Creates a queue with a strict total packet limit. */
  public PacketQueue(int capacity) {
    if (capacity < 1) {
      throw new IllegalArgumentException("capacity must be positive");
    }
    this.capacity = capacity;
    for (Priority priority : Priority.values()) {
      queues.put(priority, new ArrayDeque<>());
    }
  }

  /** Enqueues a packet, returning false when backpressure must be applied. */
  public boolean offer(Priority priority, RakNetFrame frame) {
    Objects.requireNonNull(priority, "priority");
    Objects.requireNonNull(frame, "frame");
    if (size == capacity) {
      return false;
    }
    queues.get(priority).addLast(frame);
    size++;
    return true;
  }

  /** Removes the highest-priority oldest packet, or null when empty. */
  public RakNetFrame poll() {
    for (ArrayDeque<RakNetFrame> queue : queues.values()) {
      RakNetFrame frame = queue.pollFirst();
      if (frame != null) {
        size--;
        return frame;
      }
    }
    return null;
  }
}
