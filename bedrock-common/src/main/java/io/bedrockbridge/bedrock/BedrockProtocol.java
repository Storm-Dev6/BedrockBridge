package io.bedrockbridge.bedrock;

import io.bedrockbridge.protocol.ProtocolVersion;
import java.util.Arrays;

/** Bedrock RakNet handshake constants and supported transport version. */
public final class BedrockProtocol {
    /** RakNet offline-message identification sequence. */
    public static final byte[] OFFLINE_MESSAGE_MAGIC = {
        0x00, (byte) 0xFF, (byte) 0xFF, 0x00,
        (byte) 0xFE, (byte) 0xFE, (byte) 0xFE, (byte) 0xFE,
        (byte) 0xFD, (byte) 0xFD, (byte) 0xFD, (byte) 0xFD,
        0x12, 0x34, 0x56, 0x78
    };

    /** RakNet transport protocol used by supported Bedrock 1.21.x clients. */
    public static final int RAKNET_PROTOCOL_VERSION = 11;

    /** Framework version identity for the handshake packet catalog. */
    public static final ProtocolVersion HANDSHAKE_VERSION =
            new ProtocolVersion("bedrock-raknet", "11", RAKNET_PROTOCOL_VERSION);

    private BedrockProtocol() {}

    /** Returns a defensive copy of the offline message magic. */
    public static byte[] offlineMessageMagic() {
        return Arrays.copyOf(OFFLINE_MESSAGE_MAGIC, OFFLINE_MESSAGE_MAGIC.length);
    }
}
