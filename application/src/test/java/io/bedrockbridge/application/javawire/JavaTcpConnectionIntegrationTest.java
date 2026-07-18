package io.bedrockbridge.application.javawire;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class JavaTcpConnectionIntegrationTest {
  @Test
  void completesOfflineLoginAndConfigurationOverRealSocket() throws Exception {
    try (ServerSocket server = new ServerSocket(0)) {
      ExecutorService executor = Executors.newSingleThreadExecutor();
      Future<?> fixture = executor.submit(() -> serve(server));
      try (JavaTcpConnection connection =
          JavaTcpConnection.connect(
              "127.0.0.1", server.getLocalPort(), 2_000, 2_000, ignored -> {})) {
        JavaWirePacket.LoginSuccess success =
            connection.loginOffline("localhost", server.getLocalPort(), "Alex");
        assertEquals("Alex", success.username());
        assertEquals(JavaWireState.PLAY, connection.state());
      }
      fixture.get();
      executor.shutdownNow();
    }
  }

  @Test
  void capturesPlayPacketOrderAndStopsAtUnsupportedPacket() throws Exception {
    try (ServerSocket server = new ServerSocket(0)) {
      ExecutorService executor = Executors.newSingleThreadExecutor();
      Future<?> fixture = executor.submit(() -> serveWithPlayTrace(server));
      try (JavaTcpConnection connection =
          JavaTcpConnection.connect(
              "127.0.0.1", server.getLocalPort(), 2_000, 2_000, ignored -> {})) {
        connection.loginOffline("localhost", server.getLocalPort(), "Alex");
        JavaTcpConnection.PlayTrace trace = connection.capturePlayPacketIds(8);
        assertEquals(java.util.List.of(0x26, 0x22, 0x2B), trace.packetIds());
        assertEquals(0x2B, trace.firstUnsupportedPacketId());
      }
      fixture.get();
      executor.shutdownNow();
    }
  }

  private static void serve(ServerSocket server) {
    serve(server, false);
  }

  private static void serveWithPlayTrace(ServerSocket server) {
    serve(server, true);
  }

  private static void serve(ServerSocket server, boolean playTrace) {
    try (Socket socket = server.accept()) {
      JavaWireCodec.readFrame(socket.getInputStream(), -1);
      JavaWireCodec.readFrame(socket.getInputStream(), -1);
      UUID uuid = UUID.nameUUIDFromBytes("OfflinePlayer:Alex".getBytes(StandardCharsets.UTF_8));
      ByteArrayOutputStream success = new ByteArrayOutputStream();
      DataOutputStream data = new DataOutputStream(success);
      data.writeLong(uuid.getMostSignificantBits());
      data.writeLong(uuid.getLeastSignificantBits());
      writeString(success, "Alex");
      JavaWireCodec.writeVarInt(success, 0);
      success.write(1);
      JavaWireCodec.writePacket(socket.getOutputStream(), 0x02, success.toByteArray(), -1);

      ByteArrayOutputStream known = new ByteArrayOutputStream();
      JavaWireCodec.writeVarInt(known, 1);
      writeString(known, "minecraft");
      writeString(known, "core");
      writeString(known, "1.21");
      JavaWireCodec.writePacket(socket.getOutputStream(), 0x0E, known.toByteArray(), -1);
      ByteArrayOutputStream plugin = new ByteArrayOutputStream();
      writeString(plugin, "minecraft:brand");
      plugin.write(new byte[] {1, 2, 3});
      JavaWireCodec.writePacket(socket.getOutputStream(), 0x01, plugin.toByteArray(), -1);
      ByteArrayOutputStream registry = new ByteArrayOutputStream();
      writeString(registry, "minecraft:dimension_type");
      JavaWireCodec.writeVarInt(registry, 0);
      JavaWireCodec.writePacket(socket.getOutputStream(), 0x07, registry.toByteArray(), -1);
      ByteArrayOutputStream features = new ByteArrayOutputStream();
      JavaWireCodec.writeVarInt(features, 1);
      writeString(features, "minecraft:vanilla");
      JavaWireCodec.writePacket(socket.getOutputStream(), 0x0C, features.toByteArray(), -1);
      ByteArrayOutputStream tags = new ByteArrayOutputStream();
      JavaWireCodec.writeVarInt(tags, 0);
      JavaWireCodec.writePacket(socket.getOutputStream(), 0x0D, tags.toByteArray(), -1);
      JavaWireCodec.writePacket(socket.getOutputStream(), 0x03, new byte[0], -1);
      JavaWireCodec.readFrame(socket.getInputStream(), -1);
      JavaWireCodec.readFrame(socket.getInputStream(), -1);
      JavaWireCodec.readFrame(socket.getInputStream(), -1);
      JavaWireCodec.readFrame(socket.getInputStream(), -1);
      if (playTrace) {
        ByteArrayOutputStream keepAlive = new ByteArrayOutputStream();
        new DataOutputStream(keepAlive).writeLong(42L);
        JavaWireCodec.writePacket(socket.getOutputStream(), 0x26, keepAlive.toByteArray(), -1);
        ByteArrayOutputStream gameEvent = new ByteArrayOutputStream();
        new DataOutputStream(gameEvent).writeByte(1);
        new DataOutputStream(gameEvent).writeFloat(0.5f);
        JavaWireCodec.writePacket(socket.getOutputStream(), 0x22, gameEvent.toByteArray(), -1);
        JavaWireCodec.writePacket(socket.getOutputStream(), 0x2B, new byte[0], -1);
      }
    } catch (Exception failure) {
      throw new AssertionError(failure);
    }
  }

  private static void writeString(ByteArrayOutputStream output, String value) throws Exception {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    JavaWireCodec.writeVarInt(output, bytes.length);
    output.write(bytes);
  }
}
