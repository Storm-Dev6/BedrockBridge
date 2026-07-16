package io.bedrockbridge.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class BridgeMetricsTest {
    @Test
    void recordsLifecycleWithoutTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BridgeMetrics metrics = new BridgeMetrics(registry);
        metrics.recordStarted();
        metrics.recordStopped();
        assertEquals(1.0, registry.counter("bedrockbridge.lifecycle.starts").count());
        assertEquals(4.0, registry.get("bedrockbridge.lifecycle.state").gauge().value());
    }
}
