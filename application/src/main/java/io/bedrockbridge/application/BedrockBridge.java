package io.bedrockbridge.application;

import io.bedrockbridge.api.BridgeStartedEvent;
import io.bedrockbridge.api.BridgeStoppingEvent;
import io.bedrockbridge.common.EventBus;
import io.bedrockbridge.common.LifecycleException;
import io.bedrockbridge.common.TaskScheduler;
import io.bedrockbridge.config.BridgeConfiguration;
import io.bedrockbridge.observability.BridgeMetrics;
import io.bedrockbridge.observability.Logging;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;

/** Owns the deterministic startup and shutdown of all Phase 1 infrastructure services. */
public final class BedrockBridge implements AutoCloseable {
  private static final Logger LOGGER = Logging.logger(BedrockBridge.class);
  private final BridgeConfiguration configuration;
  private final EventBus eventBus;
  private final TaskScheduler scheduler;
  private final BridgeMetrics metrics;
  private final Clock clock;
  private final AtomicReference<LifecycleState> state = new AtomicReference<>(LifecycleState.NEW);
  private final CountDownLatch termination = new CountDownLatch(1);

  /** Creates a bridge from fully validated, explicitly injected dependencies. */
  public BedrockBridge(
      BridgeConfiguration configuration,
      EventBus eventBus,
      TaskScheduler scheduler,
      BridgeMetrics metrics,
      Clock clock) {
    this.configuration = Objects.requireNonNull(configuration, "configuration");
    this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    this.metrics = Objects.requireNonNull(metrics, "metrics");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Starts infrastructure exactly once and publishes the completion event. */
  public void start() {
    if (!state.compareAndSet(LifecycleState.NEW, LifecycleState.STARTING)) {
      throw new LifecycleException(
          "Bridge can only start from NEW; current state is " + state.get());
    }
    metrics.lifecycleState(1);
    try {
      state.set(LifecycleState.RUNNING);
      metrics.recordStarted();
      eventBus.publish(new BridgeStartedEvent(Instant.now(clock)));
      LOGGER.info("{} infrastructure started", configuration.applicationName());
    } catch (RuntimeException failure) {
      state.set(LifecycleState.FAILED);
      metrics.lifecycleState(5);
      scheduler.close();
      throw new LifecycleException("Bridge startup failed", failure);
    }
  }

  /** Returns the current lock-free lifecycle snapshot. */
  public LifecycleState state() {
    return state.get();
  }

  /** Blocks the caller until shutdown completes while preserving interruption semantics. */
  public void awaitTermination() throws InterruptedException {
    termination.await();
  }

  /** Stops running or failed infrastructure exactly once; subsequent calls are no-ops. */
  @Override
  public void close() {
    while (true) {
      LifecycleState current = state.get();
      if (current == LifecycleState.STOPPED) {
        return;
      }
      if (current == LifecycleState.STOPPING) {
        awaitConcurrentShutdown();
        return;
      }
      if (current != LifecycleState.RUNNING && current != LifecycleState.FAILED) {
        throw new LifecycleException("Bridge cannot stop from " + current);
      }
      if (state.compareAndSet(current, LifecycleState.STOPPING)) {
        break;
      }
    }
    metrics.lifecycleState(3);
    RuntimeException failure = null;
    try {
      eventBus.publish(new BridgeStoppingEvent(Instant.now(clock)));
    } catch (RuntimeException listenerFailure) {
      failure = listenerFailure;
      LOGGER.error("A bridge stopping listener failed", listenerFailure);
    }
    try {
      scheduler.close();
    } catch (RuntimeException schedulerFailure) {
      if (failure == null) {
        failure = schedulerFailure;
      } else {
        failure.addSuppressed(schedulerFailure);
      }
    } finally {
      state.set(LifecycleState.STOPPED);
      metrics.recordStopped();
      termination.countDown();
      LOGGER.info("{} infrastructure stopped", configuration.applicationName());
    }
    if (failure != null) {
      throw new LifecycleException("Bridge shutdown completed with failures", failure);
    }
  }

  private void awaitConcurrentShutdown() {
    boolean interrupted = false;
    while (true) {
      try {
        termination.await();
        break;
      } catch (InterruptedException ignored) {
        interrupted = true;
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }
}
