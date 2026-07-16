package io.bedrockbridge.common;

/** Indicates that configuration could not be loaded or violates its contract. */
public final class ConfigurationException extends BridgeException {
    private static final long serialVersionUID = 1L;

    /** Creates a configuration failure. */
    public ConfigurationException(String message) {
        super(message);
    }

    /** Creates a configuration failure with its originating cause. */
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
