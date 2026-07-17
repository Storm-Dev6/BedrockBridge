package io.bedrockbridge.network.core;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/** Non-blocking UDP transport contract used by the RakNet layer. */
public interface UdpTransport extends AutoCloseable {
  /** Starts accepting datagrams exactly once. */
  void start(DatagramHandler handler);

  /** Enqueues a snapshot of the remaining bytes for transmission. */
  boolean send(InetSocketAddress remoteAddress, ByteBuffer payload);

  /** Returns the actual bound address. */
  InetSocketAddress localAddress();

  /** Stops I/O and releases the socket. */
  @Override
  void close();
}
