package io.bedrockbridge.registry.generator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.bedrockbridge.bedrock.BedrockProtocolLimits;
import io.bedrockbridge.bedrock.auth.BedrockChainVerifier;
import io.bedrockbridge.bedrock.codec.BedrockBinaryWriter;
import io.bedrockbridge.bedrock.codec.BedrockPacketFrame;
import io.bedrockbridge.bedrock.codec.BedrockPacketFrameCodec;
import io.bedrockbridge.bedrock.codec.BedrockPacketHeader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class LiveBedrockObserverTest {
  @Test
  void writesOnlyAnAuthenticatedThreeFieldArtifactOutsideTheWorkTree() throws Exception {
    Path temporaryDirectory = externalTempDirectory();
    Path artifact = temporaryDirectory.resolve("item-registry-748.ndjson");
    LiveBedrockObserver.PathTarget target = new LiveBedrockObserver.PathTarget(artifact);
    ItemRegistryArtifact.Summary summary =
        LiveBedrockObserver.extractAuthenticatedStartGame(
            syntheticStartGame(), true, true, target, BedrockProtocolLimits.defaults());

    assertEquals(2, summary.itemCount());
    assertTrue(summary.byteCount() > 0);
    String content = Files.readString(artifact, StandardCharsets.UTF_8);
    assertEquals(2, content.lines().count());
    assertTrue(
        content
            .lines()
            .allMatch(
                line ->
                    line.matches(
                        "\\{\\\"itemName\\\":\\\"[^\\\"]+\\\",\\\"itemId\\\":-?\\d+,\\\"componentBased\\\":(true|false)\\}")));
  }

  @Test
  void refusesArtifactUntilBothAuthenticationBoundariesAreProven() throws Exception {
    Path temporaryDirectory = externalTempDirectory();
    Path artifact = temporaryDirectory.resolve("blocked.ndjson");
    LiveBedrockObserver.PathTarget target = new LiveBedrockObserver.PathTarget(artifact);
    assertThrows(
        LiveBedrockObserver.ObserverBoundaryException.class,
        () ->
            LiveBedrockObserver.extractAuthenticatedStartGame(
                syntheticStartGame(), true, false, target, BedrockProtocolLimits.defaults()));
    assertTrue(Files.notExists(artifact));
  }

  @Test
  void loadsOnlyAnExternalP384PublicKey() throws Exception {
    Path temporaryDirectory = externalTempDirectory();
    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp384r1"));
    KeyPair pair = generator.generateKeyPair();
    Path key = temporaryDirectory.resolve("root.der");
    Files.write(key, pair.getPublic().getEncoded());

    assertArrayEquals(
        pair.getPublic().getEncoded(), ExternalPublicKeyLoader.load(key).getEncoded());
  }

  @Test
  void forwardsOpaqueRakNetDatagramsWithoutPersistingPayloads() throws Exception {
    Path temporaryDirectory = externalTempDirectory();
    KeyPair pair = p384KeyPair();
    BedrockChainVerifier verifier =
        new BedrockChainVerifier(
            Set.of(pair.getPublic()),
            new io.bedrockbridge.bedrock.auth.InMemoryReplayGuard(2),
            Clock.systemUTC(),
            Duration.ofSeconds(1));
    try (DatagramSocket upstream = new DatagramSocket(0);
        DatagramSocket client = new DatagramSocket(0)) {
      int listenPort;
      try (DatagramSocket probe = new DatagramSocket(0)) {
        listenPort = probe.getLocalPort();
      }
      LiveBedrockObserver observer =
          new LiveBedrockObserver(
              new InetSocketAddress(InetAddress.getLoopbackAddress(), listenPort),
              new InetSocketAddress(InetAddress.getLoopbackAddress(), upstream.getLocalPort()),
              verifier,
              temporaryDirectory.resolve("not-written.ndjson"),
              Duration.ofSeconds(1),
              Clock.systemUTC());
      ExecutorService executor = Executors.newSingleThreadExecutor();
      try {
        var future = executor.submit(observer::run);
        Thread.sleep(100);
        byte[] payload = {0x01, 0x02, 0x03};
        client.send(
            new DatagramPacket(
                payload,
                payload.length,
                new InetSocketAddress(InetAddress.getLoopbackAddress(), listenPort)));
        byte[] received = new byte[32];
        DatagramPacket upstreamPacket = new DatagramPacket(received, received.length);
        upstream.setSoTimeout(2_000);
        upstream.receive(upstreamPacket);
        assertArrayEquals(payload, java.util.Arrays.copyOf(received, upstreamPacket.getLength()));
        upstream.send(
            new DatagramPacket(
                payload,
                payload.length,
                new InetSocketAddress(InetAddress.getLoopbackAddress(), listenPort)));
        DatagramPacket clientPacket = new DatagramPacket(received, received.length);
        client.setSoTimeout(2_000);
        client.receive(clientPacket);
        assertArrayEquals(payload, java.util.Arrays.copyOf(received, clientPacket.getLength()));
        assertThrows(ExecutionException.class, future::get);
        assertTrue(Files.notExists(temporaryDirectory.resolve("not-written.ndjson")));
      } finally {
        observer.close();
        executor.shutdownNow();
      }
    }
  }

  private static byte[] syntheticStartGame() {
    BedrockProtocolLimits limits = BedrockProtocolLimits.defaults();
    BedrockBinaryWriter writer = new BedrockBinaryWriter(1024);
    writer.writeUnsignedVarInt(2);
    writer.writeString("minecraft:air", 512);
    writer.writeShortLE(0);
    writer.writeBoolean(false);
    writer.writeString("minecraft:stone", 512);
    writer.writeShortLE(1);
    writer.writeBoolean(false);
    writer.writeString("00000000-0000-0000-0000-000000000000", 128);
    writer.writeBoolean(false);
    writer.writeString("1.21.40.03", 128);
    writer.writeBytes(new byte[8 + 16 + 3]);
    return new BedrockPacketFrameCodec(limits)
        .encode(new BedrockPacketFrame(new BedrockPacketHeader(11, 0, 0), writer.toByteArray()));
  }

  private static Path externalTempDirectory() throws Exception {
    String temp = firstNonBlank(System.getenv("TEMP"));
    if (temp == null) {
      temp = firstNonBlank(System.getenv("TMPDIR"));
    }
    if (temp == null) {
      temp = firstNonBlank(System.getenv("TMP"));
    }
    if (temp == null) {
      temp = System.getProperty("user.home");
    }
    return Files.createTempDirectory(Path.of(temp), "bedrockbridge-observer-");
  }

  private static String firstNonBlank(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static KeyPair p384KeyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp384r1"));
    return generator.generateKeyPair();
  }
}
