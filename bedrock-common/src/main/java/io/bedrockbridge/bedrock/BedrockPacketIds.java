package io.bedrockbridge.bedrock;

/** Stable RakNet and protocol-748 packet identifiers used by the Bedrock stack. */
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

  public static final int LOGIN = 1;
  public static final int PLAY_STATUS = 2;
  public static final int SERVER_TO_CLIENT_HANDSHAKE = 3;
  public static final int CLIENT_TO_SERVER_HANDSHAKE = 4;
  public static final int DISCONNECT = 5;
  public static final int RESOURCE_PACKS_INFO = 6;
  public static final int RESOURCE_PACK_STACK = 7;
  public static final int RESOURCE_PACK_CLIENT_RESPONSE = 8;
  public static final int START_GAME = 11;
  public static final int NETWORK_SETTINGS = 143;
  public static final int REQUEST_NETWORK_SETTINGS = 193;

  private BedrockPacketIds() {}
}
