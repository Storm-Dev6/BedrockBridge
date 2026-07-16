package io.bedrockbridge.api;

import io.bedrockbridge.common.EventBus;
import io.bedrockbridge.common.TaskScheduler;
import java.util.Objects;

/** Immutable implementation of the services exposed through the extension API. */
public record DefaultExtensionContext(EventBus eventBus, TaskScheduler scheduler)
        implements ExtensionContext {
    /** Validates that all stable extension services are present. */
    public DefaultExtensionContext {
        Objects.requireNonNull(eventBus, "eventBus");
        Objects.requireNonNull(scheduler, "scheduler");
    }
}
