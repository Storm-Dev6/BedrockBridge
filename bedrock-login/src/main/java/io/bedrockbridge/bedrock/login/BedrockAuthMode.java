package io.bedrockbridge.bedrock.login;

/** Explicit policy for Bedrock identity-chain acceptance. */
public enum BedrockAuthMode {
  ONLINE,
  OFFLINE_DENY,
  OFFLINE_ALLOW_SELF_SIGNED
}
