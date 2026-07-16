package io.bedrockbridge.api;

import io.bedrockbridge.common.EventBus;
import io.bedrockbridge.common.TaskScheduler;

/** Restricted stable services exposed to extensions. */
public interface ExtensionContext {
    /** Returns the process event bus. */
    EventBus eventBus();

    /** Returns the shared infrastructure scheduler. */
    TaskScheduler scheduler();
}
