package io.bedrockbridge.application.javawire;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Blocking Java TCP transport for the supported offline login/configuration boundary. */
public final class JavaTcpConnection implements Closeable {
  private final Socket socket;
  private final Consumer<String> eventSink;
  private final java.io.InputStream input;
  private final java.io.OutputStream output;
  private int compressionThreshold = -1;
  private JavaWireState state = JavaWireState.HANDSHAKING;

  private JavaTcpConnection(Socket socket, int readTimeoutMillis, Consumer<String> eventSink)
      throws IOException {
    this.socket = socket;
    this.eventSink = eventSink;
    socket.setSoTimeout(readTimeoutMillis);
    input = socket.getInputStream();
    output = socket.getOutputStream();
  }

  public static JavaTcpConnection connect(
      String host,
      int port,
      int connectTimeoutMillis,
      int readTimeoutMillis,
      Consumer<String> eventSink)
      throws IOException {
    if (connectTimeoutMillis < 1 || readTimeoutMillis < 1) {
      throw new IllegalArgumentException("timeouts must be positive");
    }
    Socket socket = new Socket();
    socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
    return new JavaTcpConnection(
        socket, readTimeoutMillis, eventSink == null ? ignored -> {} : eventSink);
  }

  public JavaWireState state() {
    return state;
  }

  public JavaWirePacket.StatusResponse requestStatus(String host, int port)
      throws IOException, JavaWireException {
    send(
        0x00,
        new JavaWirePacket.Handshake(JavaWireCodec.PROTOCOL_1_21_1, host, port, 1),
        JavaWireState.HANDSHAKING);
    state = JavaWireState.STATUS;
    send(0x00, new JavaWirePacket.StatusRequest(), state);
    JavaWirePacket response = receive();
    if (!(response instanceof JavaWirePacket.StatusResponse status)) {
      throw new JavaWireException("expected Java status response, got " + response);
    }
    send(0x01, new JavaWirePacket.Ping(System.currentTimeMillis()), state);
    JavaWirePacket pong = receive();
    if (!(pong instanceof JavaWirePacket.Pong)) {
      throw new JavaWireException("expected Java status pong, got " + pong);
    }
    return status;
  }

  public JavaWirePacket.LoginSuccess loginOffline(String host, int port, String username)
      throws IOException, JavaWireException {
    if (username == null || username.isBlank() || username.length() > 16) {
      throw new IllegalArgumentException("username must be 1-16 characters");
    }
    send(0x00, new JavaWirePacket.Handshake(JavaWireCodec.PROTOCOL_1_21_1, host, port, 2), state);
    state = JavaWireState.LOGIN;
    UUID uuid =
        UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    send(0x00, new JavaWirePacket.LoginStart(username, uuid), state);
    while (true) {
      JavaWirePacket packet = receive();
      if (packet instanceof JavaWirePacket.SetCompression compression) {
        if (compression.threshold() < 0) {
          throw new JavaWireException("Java server supplied invalid compression threshold");
        }
        compressionThreshold = compression.threshold();
        eventSink.accept("java compression enabled threshold=" + compressionThreshold);
      } else if (packet instanceof JavaWirePacket.LoginSuccess success) {
        send(0x03, new JavaWirePacket.LoginAcknowledged(), state);
        state = JavaWireState.CONFIGURATION;
        return configuration(success);
      } else if (packet instanceof JavaWirePacket.Disconnect disconnect) {
        throw new JavaUpstreamDisconnect(disconnect.reasonJson());
      } else {
        throw new JavaWireException("unsupported Java login packet: " + packet);
      }
    }
  }

  private JavaWirePacket.LoginSuccess configuration(JavaWirePacket.LoginSuccess success)
      throws IOException, JavaWireException {
    send(
        0x00,
        new JavaWirePacket.ClientInformation("en_US", 10, 0, true, 0x7F, 1, false, true),
        state);
    while (true) {
      JavaWirePacket packet = receive();
      if (packet instanceof JavaWirePacket.KnownPacks knownPacks) {
        send(0x07, knownPacks, state);
      } else if (packet instanceof JavaWirePacket.RegistryData registryData) {
        validateRegistryData(registryData);
        eventSink.accept(
            "java configuration registry="
                + registryData.registryId()
                + " entries="
                + registryData.entries().size());
      } else if (packet instanceof JavaWirePacket.FeatureFlags featureFlags) {
        validateIdentifiers(featureFlags.flags(), 0x0C, "feature flag");
        eventSink.accept("java configuration feature-flags=" + featureFlags.flags().size());
      } else if (packet instanceof JavaWirePacket.UpdateTags updateTags) {
        validateTags(updateTags);
        eventSink.accept("java configuration tag-registries=" + updateTags.registries().size());
      } else if (packet instanceof JavaWirePacket.ConfigurationPluginMessage pluginMessage) {
        validateIdentifier(pluginMessage.channel(), 0x01, "plugin channel");
        eventSink.accept(
            "java configuration plugin-channel="
                + pluginMessage.channel()
                + " bytes="
                + pluginMessage.payloadBytes());
      } else if (packet instanceof JavaWirePacket.KeepAlive keepAlive) {
        send(0x04, new JavaWirePacket.KeepAlive(keepAlive.payload()), state);
      } else if (packet instanceof JavaWirePacket.FinishConfiguration) {
        send(0x03, new JavaWirePacket.AcknowledgeFinishConfiguration(), state);
        state = JavaWireState.PLAY;
        return success;
      } else if (packet instanceof JavaWirePacket.Disconnect disconnect) {
        throw new JavaUpstreamDisconnect(disconnect.reasonJson());
      } else {
        throw new JavaWireException("unsupported Java configuration packet: " + packet);
      }
    }
  }

  private static void validateRegistryData(JavaWirePacket.RegistryData registryData)
      throws JavaWireException {
    validateIdentifier(registryData.registryId(), 0x07, "registry");
    for (JavaWirePacket.RegistryEntry entry : registryData.entries()) {
      validateIdentifier(entry.entryId(), 0x07, "registry entry");
      if (entry.data() instanceof JavaNbt.End) {
        throw unsupported(0x07, "registry entry has an end-only NBT payload");
      }
    }
  }

  private static void validateTags(JavaWirePacket.UpdateTags updateTags) throws JavaWireException {
    for (JavaWirePacket.RegistryTags registry : updateTags.registries()) {
      validateIdentifier(registry.registryId(), 0x0D, "tag registry");
      for (JavaWirePacket.Tag tag : registry.tags()) {
        validateIdentifier(tag.name(), 0x0D, "tag");
        if (tag.entries().stream().anyMatch(entry -> entry < 0)) {
          throw unsupported(0x0D, "tag entry id is negative");
        }
      }
    }
  }

  private static void validateIdentifiers(List<String> identifiers, int packetId, String label)
      throws JavaWireException {
    for (String identifier : identifiers) {
      validateIdentifier(identifier, packetId, label);
    }
  }

  private static void validateIdentifier(String identifier, int packetId, String label)
      throws JavaWireException {
    if (identifier == null || !identifier.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
      throw unsupported(packetId, "invalid " + label + " identifier");
    }
  }

  private static JavaWireException unsupported(int packetId, String detail) {
    return new JavaWireException(
        "unsupported Java configuration packet id=0x"
            + Integer.toHexString(packetId)
            + ": "
            + detail);
  }

  private void send(int packetId, JavaWirePacket packet, JavaWireState packetState)
      throws IOException, JavaWireException {
    byte[] fields = JavaWireCodec.encode(packet, packetState);
    JavaWireCodec.writePacket(output, packetId, fields, compressionThreshold);
    eventSink.accept("java send state=" + packetState + " id=0x" + Integer.toHexString(packetId));
  }

  private JavaWirePacket receive() throws IOException, JavaWireException {
    JavaWireCodec.Frame frame = JavaWireCodec.readFrame(input, compressionThreshold);
    JavaWirePacket packet = JavaWireCodec.decode(state, frame.packetId(), frame.fields());
    eventSink.accept("java recv state=" + state + " id=0x" + Integer.toHexString(frame.packetId()));
    return packet;
  }

  @Override
  public void close() throws IOException {
    state = JavaWireState.CLOSED;
    socket.close();
  }
}
