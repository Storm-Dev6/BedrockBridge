package io.bedrockbridge.network.raknet;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Session-confined bounded recovery store for reliable encoded datagrams. */
public final class RecoveryQueue {
    /** Immutable datagram selected for retransmission. */
    public record Retransmission(int sequence, ByteBuffer payload, int retryCount) {
        /** Freezes the datagram payload view. */
        public Retransmission {
            payload = Objects.requireNonNull(payload, "payload").asReadOnlyBuffer();
        }
    }

    private final int maximumEntries;
    private final int maximumRetries;
    private final RttEstimator estimator;
    private final Map<Integer, Entry> entries = new HashMap<>();
    private boolean retryExhausted;

    /** Creates a recovery queue with strict entry and retry limits. */
    public RecoveryQueue(int maximumEntries, int maximumRetries, RttEstimator estimator) {
        if (maximumEntries < 1 || maximumRetries < 1) {
            throw new IllegalArgumentException("Recovery limits must be positive");
        }
        this.maximumEntries = maximumEntries;
        this.maximumRetries = maximumRetries;
        this.estimator = Objects.requireNonNull(estimator, "estimator");
    }

    /** Tracks an encoded reliable datagram until ACK or retry exhaustion. */
    public void track(int sequence, ByteBuffer payload, Instant now) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(now, "now");
        if (entries.size() >= maximumEntries || entries.containsKey(sequence)) {
            throw new IllegalStateException("Recovery queue capacity or sequence collision");
        }
        byte[] copy = new byte[payload.remaining()];
        payload.duplicate().get(copy);
        entries.put(sequence, new Entry(copy, now, now.plus(estimator.timeout())));
    }

    /** Applies an ACK and updates RTT only when the packet was never retransmitted. */
    public boolean acknowledge(int sequence, Instant now) {
        Entry entry = entries.remove(sequence);
        if (entry == null) {
            return false;
        }
        if (entry.retries == 0) {
            estimator.record(Duration.between(entry.firstSent, now));
        }
        return true;
    }

    /** Marks one sequence for immediate bounded retransmission after NACK. */
    public Optional<Retransmission> nack(int sequence, Instant now) {
        Entry entry = entries.get(sequence);
        return entry == null ? Optional.empty() : retransmit(sequence, entry, now);
    }

    /** Returns all entries whose retransmission timer has expired. */
    public List<Retransmission> due(Instant now) {
        List<Retransmission> due = new ArrayList<>();
        for (var item : List.copyOf(entries.entrySet())) {
            if (!now.isBefore(item.getValue().deadline)) {
                retransmit(item.getKey(), item.getValue(), now).ifPresent(due::add);
            }
        }
        return List.copyOf(due);
    }

    /** Returns the number of datagrams awaiting acknowledgement. */
    public int size() {
        return entries.size();
    }

    /** Returns and clears whether any datagram exhausted its retry limit. */
    public boolean consumeRetryExhausted() {
        boolean result = retryExhausted;
        retryExhausted = false;
        return result;
    }

    private Optional<Retransmission> retransmit(int sequence, Entry entry, Instant now) {
        if (++entry.retries > maximumRetries) {
            entries.remove(sequence);
            retryExhausted = true;
            return Optional.empty();
        }
        long factor = 1L << Math.min(entry.retries, 10);
        Duration delay = estimator.timeout().multipliedBy(factor);
        entry.deadline = now.plus(delay);
        return Optional.of(new Retransmission(sequence, ByteBuffer.wrap(entry.payload), entry.retries));
    }

    private static final class Entry {
        private final byte[] payload;
        private final Instant firstSent;
        private Instant deadline;
        private int retries;

        private Entry(byte[] payload, Instant firstSent, Instant deadline) {
            this.payload = payload;
            this.firstSent = firstSent;
            this.deadline = deadline;
        }
    }
}
