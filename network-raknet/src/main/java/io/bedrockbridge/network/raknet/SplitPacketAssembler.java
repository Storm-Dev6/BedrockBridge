package io.bedrockbridge.network.raknet;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Session-confined, byte-bounded split packet reassembler with expiry. */
public final class SplitPacketAssembler {
  private final int maximumAssemblies;
  private final int maximumBytes;
  private final Duration lifetime;
  private final Map<Key, Assembly> assemblies = new HashMap<>();
  private int retainedBytes;

  /** Creates a bounded reassembler. */
  public SplitPacketAssembler(int maximumAssemblies, int maximumBytes, Duration lifetime) {
    if (maximumAssemblies < 1 || maximumBytes < 1) {
      throw new IllegalArgumentException("Assembly limits must be positive");
    }
    this.maximumAssemblies = maximumAssemblies;
    this.maximumBytes = maximumBytes;
    this.lifetime = Objects.requireNonNull(lifetime, "lifetime");
    if (lifetime.isZero() || lifetime.isNegative()) {
      throw new IllegalArgumentException("lifetime must be positive");
    }
  }

  /** Adds a fragment and returns a complete unsplit frame exactly once. */
  public Optional<RakNetFrame> add(RakNetFrame fragment, Instant now) {
    Objects.requireNonNull(fragment, "fragment");
    Objects.requireNonNull(now, "now");
    if (fragment.split() == null) {
      return Optional.of(fragment);
    }
    expire(now);
    Key key = new Key(fragment.split().id(), fragment.reliability(), fragment.orderChannel());
    Assembly assembly = assemblies.get(key);
    if (assembly == null) {
      if (assemblies.size() >= maximumAssemblies) {
        throw new IllegalStateException("Split assembly limit reached");
      }
      assembly = new Assembly(fragment.split().count(), now.plus(lifetime));
      assemblies.put(key, assembly);
    } else if (assembly.fragments.length != fragment.split().count()) {
      remove(key, assembly);
      throw new IllegalArgumentException("Conflicting split count");
    }
    int index = fragment.split().index();
    byte[] bytes = new byte[fragment.payload().remaining()];
    fragment.payload().duplicate().get(bytes);
    if (assembly.fragments[index] != null) {
      if (!java.util.Arrays.equals(assembly.fragments[index], bytes)) {
        remove(key, assembly);
        throw new IllegalArgumentException("Conflicting duplicate fragment");
      }
      return Optional.empty();
    }
    if ((long) retainedBytes + bytes.length > maximumBytes) {
      remove(key, assembly);
      throw new IllegalStateException("Split byte budget exceeded");
    }
    assembly.fragments[index] = bytes;
    assembly.received++;
    assembly.bytes += bytes.length;
    retainedBytes += bytes.length;
    if (assembly.received != assembly.fragments.length) {
      return Optional.empty();
    }
    ByteBuffer payload = ByteBuffer.allocate(assembly.bytes);
    for (byte[] part : assembly.fragments) {
      payload.put(part);
    }
    payload.flip();
    remove(key, assembly);
    return Optional.of(
        new RakNetFrame(
            fragment.reliability(),
            fragment.reliableIndex(),
            fragment.sequenceIndex(),
            fragment.orderIndex(),
            fragment.orderChannel(),
            null,
            payload));
  }

  /** Removes all expired incomplete assemblies. */
  public void expire(Instant now) {
    var iterator = assemblies.entrySet().iterator();
    while (iterator.hasNext()) {
      Assembly assembly = iterator.next().getValue();
      if (!now.isBefore(assembly.deadline)) {
        retainedBytes -= assembly.bytes;
        iterator.remove();
      }
    }
  }

  private void remove(Key key, Assembly assembly) {
    if (assemblies.remove(key, assembly)) {
      retainedBytes -= assembly.bytes;
    }
  }

  private record Key(int splitId, Reliability reliability, int channel) {}

  private static final class Assembly {
    private final byte[][] fragments;
    private final Instant deadline;
    private int received;
    private int bytes;

    private Assembly(int count, Instant deadline) {
      fragments = new byte[count][];
      this.deadline = deadline;
    }
  }
}
