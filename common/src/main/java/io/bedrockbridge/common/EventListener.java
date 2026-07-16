package io.bedrockbridge.common;

/** Receives an immutable infrastructure event. */
@FunctionalInterface
public interface EventListener<E> {
    /** Handles an event on the publisher's thread. */
    void onEvent(E event);
}
