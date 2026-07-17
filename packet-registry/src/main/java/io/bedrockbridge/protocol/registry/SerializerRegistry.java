package io.bedrockbridge.protocol.registry;

import io.bedrockbridge.protocol.Packet;
import io.bedrockbridge.protocol.codec.PacketEncoder;
import java.util.Optional;

/** Read-only serializer projection over registered packet codecs. */
public final class SerializerRegistry {
  private final PacketRegistry packets;

  /** Creates a serializer view over the authoritative registry. */
  public SerializerRegistry(PacketRegistry packets) {
    this.packets = java.util.Objects.requireNonNull(packets, "packets");
  }

  /** Finds the serializer for a packet class. */
  public Optional<PacketEncoder<?>> find(Class<? extends Packet> type) {
    return packets.find(type).map(PacketRegistration::codec);
  }
}
