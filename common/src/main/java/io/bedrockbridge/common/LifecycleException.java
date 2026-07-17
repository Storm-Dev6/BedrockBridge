package io.bedrockbridge.common;

/** Indicates an invalid or failed application lifecycle transition. */
public final class LifecycleException extends BridgeException {
  private static final long serialVersionUID = 1L;

  /** Creates a lifecycle failure. */
  public LifecycleException(String message) {
    super(message);
  }

  /** Creates a lifecycle failure with its originating cause. */
  public LifecycleException(String message, Throwable cause) {
    super(message, cause);
  }
}
