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

  private static void serve(ServerSocket server) {
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
      JavaWireCodec.writePacket(socket.getOutputStream(), 0x03, new byte[0], -1);
      JavaWireCodec.readFrame(socket.getInputStream(), -1);
      JavaWireCodec.readFrame(socket.getInputStream(), -1);
      JavaWireCodec.readFrame(socket.getInputStream(), -1);
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
