package io.bedrockbridge.application.upstream;

/** Minimal Java 1.21 upstream connection states owned by one bridge session. */
public enum JavaUpstreamState {
  DISCONNECTED,
  HANDSHAKING,
  STATUS,
  LOGIN,
  CONFIGURATION,
  PLAY
}
