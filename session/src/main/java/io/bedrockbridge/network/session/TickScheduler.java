package io.bedrockbridge.network.session;

import io.bedrockbridge.common.ScheduledTask;
import io.bedrockbridge.common.TaskScheduler;
import java.time.Duration;
import java.util.Objects;

/** Drives transport maintenance at a stable fixed rate. */
public final class TickScheduler implements AutoCloseable {
    private final ScheduledTask task;

    /** Starts ticking immediately at the positive requested interval. */
    public TickScheduler(TaskScheduler scheduler, Duration interval, Runnable tick) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(interval, "interval");
        Objects.requireNonNull(tick, "tick");
        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("interval must be positive");
        }
        task = scheduler.scheduleAtFixedRate(Duration.ZERO, interval, tick);
    }

    /** Cancels future ticks without interrupting a running tick. */
    @Override
    public void close() {
        task.cancel();
    }
}
