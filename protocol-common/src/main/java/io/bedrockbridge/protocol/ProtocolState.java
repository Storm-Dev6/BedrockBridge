package io.bedrockbridge.protocol;

/** Edition-neutral connection states shared by supported protocols. */
public enum ProtocolState {
  HANDSHAKE,
  STATUS,
  LOGIN,
  CONFIGURATION,
  PLAY,
  DISCONNECTED
}
