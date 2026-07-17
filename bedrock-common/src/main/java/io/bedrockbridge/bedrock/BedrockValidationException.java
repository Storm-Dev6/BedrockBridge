package io.bedrockbridge.bedrock;

import io.bedrockbridge.common.BridgeException;

/** Indicates malformed, unsupported, or state-invalid Bedrock handshake input. */
public final class BedrockValidationException extends BridgeException {
  private static final long serialVersionUID = 1L;

  /** Creates a validation failure safe for internal diagnostics. */
  public BedrockValidationException(String message) {
    super(message);
  }
}
