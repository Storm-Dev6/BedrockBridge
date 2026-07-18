package io.bedrockbridge.application.javawire;

import java.util.UUID;

/** Typed packets used by the small, offline-compatible Java vertical slice. */
public sealed interface JavaWirePacket
    permits JavaWirePacket.Handshake,
        JavaWirePacket.StatusRequest,
        JavaWirePacket.StatusResponse,
        JavaWirePacket.Ping,
        JavaWirePacket.Pong,
        JavaWirePacket.LoginStart,
        JavaWirePacket.LoginSuccess,
        JavaWirePacket.SetCompression,
        JavaWirePacket.LoginAcknowledged,
        JavaWirePacket.Disconnect,
        JavaWirePacket.KeepAlive,
        JavaWirePacket.KnownPacks,
        JavaWirePacket.FinishConfiguration,
        JavaWirePacket.AcknowledgeFinishConfiguration {
  record Handshake(int protocolVersion, String host, int port, int nextState)
      implements JavaWirePacket {}

  record StatusRequest() implements JavaWirePacket {}

  record StatusResponse(String json) implements JavaWirePacket {}

  record Ping(long payload) implements JavaWirePacket {}

  record Pong(long payload) implements JavaWirePacket {}

  record LoginStart(String username, UUID uuid) implements JavaWirePacket {}

  record LoginSuccess(UUID uuid, String username, boolean strictErrorHandling)
      implements JavaWirePacket {}

  record SetCompression(int threshold) implements JavaWirePacket {}

  record LoginAcknowledged() implements JavaWirePacket {}

  record Disconnect(String reasonJson) implements JavaWirePacket {}

  record KeepAlive(long payload) implements JavaWirePacket {}

  record KnownPacks(java.util.List<KnownPack> packs) implements JavaWirePacket {
    public KnownPacks {
      packs = java.util.List.copyOf(packs);
    }
  }

  record KnownPack(String namespace, String id, String version) {}

  record FinishConfiguration() implements JavaWirePacket {}

  record AcknowledgeFinishConfiguration() implements JavaWirePacket {}
}
