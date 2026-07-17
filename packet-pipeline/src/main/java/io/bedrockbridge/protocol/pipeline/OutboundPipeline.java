package io.bedrockbridge.protocol.pipeline;

import java.util.List;

/** Named outbound specialization of the generic packet pipeline. */
public final class OutboundPipeline extends DefaultPacketPipeline {
  /** Creates an immutable outbound stage chain. */
  public OutboundPipeline(List<PipelineStage> stages) {
    super(stages);
  }
}
