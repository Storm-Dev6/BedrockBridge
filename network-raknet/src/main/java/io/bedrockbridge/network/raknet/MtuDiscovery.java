package io.bedrockbridge.network.raknet;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.OptionalInt;

/** Session-confined binary-search MTU discovery with bounded attempts and probe timeout. */
public final class MtuDiscovery {
  private final Duration probeTimeout;
  private final int maximumAttempts;
  private int lowerBound;
  private int upperBound;
  private int attempts;
  private int pending;
  private Instant deadline;

  /** Creates discovery over an inclusive valid MTU range. */
  public MtuDiscovery(int minimum, int maximum, int maximumAttempts, Duration probeTimeout) {
    if (minimum < 576 || maximum < minimum || maximum > 65_507 || maximumAttempts < 1) {
      throw new IllegalArgumentException("Invalid MTU discovery limits");
    }
    this.lowerBound = minimum;
    this.upperBound = maximum;
    this.maximumAttempts = maximumAttempts;
    this.probeTimeout = Objects.requireNonNull(probeTimeout, "probeTimeout");
    if (probeTimeout.isZero() || probeTimeout.isNegative()) {
      throw new IllegalArgumentException("probeTimeout must be positive");
    }
  }

  /** Returns the next probe size or empty when discovery has converged or exhausted attempts. */
  public OptionalInt nextProbe(Instant now) {
    Objects.requireNonNull(now, "now");
    if (pending != 0) {
      if (now.isBefore(deadline)) {
        return OptionalInt.empty();
      }
      upperBound = pending - 1;
      pending = 0;
    }
    if (lowerBound >= upperBound || attempts >= maximumAttempts) {
      return OptionalInt.empty();
    }
    pending = lowerBound + Math.ceilDiv(upperBound - lowerBound, 2);
    attempts++;
    deadline = now.plus(probeTimeout);
    return OptionalInt.of(pending);
  }

  /** Marks the outstanding probe as successfully observed by the peer. */
  public void acknowledge(int observedSize) {
    if (pending == 0 || observedSize != pending) {
      throw new IllegalArgumentException("MTU acknowledgement does not match pending probe");
    }
    lowerBound = pending;
    pending = 0;
  }

  /** Returns the largest MTU proven so far. */
  public int discoveredMtu() {
    return lowerBound;
  }
}
