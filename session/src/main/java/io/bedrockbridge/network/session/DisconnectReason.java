package io.bedrockbridge.network.session;

/** Classified transport-level session termination reason. */
public enum DisconnectReason {
  CLIENT_REQUEST,
  SERVER_SHUTDOWN,
  TIMEOUT,
  RETRY_EXHAUSTED,
  PROTOCOL_ERROR
}
