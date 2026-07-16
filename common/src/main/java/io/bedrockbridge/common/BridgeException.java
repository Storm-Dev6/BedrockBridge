package io.bedrockbridge.common;

/** Base unchecked exception for failures that the bridge can classify and report. */
public class BridgeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** Creates a classified bridge failure. */
    public BridgeException(String message) {
        super(message);
    }

    /** Creates a classified bridge failure with its originating cause. */
    public BridgeException(String message, Throwable cause) {
        super(message, cause);
    }
}
