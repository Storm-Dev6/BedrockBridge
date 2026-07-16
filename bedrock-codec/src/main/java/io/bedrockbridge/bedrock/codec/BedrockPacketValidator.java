package io.bedrockbridge.bedrock.codec;

import io.bedrockbridge.bedrock.BedrockProtocol;
import io.bedrockbridge.bedrock.BedrockValidationException;
import io.bedrockbridge.bedrock.packet.*;
import io.bedrockbridge.network.raknet.MtuPolicy;
import io.bedrockbridge.protocol.Packet;

/** Central semantic validation for decoded Bedrock transport handshake packets. */
public final class BedrockPacketValidator {
    private final MtuPolicy mtuPolicy;

    /** Creates validation with the server's MTU policy. */
    public BedrockPacketValidator(MtuPolicy mtuPolicy) {
        this.mtuPolicy = java.util.Objects.requireNonNull(mtuPolicy, "mtuPolicy");
    }

    /** Rejects unsupported version, MTU, security, address, and timestamp fields. */
    public void validate(Packet packet) {
        if (!packet.protocolVersion().equals(BedrockProtocol.HANDSHAKE_VERSION)) {
            throw new BedrockValidationException("Unsupported Bedrock handshake version");
        }
        if (packet instanceof OpenConnectionRequest1 request) {
            if (request.rakNetVersion() != BedrockProtocol.RAKNET_PROTOCOL_VERSION) {
                throw new BedrockValidationException("Unsupported RakNet protocol version");
            }
            validateMtu(request.mtu());
        } else if (packet instanceof OpenConnectionRequest2 request) {
            validateMtu(request.mtu());
            if (request.clientGuid() == 0) {
                throw new BedrockValidationException("Client GUID must be nonzero");
            }
        } else if (packet instanceof ConnectionRequest request && request.security()) {
            throw new BedrockValidationException("Transport security flag is unsupported");
        }
    }

    private void validateMtu(int mtu) {
        try {
            mtuPolicy.negotiate(mtu, mtu);
        } catch (IllegalArgumentException invalid) {
            throw new BedrockValidationException("MTU is outside server policy");
        }
    }
}
