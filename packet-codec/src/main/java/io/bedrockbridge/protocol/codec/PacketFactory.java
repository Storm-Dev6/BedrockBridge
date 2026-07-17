package io.bedrockbridge.protocol.codec;

import io.bedrockbridge.protocol.Packet;

/** Creates a fresh packet instance for one decode operation. */
@FunctionalInterface
public interface PacketFactory<T extends Packet> {
  /** Creates an unshared packet instance. */
  T create();
}
