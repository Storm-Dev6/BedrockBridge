package io.bedrockbridge.common;

import java.time.Duration;

/** Schedules bounded infrastructure work outside network event loops. */
public interface TaskScheduler extends AutoCloseable {
    /** Schedules one execution after the specified non-negative delay. */
    ScheduledTask schedule(Duration delay, Runnable task);

    /** Schedules repeated execution with a positive interval. */
    ScheduledTask scheduleAtFixedRate(Duration initialDelay, Duration interval, Runnable task);

    /** Stops accepting tasks and waits for an orderly shutdown. */
    @Override
    void close();
}
