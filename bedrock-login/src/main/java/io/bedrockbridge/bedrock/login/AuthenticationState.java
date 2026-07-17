package io.bedrockbridge.bedrock.login;

/** Security-sensitive states of the Bedrock identity and encryption handshake. */
public enum AuthenticationState {
  AWAITING_LOGIN,
  AWAITING_CLIENT_HANDSHAKE,
  AUTHENTICATED,
  FAILED,
  CLOSED
}
