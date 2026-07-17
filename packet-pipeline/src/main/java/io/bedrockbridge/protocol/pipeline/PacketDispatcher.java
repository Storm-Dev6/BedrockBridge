package io.bedrockbridge.protocol.pipeline;

import io.bedrockbridge.common.RegistrationException;
import io.bedrockbridge.protocol.Packet;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/** Dynamic, switch-free exact-type packet handler dispatcher. */
public final class PacketDispatcher {
  private final ConcurrentHashMap<Class<? extends Packet>, BiConsumer<Packet, PipelineContext>>
      handlers = new ConcurrentHashMap<>();

  /** Registers exactly one handler for a packet class. */
  public <T extends Packet> void register(
      Class<T> type, BiConsumer<? super T, PipelineContext> handler) {
    BiConsumer<Packet, PipelineContext> adapted =
        (packet, context) -> handler.accept(type.cast(packet), context);
    if (handlers.putIfAbsent(type, adapted) != null) {
      throw new RegistrationException("Handler already registered: " + type.getName());
    }
  }

  /** Dispatches to an exact-type handler and reports whether one existed. */
  public boolean dispatch(Packet packet, PipelineContext context) {
    return Optional.ofNullable(handlers.get(packet.getClass()))
        .map(
            handler -> {
              handler.accept(packet, context);
              return true;
            })
        .orElse(false);
  }
}
