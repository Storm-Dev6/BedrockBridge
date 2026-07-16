package io.bedrockbridge.common;

/** Idempotent event-listener registration handle. */
@FunctionalInterface
public interface Subscription extends AutoCloseable {
    /** Removes the associated listener; repeated calls have no effect. */
    @Override
    void close();
}
