package io.bedrockbridge.application.javawire;

/** Java protocol connection state. */
public enum JavaWireState {
  HANDSHAKING,
  STATUS,
  LOGIN,
  CONFIGURATION,
  PLAY,
  CLOSED
}
