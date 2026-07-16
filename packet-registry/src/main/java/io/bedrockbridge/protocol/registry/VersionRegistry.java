package io.bedrockbridge.protocol.registry;

import io.bedrockbridge.protocol.ProtocolVersion;
import java.util.Optional;

/** Semantic alias for protocol-version registration and lookup. */
public final class VersionRegistry {
    private final ProtocolRegistry protocols = new ProtocolRegistry();

    /** Registers a version. */
    public void register(ProtocolVersion version) {
        protocols.register(version);
    }

    /** Finds an exact family and protocol ID. */
    public Optional<ProtocolVersion> find(String family, int protocolId) {
        return protocols.find(family, protocolId);
    }
}
