package io.bedrockbridge.config;

/** Applies security and cross-field policy to a structurally valid configuration. */
@FunctionalInterface
public interface ConfigurationValidator {
    /** Returns the validated instance or throws a classified configuration error. */
    BridgeConfiguration validate(BridgeConfiguration configuration);
}
