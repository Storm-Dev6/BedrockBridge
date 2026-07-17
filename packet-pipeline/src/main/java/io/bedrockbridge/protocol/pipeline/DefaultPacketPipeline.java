package io.bedrockbridge.protocol.pipeline;

import io.bedrockbridge.protocol.Packet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable, thread-safe packet pipeline. */
public class DefaultPacketPipeline implements PacketPipeline {
  private final List<PipelineStage> stages;

  /** Freezes stages in invocation order. */
  public DefaultPacketPipeline(List<PipelineStage> stages) {
    this.stages = List.copyOf(stages);
  }

  @Override
  public Optional<Packet> process(Packet packet, PipelineContext context) {
    Packet current = Objects.requireNonNull(packet, "packet");
    Objects.requireNonNull(context, "context");
    for (PipelineStage stage : stages) {
      Optional<Packet> next = stage.process(current, context);
      if (next.isEmpty()) {
        return Optional.empty();
      }
      current = next.orElseThrow();
    }
    return Optional.of(current);
  }
}
