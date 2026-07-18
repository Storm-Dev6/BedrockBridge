package io.bedrockbridge.bedrock.session;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

/** Receives one reassembled, ordered RakNet payload after transport admission. */
@FunctionalInterface
public interface ConnectedFrameHandler {
  /** Handles a read-only payload and may enqueue bounded connected payloads for the peer. */
  void handle(ByteBuffer payload, Consumer<ByteBuffer> outbound);
}
