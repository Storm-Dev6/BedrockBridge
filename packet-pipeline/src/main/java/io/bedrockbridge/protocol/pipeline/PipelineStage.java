package io.bedrockbridge.protocol.pipeline;

import io.bedrockbridge.protocol.Packet;
import java.util.Optional;

/** One synchronous, allocation-conscious packet transformation or filtering stage. */
@FunctionalInterface
public interface PipelineStage {
  /** Returns the next packet or empty to drop processing intentionally. */
  Optional<Packet> process(Packet packet, PipelineContext context);
}
