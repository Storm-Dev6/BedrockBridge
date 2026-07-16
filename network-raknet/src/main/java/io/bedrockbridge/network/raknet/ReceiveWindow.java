package io.bedrockbridge.network.raknet;

import io.bedrockbridge.network.core.UnsignedTriad;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/** Bounded sliding window that deduplicates reliable or datagram sequence numbers. */
public final class ReceiveWindow {
    /** Result of attempting to admit a sequence number. */
    public enum Result {
        ACCEPTED,
        DUPLICATE,
        TOO_OLD,
        TOO_FAR_AHEAD
    }

    private final int capacity;
    private final BitSet seen;
    private int base;

    /** Creates a window whose base is the first expected sequence. */
    public ReceiveWindow(int capacity, int initialBase) {
        if (capacity < 32 || capacity > 65_536) {
            throw new IllegalArgumentException("capacity must be between 32 and 65536");
        }
        this.capacity = capacity;
        this.base = UnsignedTriad.normalize(initialBase);
        seen = new BitSet(capacity);
    }

    /** Admits a sequence once and advances over a contiguous prefix. */
    public Result accept(int sequence) {
        int forward = UnsignedTriad.distance(base, sequence);
        if (forward >= UnsignedTriad.MODULUS / 2) {
            return Result.TOO_OLD;
        }
        if (forward >= capacity) {
            return Result.TOO_FAR_AHEAD;
        }
        if (seen.get(forward)) {
            return Result.DUPLICATE;
        }
        seen.set(forward);
        int contiguous = seen.nextClearBit(0);
        if (contiguous > 0) {
            shiftLeft(contiguous);
            base = UnsignedTriad.normalize(base + contiguous);
        }
        return Result.ACCEPTED;
    }

    /** Returns the next sequence not yet observed. */
    public int base() {
        return base;
    }

    /** Returns missing sequences before a future candidate, bounded by this window. */
    public List<Integer> missingBefore(int candidate) {
        int forward = UnsignedTriad.distance(base, candidate);
        if (forward >= capacity) {
            return List.of();
        }
        List<Integer> missing = new ArrayList<>();
        for (int offset = 0; offset < forward; offset++) {
            if (!seen.get(offset)) {
                missing.add(UnsignedTriad.normalize(base + offset));
            }
        }
        return List.copyOf(missing);
    }

    private void shiftLeft(int amount) {
        BitSet shifted = seen.get(amount, capacity);
        seen.clear();
        for (int bit = shifted.nextSetBit(0); bit >= 0; bit = shifted.nextSetBit(bit + 1)) {
            seen.set(bit);
        }
    }
}
