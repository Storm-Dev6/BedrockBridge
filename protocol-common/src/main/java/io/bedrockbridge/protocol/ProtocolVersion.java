package io.bedrockbridge.protocol;

import io.bedrockbridge.common.Checks;

/** Stable identity for one wire protocol version in a named protocol family. */
public record ProtocolVersion(String family, String name, int protocolId) {
    /** Validates nonblank names and a nonnegative wire identifier. */
    public ProtocolVersion {
        family = Checks.notBlank(family, "family");
        name = Checks.notBlank(name, "name");
        if (protocolId < 0) {
            throw new IllegalArgumentException("protocolId must be nonnegative");
        }
    }
}
