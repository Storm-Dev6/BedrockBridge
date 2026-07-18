package io.bedrockbridge.application.javawire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Opt-in manual test against a user-provided local Java 1.21.1/Paper server. */
@EnabledIfEnvironmentVariable(named = "BEDROCKBRIDGE_REAL_JAVA", matches = "true")
class JavaRealServerManualTest {
  @Test
  void reachesPlayAgainstLocalOfflineServer() throws Exception {
    String host = System.getenv().getOrDefault("BEDROCKBRIDGE_JAVA_HOST", "127.0.0.1");
    int port = Integer.parseInt(System.getenv().getOrDefault("BEDROCKBRIDGE_JAVA_PORT", "25565"));
    try (JavaTcpConnection connection =
        JavaTcpConnection.connect(host, port, 5_000, 15_000, ignored -> {})) {
      JavaWirePacket.LoginSuccess success = connection.loginOffline(host, port, "BedrockBridge");
      assertEquals("BedrockBridge", success.username());
      assertEquals(JavaWireState.PLAY, connection.state());
      int traceLimit =
          Integer.parseInt(System.getenv().getOrDefault("BEDROCKBRIDGE_TRACE_LIMIT", "10"));
      JavaTcpConnection.PlayTrace trace = connection.capturePlayPacketIdsForTrace(traceLimit);
      assertFalse(trace.packetIds().isEmpty());
      System.out.println(
          "Paper PLAY trace ids="
              + trace.packetIds()
              + " firstUnsupported="
              + trace.firstUnsupportedPacketId()
              + " reason="
              + trace.firstUnsupportedReason());
    }
  }
}
