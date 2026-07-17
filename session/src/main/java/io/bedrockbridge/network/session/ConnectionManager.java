package io.bedrockbridge.network.session;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Optional;

/** Concurrent registry and lifecycle owner for active RakNet connections. */
public interface ConnectionManager extends AutoCloseable {
  /** Finds an active session by remote endpoint. */
  Optional<RakNetSession> find(InetSocketAddress remoteAddress);

  /** Returns a weakly consistent immutable snapshot of active sessions. */
  Collection<RakNetSession> sessions();

  /** Disconnects every session and stops transport maintenance. */
  @Override
  void close();
}
