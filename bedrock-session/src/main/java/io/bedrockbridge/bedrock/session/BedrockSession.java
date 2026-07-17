package io.bedrockbridge.bedrock.session;

import io.bedrockbridge.bedrock.codec.BedrockDatagramCodec;
import io.bedrockbridge.bedrock.login.BedrockLoginState;
import io.bedrockbridge.bedrock.login.BedrockLoginStateMachine;
import io.bedrockbridge.protocol.Packet;
import io.bedrockbridge.protocol.PacketDirection;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;

/** Owns one Bedrock transport handshake, timeout, keepalive response, and outbound encoding. */
public final class BedrockSession {
  private final InetSocketAddress remoteAddress;
  private final BedrockDatagramCodec codec;
  private final BedrockLoginStateMachine login;
  private final Duration timeout;
  private final Consumer<ByteBuffer> sender;
  private Instant lastActivity;

  /** Creates a session with bounded inactivity timeout and an ownership-transferring sender. */
  public BedrockSession(
      InetSocketAddress remoteAddress,
      BedrockDatagramCodec codec,
      BedrockLoginStateMachine login,
      Duration timeout,
      Consumer<ByteBuffer> sender,
      Instant now) {
    this.remoteAddress = Objects.requireNonNull(remoteAddress, "remoteAddress");
    this.codec = Objects.requireNonNull(codec, "codec");
    this.login = Objects.requireNonNull(login, "login");
    this.timeout = Objects.requireNonNull(timeout, "timeout");
    this.sender = Objects.requireNonNull(sender, "sender");
    lastActivity = Objects.requireNonNull(now, "now");
    if (timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("timeout must be positive");
    }
  }

  /** Decodes, validates, applies, and replies to one serverbound handshake packet. */
  public synchronized void receive(ByteBuffer payload, Instant now) {
    if (state() == BedrockLoginState.DISCONNECTED) {
      return;
    }
    Packet packet = codec.decode(payload, PacketDirection.SERVERBOUND);
    lastActivity = now;
    login.handle(packet, now).ifPresent(this::send);
  }

  /** Disconnects sessions whose peer has not produced transport activity in time. */
  public synchronized void tick(Instant now) {
    if (state() != BedrockLoginState.DISCONNECTED
        && Duration.between(lastActivity, now).compareTo(timeout) >= 0) {
      login.disconnect();
    }
  }

  /** Immediately closes this handshake session. */
  public synchronized void disconnect() {
    login.disconnect();
  }

  /** Returns the peer endpoint. */
  public InetSocketAddress remoteAddress() {
    return remoteAddress;
  }

  /** Returns the current login state. */
  public BedrockLoginState state() {
    return login.state();
  }

  private void send(Packet packet) {
    ByteBuffer output = ByteBuffer.allocate(2048);
    codec.encode(packet, output);
    output.flip();
    sender.accept(output.asReadOnlyBuffer());
  }
}
