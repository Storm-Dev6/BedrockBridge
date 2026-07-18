package io.bedrockbridge.application.upstream;

import io.bedrockbridge.common.ConfigurationException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Fail-closed Java upstream state machine for handshake, status, login, configuration, and play.
 */
public final class JavaUpstreamSession {
  private final String host;
  private final int port;
  private final Consumer<JavaUpstreamPacket> sender;
  private JavaUpstreamState state = JavaUpstreamState.DISCONNECTED;

  public JavaUpstreamSession(String host, int port, Consumer<JavaUpstreamPacket> sender) {
    this.host = Objects.requireNonNull(host, "host");
    this.port = port;
    this.sender = Objects.requireNonNull(sender, "sender");
    if (host.isBlank() || port < 1 || port > 65_535) {
      throw new IllegalArgumentException("Invalid Java upstream endpoint");
    }
  }

  public void connect() {
    require(JavaUpstreamState.DISCONNECTED);
    sender.accept(new JavaUpstreamPacket.Handshake(host, port, 2));
    state = JavaUpstreamState.HANDSHAKING;
  }

  public void beginStatus() {
    require(JavaUpstreamState.HANDSHAKING);
    sender.accept(new JavaUpstreamPacket.StatusRequest());
    state = JavaUpstreamState.STATUS;
  }

  public void beginLogin(String username) {
    require(JavaUpstreamState.HANDSHAKING);
    if (username == null || username.isBlank() || username.length() > 16) {
      throw new ConfigurationException("Java upstream username is invalid");
    }
    sender.accept(new JavaUpstreamPacket.LoginStart(username));
    state = JavaUpstreamState.LOGIN;
  }

  public void enterConfiguration() {
    require(JavaUpstreamState.LOGIN);
    state = JavaUpstreamState.CONFIGURATION;
  }

  public void acknowledgeConfiguration() {
    require(JavaUpstreamState.CONFIGURATION);
    sender.accept(new JavaUpstreamPacket.ConfigurationAcknowledged());
    state = JavaUpstreamState.PLAY;
    sender.accept(new JavaUpstreamPacket.PlayReady());
  }

  public JavaUpstreamState state() {
    return state;
  }

  private void require(JavaUpstreamState expected) {
    if (state != expected) {
      throw new IllegalStateException("Expected Java state " + expected + " but was " + state);
    }
  }
}
