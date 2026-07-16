package io.bedrockbridge.network.raknet;

import io.bedrockbridge.network.core.UnsignedTriad;

/** Inclusive range of acknowledged or missing datagram sequence numbers. */
public record AckRange(int start, int end) {
    /** Normalizes range endpoints and limits a range to half the sequence space. */
    public AckRange {
        start = UnsignedTriad.normalize(start);
        end = UnsignedTriad.normalize(end);
        if (UnsignedTriad.distance(start, end) >= UnsignedTriad.MODULUS / 2) {
            throw new IllegalArgumentException("ACK range is ambiguous");
        }
    }

    /** Returns whether this range contains the sequence using forward wrap-aware distance. */
    public boolean contains(int sequence) {
        return UnsignedTriad.distance(start, sequence) <= UnsignedTriad.distance(start, end);
    }
}
