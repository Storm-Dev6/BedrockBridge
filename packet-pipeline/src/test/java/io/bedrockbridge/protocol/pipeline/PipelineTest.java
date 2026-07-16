package io.bedrockbridge.protocol.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.bedrockbridge.protocol.Packet;
import io.bedrockbridge.protocol.PacketDirection;
import io.bedrockbridge.protocol.PacketReader;
import io.bedrockbridge.protocol.PacketWriter;
import io.bedrockbridge.protocol.ProtocolState;
import io.bedrockbridge.protocol.ProtocolVersion;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PipelineTest {
    @Test
    void invokesStagesAndDispatcherInOrder() {
        AtomicInteger count = new AtomicInteger();
        Packet packet = new TestPacket();
        var pipeline = new InboundPipeline(List.of(
                (value, context) -> { count.incrementAndGet(); return Optional.of(value); },
                (value, context) -> { count.incrementAndGet(); return Optional.of(value); }));
        var context = new PipelineContext(
                packet.protocolVersion(), packet.state(), packet.direction());
        Packet result = pipeline.process(packet, context).orElseThrow();
        var dispatcher = new PacketDispatcher();
        dispatcher.register(TestPacket.class, (value, ignored) -> count.incrementAndGet());
        dispatcher.dispatch(result, context);
        assertEquals(3, count.get());
    }

    private static final class TestPacket implements Packet {
        @Override public int packetId() { return 0; }
        @Override public ProtocolVersion protocolVersion() { return new ProtocolVersion("test", "1", 1); }
        @Override public ProtocolState state() { return ProtocolState.PLAY; }
        @Override public PacketDirection direction() { return PacketDirection.SERVERBOUND; }
        @Override public void encode(PacketWriter writer) {}
        @Override public void decode(PacketReader reader) {}
    }
}
