package io.bedrockbridge.protocol.pipeline;

import io.bedrockbridge.protocol.Packet;
import java.util.Optional;

/** Ordered packet processing pipeline. */
public interface PacketPipeline {
  /** Processes a packet through an immutable stage snapshot. */
  Optional<Packet> process(Packet packet, PipelineContext context);
}
