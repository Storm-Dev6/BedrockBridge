package io.bedrockbridge.common;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** Lock-free-for-read event bus with deterministic listener ordering. */
public final class DefaultEventBus implements EventBus {
  private final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<EventListener<Object>>> listeners =
      new ConcurrentHashMap<>();

  @Override
  public <E> Subscription subscribe(Class<E> eventType, EventListener<? super E> listener) {
    Objects.requireNonNull(eventType, "eventType");
    Objects.requireNonNull(listener, "listener");
    EventListener<Object> adapted = event -> listener.onEvent(eventType.cast(event));
    CopyOnWriteArrayList<EventListener<Object>> typedListeners =
        listeners.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>());
    typedListeners.add(adapted);
    AtomicBoolean subscribed = new AtomicBoolean(true);
    return () -> {
      if (subscribed.compareAndSet(true, false)) {
        typedListeners.remove(adapted);
      }
    };
  }

  @Override
  public void publish(Object event) {
    Objects.requireNonNull(event, "event");
    for (EventListener<Object> listener :
        listeners.getOrDefault(event.getClass(), new CopyOnWriteArrayList<>())) {
      listener.onEvent(event);
    }
  }
}
