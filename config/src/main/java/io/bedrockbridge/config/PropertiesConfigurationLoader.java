package io.bedrockbridge.config;

import io.bedrockbridge.common.ConfigurationException;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

/** Loads strict UTF-8 Java properties without silently accepting missing required values. */
public final class PropertiesConfigurationLoader implements ConfigurationLoader {
    @Override
    public BridgeConfiguration load(Path path) {
        Objects.requireNonNull(path, "path");
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException failure) {
            throw new ConfigurationException("Unable to read configuration: " + path, failure);
        }
        rejectUnknownProperties(properties);
        try {
            return new BridgeConfiguration(
                    required(properties, "bridge.application-name"),
                    required(properties, "bridge.bind-address"),
                    integer(properties, "bridge.bind-port"),
                    required(properties, "bridge.upstream-address"),
                    integer(properties, "bridge.upstream-port"),
                    integer(properties, "bridge.maximum-sessions"),
                    integer(properties, "bridge.scheduler-threads"),
                    bool(properties, "bridge.development-mode"));
        } catch (IllegalArgumentException failure) {
            throw new ConfigurationException(
                    "Invalid configuration: " + failure.getMessage(), failure);
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing property " + key);
        }
        return value.trim();
    }

    private static int integer(Properties properties, String key) {
        String value = required(properties, key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(key + " must be an integer", failure);
        }
    }

    private static boolean bool(Properties properties, String key) {
        String value = required(properties, key);
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException(key + " must be true or false");
        }
        return Boolean.parseBoolean(value);
    }

    private static void rejectUnknownProperties(Properties properties) {
        var accepted = java.util.Set.of(
                "bridge.application-name",
                "bridge.bind-address",
                "bridge.bind-port",
                "bridge.upstream-address",
                "bridge.upstream-port",
                "bridge.maximum-sessions",
                "bridge.scheduler-threads",
                "bridge.development-mode");
        for (Object key : properties.keySet()) {
            if (!accepted.contains(key.toString())) {
                throw new ConfigurationException("Unknown configuration property: " + key);
            }
        }
    }
}
