package io.bedrockbridge.bedrock.auth;

import java.util.List;

/** Size-limited raw Bedrock authentication payload before verification. */
public record BedrockLoginPayload(List<String> chain, String clientDataJwt) {
  /** Defensively copies and validates token counts and aggregate size. */
  public BedrockLoginPayload {
    chain = List.copyOf(chain);
    if (chain.isEmpty() || chain.size() > 8) {
      throw new IllegalArgumentException("Login chain must contain between 1 and 8 tokens");
    }
    int size = clientDataJwt == null ? 0 : clientDataJwt.length();
    for (String token : chain) {
      size = Math.addExact(size, token.length());
    }
    if (clientDataJwt == null || size > 1 << 20) {
      throw new IllegalArgumentException("Login payload exceeds size limit");
    }
  }
}
