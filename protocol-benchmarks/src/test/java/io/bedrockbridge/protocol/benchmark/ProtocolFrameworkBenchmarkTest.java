package io.bedrockbridge.protocol.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class ProtocolFrameworkBenchmarkTest {
  @Test
  void benchmarkFixtureExercisesProductionPaths() {
    var benchmark = new ProtocolFrameworkBenchmark();
    benchmark.setup();
    assertEquals(Integer.BYTES, benchmark.encode());
    assertNotNull(benchmark.decode());
    assertNotNull(benchmark.registryLookup());
    assertNotNull(benchmark.pipelineThroughput());
  }
}
