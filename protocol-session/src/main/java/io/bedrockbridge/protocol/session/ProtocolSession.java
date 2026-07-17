package io.bedrockbridge.protocol.session;

import io.bedrockbridge.protocol.Packet;
import io.bedrockbridge.protocol.PacketDirection;
import io.bedrockbridge.protocol.ProtocolState;
import io.bedrockbridge.protocol.ProtocolVersion;
import io.bedrockbridge.protocol.pipeline.InboundPipeline;
import io.bedrockbridge.protocol.pipeline.OutboundPipeline;
import io.bedrockbridge.protocol.pipeline.PacketDispatcher;
import io.bedrockbridge.protocol.pipeline.PipelineContext;
import java.util.Objects;

/** Edition-neutral session joining version, state, pipelines, and dispatch. */
public final class ProtocolSession {
  private final ProtocolVersion version;
  private final ProtocolStateMachine stateMachine;
  private final InboundPipeline inbound;
  private final OutboundPipeline outbound;
  private final PacketDispatcher dispatcher;

  /** Creates a fully configured protocol session. */
  public ProtocolSession(
      ProtocolVersion version,
      ProtocolStateMachine stateMachine,
      InboundPipeline inbound,
      OutboundPipeline outbound,
      PacketDispatcher dispatcher) {
    this.version = Objects.requireNonNull(version, "version");
    this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine");
    this.inbound = Objects.requireNonNull(inbound, "inbound");
    this.outbound = Objects.requireNonNull(outbound, "outbound");
    this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
  }

  /** Processes and dispatches one serverbound packet after metadata validation. */
  public boolean receive(Packet packet) {
    validate(packet, PacketDirection.SERVERBOUND);
    PipelineContext context = context(PacketDirection.SERVERBOUND);
    return inbound
        .process(packet, context)
        .map(value -> dispatcher.dispatch(value, context))
        .orElse(false);
  }

  /** Processes one clientbound packet and returns the resulting packet or null when dropped. */
  public Packet send(Packet packet) {
    validate(packet, PacketDirection.CLIENTBOUND);
    return outbound.process(packet, context(PacketDirection.CLIENTBOUND)).orElse(null);
  }

  /** Transitions the connection state using registered rules. */
  public void transition(ProtocolState target) {
    stateMachine.transition(target);
  }

  /** Returns the current state. */
  public ProtocolState state() {
    return stateMachine.state();
  }

  private PipelineContext context(PacketDirection direction) {
    return new PipelineContext(version, stateMachine.state(), direction);
  }

  private void validate(Packet packet, PacketDirection expectedDirection) {
    Objects.requireNonNull(packet, "packet");
    if (!packet.protocolVersion().equals(version)
        || packet.state() != stateMachine.state()
        || packet.direction() != expectedDirection) {
      throw new IllegalArgumentException("Packet metadata does not match protocol session");
    }
  }
}
