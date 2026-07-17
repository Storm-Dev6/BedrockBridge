package io.bedrockbridge.protocol.registry;

import io.bedrockbridge.protocol.Packet;
import io.bedrockbridge.protocol.PacketKey;
import java.util.Optional;

/** Dynamic, switch-free packet registration lookup. */
public interface PacketRegistry {
  /** Registers one unique packet key and packet class. */
  <T extends Packet> void register(PacketRegistration<T> registration);

  /** Resolves packet metadata from the complete wire key. */
  Optional<PacketRegistration<?>> find(PacketKey key);

  /** Resolves packet metadata from its runtime packet class. */
  Optional<PacketRegistration<?>> find(Class<? extends Packet> packetType);
}
