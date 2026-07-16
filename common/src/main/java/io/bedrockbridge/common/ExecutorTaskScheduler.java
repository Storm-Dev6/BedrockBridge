package io.bedrockbridge.common;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Scheduler backed by a fixed-size scheduled executor with named daemon threads. */
public final class ExecutorTaskScheduler implements TaskScheduler {
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);
    private final ScheduledExecutorService executor;

    /** Creates a scheduler with a positive worker count and thread-name prefix. */
    public ExecutorTaskScheduler(int workers, String threadNamePrefix) {
        Checks.inRange(workers, 1, 64, "workers");
        Checks.notBlank(threadNamePrefix, "threadNamePrefix");
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread =
                    new Thread(runnable, threadNamePrefix + '-' + sequence.incrementAndGet());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler(
                    (ignored, failure) -> failure.printStackTrace(System.err));
            return thread;
        };
        executor = Executors.newScheduledThreadPool(workers, factory);
    }

    @Override
    public ScheduledTask schedule(Duration delay, Runnable task) {
        validateDuration(delay, true, "delay");
        Objects.requireNonNull(task, "task");
        var future = executor.schedule(task, delay.toNanos(), TimeUnit.NANOSECONDS);
        return () -> future.cancel(false);
    }

    @Override
    public ScheduledTask scheduleAtFixedRate(
            Duration initialDelay, Duration interval, Runnable task) {
        validateDuration(initialDelay, true, "initialDelay");
        validateDuration(interval, false, "interval");
        Objects.requireNonNull(task, "task");
        var future = executor.scheduleAtFixedRate(
                task, initialDelay.toNanos(), interval.toNanos(), TimeUnit.NANOSECONDS);
        return () -> future.cancel(false);
    }

    private static void validateDuration(Duration duration, boolean zeroAllowed, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isNegative() || (!zeroAllowed && duration.isZero())) {
            throw new IllegalArgumentException(
                    name + " must be " + (zeroAllowed ? "non-negative" : "positive"));
        }
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(
                        SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new LifecycleException("Scheduler workers did not terminate");
                }
            }
        } catch (InterruptedException interrupted) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            throw new LifecycleException("Interrupted while stopping scheduler", interrupted);
        }
    }
}
