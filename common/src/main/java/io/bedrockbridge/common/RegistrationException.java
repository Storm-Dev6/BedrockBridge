package io.bedrockbridge.common;

/** Indicates an invalid dependency or event registration. */
public final class RegistrationException extends BridgeException {
    private static final long serialVersionUID = 1L;

    /** Creates a registration failure. */
    public RegistrationException(String message) {
        super(message);
    }
}
