package io.bedrockbridge.protocol.registry;

import io.bedrockbridge.protocol.ProtocolVersion;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Explicit directed compatibility matrix; unknown pairs are incompatible. */
public final class CompatibilityMatrix {
  private final Set<Pair> compatible = ConcurrentHashMap.newKeySet();

  /** Declares a directed source-to-target version pair compatible. */
  public void allow(ProtocolVersion source, ProtocolVersion target) {
    compatible.add(new Pair(source, target));
  }

  /** Returns whether the exact directed pair was declared compatible. */
  public boolean isCompatible(ProtocolVersion source, ProtocolVersion target) {
    return compatible.contains(new Pair(source, target));
  }

  private record Pair(ProtocolVersion source, ProtocolVersion target) {}
}
