package io.bedrockbridge.network.raknet;

/** Delivery and ordering guarantees represented by a RakNet frame. */
public enum Reliability {
    UNRELIABLE(false, false, false),
    UNRELIABLE_SEQUENCED(false, true, true),
    RELIABLE(true, false, false),
    RELIABLE_ORDERED(true, true, false),
    RELIABLE_SEQUENCED(true, true, true);

    private final boolean reliable;
    private final boolean ordered;
    private final boolean sequenced;

    Reliability(boolean reliable, boolean ordered, boolean sequenced) {
        this.reliable = reliable;
        this.ordered = ordered;
        this.sequenced = sequenced;
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
