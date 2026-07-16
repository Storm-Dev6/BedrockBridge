package io.bedrockbridge.network.session;

/** Transport-level RakNet session lifecycle states. */
public enum SessionState {
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    DISCONNECTED
}
