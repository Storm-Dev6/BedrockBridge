package io.bedrockbridge.network.core;

/** Wrap-aware arithmetic for RakNet's unsigned 24-bit sequence space. */
public final class UnsignedTriad {
    /** Number of distinct values in the sequence space. */
    public static final int MODULUS = 1 << 24;
    private static final int MASK = MODULUS - 1;
    private static final int HALF_RANGE = MODULUS >>> 1;

    private UnsignedTriad() {}

    /** Normalizes any integer into the unsigned 24-bit sequence space. */
    public static int normalize(int value) {
        return value & MASK;
    }

    /** Returns the next sequence value with wrap-around. */
    public static int increment(int value) {
        return normalize(value + 1);
    }

    /** Returns whether candidate is strictly newer than reference within half the sequence space. */
    public static boolean isNewer(int candidate, int reference) {
        int distance = normalize(candidate - reference);
        return distance != 0 && distance < HALF_RANGE;
    }

    /** Returns the forward distance from start to end. */
    public static int distance(int start, int end) {
        return normalize(end - start);
    }
}
