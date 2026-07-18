package io.bedrockbridge.bedrock.session;

import java.net.InetSocketAddress;

/** Creates one connected DATA handler per admitted Bedrock endpoint. */
@FunctionalInterface
public interface ConnectedFrameHandlerFactory {
  /** Creates a handler for one remote peer, or returns {@code null} to reject game traffic. */
  ConnectedFrameHandler create(InetSocketAddress remoteAddress);
}
