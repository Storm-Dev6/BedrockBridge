package io.bedrockbridge.common;

/** Synchronous, type-safe process event dispatcher. */
public interface EventBus {
    /** Registers a listener and returns a handle that removes it. */
    <E> Subscription subscribe(Class<E> eventType, EventListener<? super E> listener);

    /** Delivers an event to the event's exact registered type in registration order. */
    void publish(Object event);
}
