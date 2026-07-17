package io.bedrockbridge.network.session;

import java.net.InetSocketAddress;
import java.time.Instant;

/** Creates a fully bounded RakNet session for an admitted endpoint. */
@FunctionalInterface
public interface SessionFactory {
  /** Creates one new session at the supplied clock instant. */
  RakNetSession create(InetSocketAddress remoteAddress, Instant now);
}
