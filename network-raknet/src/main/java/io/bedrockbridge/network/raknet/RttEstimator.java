package io.bedrockbridge.network.raknet;

import java.time.Duration;

/** Session-confined Jacobson/Karels RTT estimator with bounded retransmission timeout. */
public final class RttEstimator {
  private final long minimumNanos;
  private final long maximumNanos;
  private long smoothedNanos;
  private long variationNanos;
  private boolean initialized;

  /** Creates an estimator with positive minimum and maximum timeouts. */
  public RttEstimator(Duration minimum, Duration maximum) {
    minimumNanos = positive(minimum, "minimum");
    maximumNanos = positive(maximum, "maximum");
    if (minimumNanos > maximumNanos) {
      throw new IllegalArgumentException("minimum must not exceed maximum");
    }
  }

  /** Adds a non-retransmitted RTT sample. */
  public void record(Duration sample) {
    long sampleNanos = positive(sample, "sample");
    if (!initialized) {
      smoothedNanos = sampleNanos;
      variationNanos = sampleNanos / 2;
      initialized = true;
    } else {
      long error = Math.abs(smoothedNanos - sampleNanos);
      variationNanos = (3 * variationNanos + error) / 4;
      smoothedNanos = (7 * smoothedNanos + sampleNanos) / 8;
    }
  }

  /** Returns the bounded current retransmission timeout. */
  public Duration timeout() {
    long estimated = initialized ? smoothedNanos + 4 * variationNanos : minimumNanos;
    return Duration.ofNanos(Math.clamp(estimated, minimumNanos, maximumNanos));
  }

  private static long positive(Duration duration, String name) {
    if (duration == null || duration.isNegative() || duration.isZero()) {
      throw new IllegalArgumentException(name + " must be positive");
    }
    return duration.toNanos();
  }
}
