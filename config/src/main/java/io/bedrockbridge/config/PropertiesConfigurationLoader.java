package io.bedrockbridge.config;

import io.bedrockbridge.common.ConfigurationException;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
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
      String bindAddress = required(properties, "bridge.bind-address");
      int bindPort = integer(properties, "bridge.bind-port");
      String upstreamAddress = required(properties, "bridge.upstream-address");
      int upstreamPort = integer(properties, "bridge.upstream-port");
      return new BridgeConfiguration(
          required(properties, "bridge.application-name"),
          bindAddress,
          bindPort,
          upstreamAddress,
          upstreamPort,
          integer(properties, "bridge.maximum-sessions"),
          integer(properties, "bridge.scheduler-threads"),
          bool(properties, "bridge.development-mode"),
          optional(properties, "bridge.registry-path"),
          optional(properties, "bridge.registry-protocol-version"),
          optional(properties, "bridge.registry-sha256"),
          optionalOr(properties, "bridge.offline-auth-mode", "deny"),
          parseUpstreams(properties, upstreamAddress, upstreamPort),
          parseListenerMappings(properties, bindPort));
    } catch (IllegalArgumentException failure) {
      throw new ConfigurationException("Invalid configuration: " + failure.getMessage(), failure);
    }
  }

  private static String required(Properties properties, String key) {
    String value = properties.getProperty(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing property " + key);
    }
    return value.trim();
  }

  private static String optional(Properties properties, String key) {
    String value = properties.getProperty(key);
    return value == null ? "" : value.trim();
  }

  private static String optionalOr(Properties properties, String key, String fallback) {
    String value = optional(properties, key);
    return value.isEmpty() ? fallback : value;
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

  private static Map<String, JavaUpstreamDefinition> parseUpstreams(
      Properties properties, String defaultAddress, int defaultPort) {
    Map<String, JavaUpstreamDefinition> result = new LinkedHashMap<>();
    result.put(
        "default",
        new JavaUpstreamDefinition("default", defaultAddress, defaultPort, 5_000, 15_000));
    Map<String, Map<String, String>> fields = new LinkedHashMap<>();
    String prefix = "bridge.upstream.";
    for (Object rawKey : properties.keySet()) {
      String key = rawKey.toString();
      if (!key.startsWith(prefix)) {
        continue;
      }
      String suffix = key.substring(prefix.length());
      int separator = suffix.indexOf('.');
      if (separator < 1) {
        throw new IllegalArgumentException("Invalid named upstream property: " + key);
      }
      String name = suffix.substring(0, separator);
      String field = suffix.substring(separator + 1);
      if (!field.equals("address")
          && !field.equals("port")
          && !field.equals("connect-timeout-ms")
          && !field.equals("read-timeout-ms")) {
        throw new IllegalArgumentException("Unknown named upstream field: " + key);
      }
      fields
          .computeIfAbsent(name, ignored -> new LinkedHashMap<>())
          .put(field, required(properties, key));
    }
    for (Map.Entry<String, Map<String, String>> entry : fields.entrySet()) {
      Map<String, String> value = entry.getValue();
      if (!value.containsKey("address") || !value.containsKey("port")) {
        throw new IllegalArgumentException(
            "Named upstream requires address and port: " + entry.getKey());
      }
      result.put(
          entry.getKey(),
          new JavaUpstreamDefinition(
              entry.getKey(),
              value.get("address"),
              parseInteger(value.get("port"), "upstream port"),
              parseInteger(value.getOrDefault("connect-timeout-ms", "5000"), "connect timeout"),
              parseInteger(value.getOrDefault("read-timeout-ms", "15000"), "read timeout")));
    }
    return result;
  }

  private static Map<Integer, String> parseListenerMappings(
      Properties properties, int defaultPort) {
    Map<Integer, String> result = new LinkedHashMap<>();
    result.put(defaultPort, "default");
    String prefix = "bridge.listener.";
    for (Object rawKey : properties.keySet()) {
      String key = rawKey.toString();
      if (!key.startsWith(prefix) || !key.endsWith(".upstream")) {
        continue;
      }
      String portText = key.substring(prefix.length(), key.length() - ".upstream".length());
      int port = parseInteger(portText, "listener port");
      result.put(port, required(properties, key));
    }
    return result;
  }

  private static int parseInteger(String value, String label) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException failure) {
      throw new IllegalArgumentException(label + " must be an integer", failure);
    }
  }

  private static void rejectUnknownProperties(Properties properties) {
    var accepted =
        java.util.Set.of(
            "bridge.application-name",
            "bridge.bind-address",
            "bridge.bind-port",
            "bridge.upstream-address",
            "bridge.upstream-port",
            "bridge.maximum-sessions",
            "bridge.scheduler-threads",
            "bridge.development-mode",
            "bridge.registry-path",
            "bridge.registry-protocol-version",
            "bridge.registry-sha256",
            "bridge.offline-auth-mode");
    for (Object keyObject : properties.keySet()) {
      String key = keyObject.toString();
      if (!accepted.contains(key)
          && !key.startsWith("bridge.upstream.")
          && !(key.startsWith("bridge.listener.") && key.endsWith(".upstream"))) {
        throw new ConfigurationException("Unknown configuration property: " + key);
      }
    }
  }
}
