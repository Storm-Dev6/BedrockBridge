package io.bedrockbridge.protocol.codec;

import io.bedrockbridge.protocol.Packet;
import io.bedrockbridge.protocol.PacketReader;

/** Decodes one packet type into a fresh instance. */
@FunctionalInterface
public interface PacketDecoder<T extends Packet> {
  /** Reads fields into the supplied packet. */
  void decode(T packet, PacketReader reader);
}
