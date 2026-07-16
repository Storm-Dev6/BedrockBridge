package io.bedrockbridge.network.raknet;

import java.nio.ByteBuffer;
import java.util.Objects;

/** Immutable logical RakNet frame before datagram batching or after reassembly. */
public record RakNetFrame(
        Reliability reliability,
        int reliableIndex,
        int sequenceIndex,
        int orderIndex,
        int orderChannel,
        SplitInfo split,
        ByteBuffer payload) {
    /** Validates frame metadata and freezes its payload view. */
    public RakNetFrame {
        Objects.requireNonNull(reliability, "reliability");
        if (orderChannel < 0 || orderChannel > 31) {
            throw new IllegalArgumentException("orderChannel must be between 0 and 31");
        }
        payload = Objects.requireNonNull(payload, "payload").asReadOnlyBuffer();
    }

    /** Fragment identity and bounds carried by a split frame. */
    public record SplitInfo(int count, int id, int index) {
        /** Validates an individual fragment descriptor. */
        public SplitInfo {
            if (count < 2 || count > 4096 || index < 0 || index >= count) {
                throw new IllegalArgumentException("Invalid split packet descriptor");
            }
        }
    }
}
