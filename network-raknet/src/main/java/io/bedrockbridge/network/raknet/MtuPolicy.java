package io.bedrockbridge.network.raknet;

/** Validates and negotiates a conservative per-session UDP MTU. */
public final class MtuPolicy {
    private final int minimum;
    private final int preferred;
    private final int maximum;

    /** Creates an ordered inclusive MTU policy. */
    public MtuPolicy(int minimum, int preferred, int maximum) {
        if (minimum < 576 || minimum > preferred || preferred > maximum || maximum > 65_507) {
            throw new IllegalArgumentException("Invalid MTU policy");
        }
        this.minimum = minimum;
        this.preferred = preferred;
        this.maximum = maximum;
    }

    /** Selects an MTU no larger than both the observed probe and local policy. */
    public int negotiate(int requested, int observedDatagramSize) {
        int proven = Math.min(requested, observedDatagramSize);
        if (proven < minimum) {
            throw new IllegalArgumentException("MTU probe is below the supported minimum");
        }
        return Math.min(Math.min(proven, preferred), maximum);
    }

    /** Returns the maximum configured MTU. */
    public int maximum() {
        return maximum;
    }
}
