package io.bedrockbridge.protocol.registry;

import io.bedrockbridge.protocol.PacketKey;
import io.bedrockbridge.protocol.codec.PacketDecoder;
import java.util.Optional;

/** Read-only deserializer projection over registered packet IDs. */
public final class DeserializerRegistry {
  private final PacketRegistry packets;

  /** Creates a deserializer view over the authoritative registry. */
  public DeserializerRegistry(PacketRegistry packets) {
    this.packets = java.util.Objects.requireNonNull(packets, "packets");
  }

  /** Finds the deserializer for a complete packet key. */
  public Optional<PacketDecoder<?>> find(PacketKey key) {
    return packets.find(key).map(PacketRegistration::codec);
  }
}
