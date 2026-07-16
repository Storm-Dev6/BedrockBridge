package io.bedrockbridge.config;

import java.nio.file.Path;

/** Loads a bridge configuration from a controlled external source. */
@FunctionalInterface
public interface ConfigurationLoader {
    /** Loads configuration from the specified path or throws a classified configuration error. */
    BridgeConfiguration load(Path path);
}
