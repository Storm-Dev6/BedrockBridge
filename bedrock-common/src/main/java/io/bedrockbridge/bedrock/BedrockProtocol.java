package io.bedrockbridge.bedrock;

import io.bedrockbridge.protocol.ProtocolVersion;
import java.util.Arrays;

/** Bedrock RakNet and Minecraft protocol constants supported by the bridge. */
public final class BedrockProtocol {
  private static final byte[] OFFLINE_MESSAGE_MAGIC = {
    0x00,
    (byte) 0xFF,
    (byte) 0xFF,
    0x00,
    (byte) 0xFE,
    (byte) 0xFE,
    (byte) 0xFE,
    (byte) 0xFE,
    (byte) 0xFD,
    (byte) 0xFD,
    (byte) 0xFD,
    (byte) 0xFD,
    0x12,
    0x34,
    0x56,
    0x78
  };

  /** Length of the RakNet offline-message identification sequence. */
  public static final int OFFLINE_MESSAGE_MAGIC_LENGTH = OFFLINE_MESSAGE_MAGIC.length;

  /** RakNet transport protocol used by supported Bedrock 1.21.x clients. */
  public static final int RAKNET_PROTOCOL_VERSION = 11;

  /** Framework version identity for the handshake packet catalog. */
  public static final ProtocolVersion HANDSHAKE_VERSION =
      new ProtocolVersion("bedrock-raknet", "11", RAKNET_PROTOCOL_VERSION);

  /** Minecraft Bedrock 1.21.40 network protocol published by Mojang as r/21_u4. */
  public static final int NETWORK_PROTOCOL_748 = 748;

  /** Exact framework identity for the first supported Bedrock play protocol. */
  public static final ProtocolVersion PLAY_VERSION_748 =
      new ProtocolVersion("minecraft-bedrock", "1.21.40", NETWORK_PROTOCOL_748);

  private BedrockProtocol() {}

  /** Returns a defensive copy of the offline message magic. */
  public static byte[] offlineMessageMagic() {
    return Arrays.copyOf(OFFLINE_MESSAGE_MAGIC, OFFLINE_MESSAGE_MAGIC.length);
  }
}
