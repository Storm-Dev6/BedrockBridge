package io.bedrockbridge.bedrock;

/** Stable RakNet handshake packet identifiers used by the Bedrock transport bootstrap. */
public final class BedrockPacketIds {
    public static final int CONNECTED_PING = 0x00;
    public static final int CONNECTED_PONG = 0x03;
    public static final int OPEN_CONNECTION_REQUEST_1 = 0x05;
    public static final int OPEN_CONNECTION_REPLY_1 = 0x06;
    public static final int OPEN_CONNECTION_REQUEST_2 = 0x07;
    public static final int OPEN_CONNECTION_REPLY_2 = 0x08;
    public static final int CONNECTION_REQUEST = 0x09;
    public static final int CONNECTION_REQUEST_ACCEPTED = 0x10;
    public static final int NEW_INCOMING_CONNECTION = 0x13;
    public static final int DISCONNECT_NOTIFICATION = 0x15;

    private BedrockPacketIds() {}
}
