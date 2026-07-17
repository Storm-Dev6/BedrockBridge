package io.bedrockbridge.config;

import io.bedrockbridge.common.ConfigurationException;
import java.util.Objects;

/** Enforces production-safe infrastructure configuration policy. */
public final class DefaultConfigurationValidator implements ConfigurationValidator {
  @Override
  public BridgeConfiguration validate(BridgeConfiguration configuration) {
    Objects.requireNonNull(configuration, "configuration");
    if (!configuration.developmentMode()
        && configuration.bindAddress().equals(configuration.upstreamAddress())
        && configuration.bindPort() == configuration.upstreamPort()) {
      throw new ConfigurationException(
          "Bind and upstream endpoints must differ in production mode");
    }
    return configuration;
  }
}
