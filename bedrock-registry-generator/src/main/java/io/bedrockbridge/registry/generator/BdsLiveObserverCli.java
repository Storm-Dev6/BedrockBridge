package io.bedrockbridge.registry.generator;

import io.bedrockbridge.bedrock.auth.BedrockChainVerifier;
import io.bedrockbridge.bedrock.auth.InMemoryReplayGuard;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** CLI for the bounded real-client RakNet relay and authenticated observation boundary. */
public final class BdsLiveObserverCli {
  private BdsLiveObserverCli() {}

  public static void main(String[] args) throws Exception {
    Map<String, String> options = parseOptions(args);
    InetSocketAddress listen = endpoint(required(options, "--listen"));
    InetSocketAddress bds = endpoint(required(options, "--bds"));
    Path artifact = Path.of(required(options, "--artifact"));
    Path trustedRoot = Path.of(required(options, "--trusted-root"));
    long timeoutSeconds = parseTimeout(options.getOrDefault("--timeout-seconds", "300"));
    PublicKey root = ExternalPublicKeyLoader.load(trustedRoot);
    BedrockChainVerifier verifier =
        new BedrockChainVerifier(
            Set.of(root), new InMemoryReplayGuard(8), Clock.systemUTC(), Duration.ofSeconds(30));
    LiveBedrockObserver observer =
        new LiveBedrockObserver(
            listen, bds, verifier, artifact, Duration.ofSeconds(timeoutSeconds), Clock.systemUTC());
    try {
      LiveBedrockObserver.Result result = observer.run();
      System.out.println("observer-state=" + result.terminalState());
      if (result.artifact() != null) {
        ItemRegistryArtifact.Summary summary = result.artifact();
        System.out.println(
            "protocol=748 itemCount="
                + summary.itemCount()
                + " artifactBytes="
                + summary.byteCount()
                + " artifactSha256="
                + summary.sha256());
      }
      result.trace().forEach(event -> System.out.println("observer-trace " + event));
    } catch (LiveBedrockObserver.ObserverBoundaryException boundary) {
      System.err.println("OBSERVER_BLOCKED state=ENCRYPTION_OR_PROTOCOL_BOUNDARY");
      System.err.println("OBSERVER_BLOCKED reason=" + boundary.getMessage());
      observer.trace().forEach(event -> System.err.println("observer-trace " + event));
      throw boundary;
    }
  }

  private static Map<String, String> parseOptions(String[] args) {
    if (args.length < 8 || args.length > 10 || args.length % 2 != 0) {
      throw new IllegalArgumentException(
          "Usage: --listen <ip:port> --bds <ip:port> --artifact <external.ndjson> "
              + "--trusted-root <external.der-or-pem> [--timeout-seconds <1..3600>]");
    }
    Map<String, String> result = new HashMap<>();
    for (int index = 0; index < args.length; index += 2) {
      String key = args[index];
      String value = args[index + 1];
      if (!key.startsWith("--") || value.isBlank() || result.put(key, value) != null) {
        throw new IllegalArgumentException("Invalid or duplicate observer option");
      }
    }
    if (result.keySet().stream()
        .anyMatch(
            key ->
                !key.equals("--listen")
                    && !key.equals("--bds")
                    && !key.equals("--artifact")
                    && !key.equals("--trusted-root")
                    && !key.equals("--timeout-seconds"))) {
      throw new IllegalArgumentException("Unknown observer option");
    }
    return Map.copyOf(result);
  }

  private static String required(Map<String, String> options, String name) {
    String value = options.get(name);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing required option: " + name);
    }
    return value;
  }

  private static long parseTimeout(String value) {
    try {
      long seconds = Long.parseLong(value);
      if (seconds < 1 || seconds > 3_600) {
        throw new IllegalArgumentException("--timeout-seconds must be between 1 and 3600");
      }
      return seconds;
    } catch (NumberFormatException invalid) {
      throw new IllegalArgumentException("--timeout-seconds must be an integer", invalid);
    }
  }

  private static InetSocketAddress endpoint(String value) {
    int separator = value.lastIndexOf(':');
    if (separator <= 0 || separator == value.length() - 1) {
      throw new IllegalArgumentException("Endpoint must use host:port syntax");
    }
    String host = value.substring(0, separator);
    try {
      int port = Integer.parseInt(value.substring(separator + 1));
      if (port < 1 || port > 65_535) {
        throw new IllegalArgumentException("Endpoint port is out of range");
      }
      return new InetSocketAddress(host, port);
    } catch (NumberFormatException invalid) {
      throw new IllegalArgumentException("Endpoint port is not an integer", invalid);
    }
  }
}
