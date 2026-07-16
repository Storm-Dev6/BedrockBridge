package io.bedrockbridge.protocol.pipeline;

import java.util.List;

/** Named inbound specialization of the generic packet pipeline. */
public final class InboundPipeline extends DefaultPacketPipeline {
    /** Creates an immutable inbound stage chain. */
    public InboundPipeline(List<PipelineStage> stages) { super(stages); }
}
