package io.bedrockbridge.bedrock.login;

/** Transport-handshake states before any Minecraft login or gameplay protocol begins. */
public enum BedrockLoginState {
  NEW,
  MTU_NEGOTIATED,
  OFFLINE_ACCEPTED,
  CONNECTION_REQUESTED,
  CONNECTED,
  DISCONNECTED
}
