package io.bedrockbridge.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.bedrockbridge.api.BridgeStartedEvent;
import io.bedrockbridge.common.DefaultEventBus;
import io.bedrockbridge.common.ExecutorTaskScheduler;
import io.bedrockbridge.common.LifecycleException;
import io.bedrockbridge.config.BridgeConfiguration;
import io.bedrockbridge.observability.BridgeMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class BedrockBridgeTest {
    @Test
    void startsPublishesAndStopsDeterministically() {
        DefaultEventBus eventBus = new DefaultEventBus();
        AtomicReference<Instant> started = new AtomicReference<>();
        eventBus.subscribe(BridgeStartedEvent.class, event -> started.set(event.occurredAt()));
        var scheduler = new ExecutorTaskScheduler(1, "lifecycle-test");
        Instant now = Instant.parse("2026-07-16T00:00:00Z");
        var bridge = new BedrockBridge(
                configuration(),
                eventBus,
                scheduler,
                new BridgeMetrics(new SimpleMeterRegistry()),
                Clock.fixed(now, ZoneOffset.UTC));
        bridge.start();
        assertEquals(LifecycleState.RUNNING, bridge.state());
        assertEquals(now, started.get());
        bridge.close();
        bridge.close();
        assertEquals(LifecycleState.STOPPED, bridge.state());
    }

    @Test
    void rejectsSecondStart() {
        var bridge = BridgeLauncher.create(configuration());
        bridge.start();
        assertThrows(LifecycleException.class, bridge::start);
        bridge.close();
    }

    @Test
    void listenerFailureStillCompletesShutdown() {
        DefaultEventBus eventBus = new DefaultEventBus();
        eventBus.subscribe(
                io.bedrockbridge.api.BridgeStoppingEvent.class,
                ignored -> {
                    throw new IllegalStateException("listener failure");
                });
        var bridge = new BedrockBridge(
                configuration(),
                eventBus,
                new ExecutorTaskScheduler(1, "failure-test"),
                new BridgeMetrics(new SimpleMeterRegistry()),
                Clock.systemUTC());
        bridge.start();
        assertThrows(LifecycleException.class, bridge::close);
        assertEquals(LifecycleState.STOPPED, bridge.state());
    }

    private static BridgeConfiguration configuration() {
        return new BridgeConfiguration(
                "bridge", "0.0.0.0", 19132, "localhost", 25565, 100, 1, false);
    }
}
