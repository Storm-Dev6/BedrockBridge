package io.bedrockbridge.api;

import static org.junit.jupiter.api.Assertions.assertSame;

import io.bedrockbridge.common.DefaultEventBus;
import io.bedrockbridge.common.ExecutorTaskScheduler;
import org.junit.jupiter.api.Test;

class ExtensionContextTest {
    @Test
    void exposesStableServices() {
        var eventBus = new DefaultEventBus();
        try (var scheduler = new ExecutorTaskScheduler(1, "api-test")) {
            ExtensionContext context = new DefaultExtensionContext(eventBus, scheduler);
            assertSame(eventBus, context.eventBus());
            assertSame(scheduler, context.scheduler());
        }
    }
}
