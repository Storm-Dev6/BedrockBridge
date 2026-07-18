package io.bedrockbridge.config;

import io.bedrockbridge.common.Checks;

/** Named Java upstream endpoint with bounded connection lifecycle timeouts. */
public record JavaUpstreamDefinition(
    String name, String address, int port, int connectTimeoutMillis, int readTimeoutMillis) {
  public JavaUpstreamDefinition {
    name = Checks.notBlank(name, "upstream name").trim();
    address = Checks.notBlank(address, "upstream address").trim();
    port = Checks.inRange(port, 1, 65_535, "upstream port");
    connectTimeoutMillis = Checks.inRange(connectTimeoutMillis, 1, 120_000, "connect timeout");
    readTimeoutMillis = Checks.inRange(readTimeoutMillis, 1, 300_000, "read timeout");
  }
}
