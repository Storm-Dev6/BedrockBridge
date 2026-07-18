package io.bedrockbridge.application.javawire;

/** Malformed or unsupported Java wire data. */
public class JavaWireException extends Exception {
  private static final long serialVersionUID = 1L;

  public JavaWireException(String message) {
    super(message);
  }

  public JavaWireException(String message, Throwable cause) {
    super(message, cause);
  }
}
