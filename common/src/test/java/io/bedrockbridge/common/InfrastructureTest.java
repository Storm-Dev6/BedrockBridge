package io.bedrockbridge.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InfrastructureTest {
    @Test
    void serviceContainerRejectsDuplicateContracts() {
        ServiceContainer container = new ServiceContainer().register(CharSequence.class, "bridge");
        assertEquals("bridge", container.require(CharSequence.class));
        assertThrows(
                RegistrationException.class,
                () -> container.register(CharSequence.class, "other"));
    }

    @Test
    void eventSubscriptionIsOrderedAndClosable() {
        EventBus bus = new DefaultEventBus();
        AtomicInteger result = new AtomicInteger();
        Subscription first = bus.subscribe(String.class, ignored -> result.compareAndSet(0, 1));
        bus.subscribe(String.class, ignored -> result.compareAndSet(1, 2));
        bus.publish("event");
        assertEquals(2, result.get());
        first.close();
        first.close();
    }

    @Test
    void schedulerExecutesAndCancelsTasks() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try (TaskScheduler scheduler = new ExecutorTaskScheduler(1, "test-scheduler")) {
            ScheduledTask task = scheduler.schedule(Duration.ZERO, latch::countDown);
            assertTrue(latch.await(2, TimeUnit.SECONDS));
            task.cancel();
        }
    }
}
