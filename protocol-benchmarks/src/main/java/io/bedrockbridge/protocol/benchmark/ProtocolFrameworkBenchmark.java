package io.bedrockbridge.protocol.benchmark;

import io.bedrockbridge.protocol.Packet;
import io.bedrockbridge.protocol.PacketDirection;
import io.bedrockbridge.protocol.PacketKey;
import io.bedrockbridge.protocol.PacketReader;
import io.bedrockbridge.protocol.PacketWriter;
import io.bedrockbridge.protocol.ProtocolState;
import io.bedrockbridge.protocol.ProtocolVersion;
import io.bedrockbridge.protocol.codec.DefaultPacketCodec;
import io.bedrockbridge.protocol.pipeline.InboundPipeline;
import io.bedrockbridge.protocol.pipeline.PipelineContext;
import io.bedrockbridge.protocol.registry.DynamicPacketRegistry;
import io.bedrockbridge.protocol.registry.PacketRegistration;
import io.bedrockbridge.protocol.session.PacketProcessor;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** JMH throughput benchmarks for encode, decode, registry lookup, and pipeline processing. */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
public class ProtocolFrameworkBenchmark {
  private static final ProtocolVersion VERSION = new ProtocolVersion("benchmark", "1", 1);
  private final DynamicPacketRegistry registry = new DynamicPacketRegistry();
  private final BenchmarkPacket packet = new BenchmarkPacket();
  private final ByteBuffer encoded = ByteBuffer.allocate(16);
  private final InboundPipeline pipeline =
      new InboundPipeline(List.of((value, context) -> Optional.of(value)));
  private final PipelineContext context =
      new PipelineContext(VERSION, ProtocolState.PLAY, PacketDirection.SERVERBOUND);
  private PacketProcessor processor;

  /** Initializes the registry and canonical encoded fixture before measurements. */
  @Setup(Level.Trial)
  public void setup() {
    registry.register(
        new PacketRegistration<>(
            new PacketKey(VERSION, ProtocolState.PLAY, PacketDirection.SERVERBOUND, 1),
            BenchmarkPacket.class,
            new DefaultPacketCodec<>(),
            BenchmarkPacket::new));
    processor = new PacketProcessor(registry);
    processor.encode(packet, encoded);
    encoded.flip();
  }

  /** Measures packet field encoding operations per second. */
  @Benchmark
  public int encode() {
    ByteBuffer output = ByteBuffer.allocate(16);
    return processor.encode(packet, output);
  }

  /** Measures packet factory and decoding operations per second. */
  @Benchmark
  public Packet decode() {
    return processor.decode(
        VERSION, ProtocolState.PLAY, PacketDirection.SERVERBOUND, 1, encoded.duplicate());
  }

  /** Measures complete-key registry lookups per second. */
  @Benchmark
  public Object registryLookup() {
    return registry.find(
        new PacketKey(VERSION, ProtocolState.PLAY, PacketDirection.SERVERBOUND, 1));
  }

  /** Measures a single-stage packet pipeline throughput. */
  @Benchmark
  public Object pipelineThroughput() {
    return pipeline.process(packet, context);
  }

  /** Minimal edition-neutral benchmark fixture packet. */
  public static final class BenchmarkPacket implements Packet {
    private int value = 42;

    @Override
    public int packetId() {
      return 1;
    }

    @Override
    public ProtocolVersion protocolVersion() {
      return VERSION;
    }

    @Override
    public ProtocolState state() {
      return ProtocolState.PLAY;
    }

    @Override
    public PacketDirection direction() {
      return PacketDirection.SERVERBOUND;
    }

    @Override
    public void encode(PacketWriter writer) {
      writer.writeInt(value);
    }

    @Override
    public void decode(PacketReader reader) {
      value = reader.readInt();
    }
  }
}
