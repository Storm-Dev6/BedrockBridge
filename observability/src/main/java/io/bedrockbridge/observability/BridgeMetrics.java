package io.bedrockbridge.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/** Stable infrastructure metrics with bounded tag cardinality. */
public final class BridgeMetrics {
  private final Counter starts;
  private final Counter stops;
  private final AtomicInteger lifecycleState = new AtomicInteger();

  /** Registers the bridge's foundational meters in the supplied registry. */
  public BridgeMetrics(MeterRegistry registry) {
    Objects.requireNonNull(registry, "registry");
    starts = Counter.builder("bedrockbridge.lifecycle.starts").register(registry);
    stops = Counter.builder("bedrockbridge.lifecycle.stops").register(registry);
    Gauge.builder("bedrockbridge.lifecycle.state", lifecycleState, AtomicInteger::get)
        .description(
            "Lifecycle ordinal: 0 new, 1 starting, 2 running, 3 stopping, 4 stopped, 5 failed")
        .register(registry);
  }

  /** Records one successful transition into running state. */
  public void recordStarted() {
    starts.increment();
    lifecycleState.set(2);
  }

  /** Records one successful transition into stopped state. */
  public void recordStopped() {
    stops.increment();
    lifecycleState.set(4);
  }

  /** Reflects an intermediate or failed lifecycle ordinal without adding unbounded labels. */
  public void lifecycleState(int state) {
    lifecycleState.set(state);
  }
}
