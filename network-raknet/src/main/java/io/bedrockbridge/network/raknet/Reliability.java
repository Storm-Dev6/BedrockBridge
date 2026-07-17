package io.bedrockbridge.network.raknet;

/** Delivery and ordering guarantees represented by a RakNet frame. */
public enum Reliability {
  UNRELIABLE(0, false, false, false),
  UNRELIABLE_SEQUENCED(1, false, true, true),
  RELIABLE(2, true, false, false),
  RELIABLE_ORDERED(3, true, true, false),
  RELIABLE_SEQUENCED(4, true, true, true);

  private final int protocolId;
  private final boolean reliable;
  private final boolean ordered;
  private final boolean sequenced;

  Reliability(int protocolId, boolean reliable, boolean ordered, boolean sequenced) {
    this.protocolId = protocolId;
    this.reliable = reliable;
    this.ordered = ordered;
    this.sequenced = sequenced;
  }

  /** Returns the stable three-bit RakNet wire identifier. */
  public int protocolId() {
    return protocolId;
  }

  /** Resolves a stable RakNet wire identifier. */
  public static Reliability fromProtocolId(int protocolId) {
    return switch (protocolId) {
      case 0 -> UNRELIABLE;
      case 1 -> UNRELIABLE_SEQUENCED;
      case 2 -> RELIABLE;
      case 3 -> RELIABLE_ORDERED;
      case 4 -> RELIABLE_SEQUENCED;
      default -> throw new IllegalArgumentException("Unsupported reliability id: " + protocolId);
    };
  }

  /** Returns whether loss requires retransmission. */
  public boolean isReliable() {
    return reliable;
  }

  /** Returns whether the frame carries ordering metadata. */
  public boolean isOrdered() {
    return ordered;
  }

  /** Returns whether stale frames may be superseded. */
  public boolean isSequenced() {
    return sequenced;
  }
}
