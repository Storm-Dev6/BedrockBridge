package io.bedrockbridge.application.upstream;

/** Typed, credential-free control messages sent to a Java upstream adapter. */
public sealed interface JavaUpstreamPacket
    permits JavaUpstreamPacket.Handshake,
        JavaUpstreamPacket.StatusRequest,
        JavaUpstreamPacket.LoginStart,
        JavaUpstreamPacket.ConfigurationAcknowledged,
        JavaUpstreamPacket.PlayReady {
  record Handshake(String host, int port, int nextState) implements JavaUpstreamPacket {}

  record StatusRequest() implements JavaUpstreamPacket {}

  record LoginStart(String username) implements JavaUpstreamPacket {}

  record ConfigurationAcknowledged() implements JavaUpstreamPacket {}

  record PlayReady() implements JavaUpstreamPacket {}
}
