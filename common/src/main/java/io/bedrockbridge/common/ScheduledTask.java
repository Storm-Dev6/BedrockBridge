package io.bedrockbridge.common;

/** Handle for cancelling a scheduled task. */
@FunctionalInterface
public interface ScheduledTask {
    /** Cancels execution and returns whether cancellation changed task state. */
    boolean cancel();
}
