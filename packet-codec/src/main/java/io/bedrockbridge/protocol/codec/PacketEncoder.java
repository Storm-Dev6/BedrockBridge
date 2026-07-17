package io.bedrockbridge.protocol.codec;

import io.bedrockbridge.protocol.Packet;
import io.bedrockbridge.protocol.PacketWriter;

/** Encodes one packet type. */
@FunctionalInterface
public interface PacketEncoder<T extends Packet> {
  /** Writes the packet fields to the supplied writer. */
  void encode(T packet, PacketWriter writer);
}
