package io.bedrockbridge.protocol.registry;

import io.bedrockbridge.common.RegistrationException;
import io.bedrockbridge.protocol.ProtocolVersion;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Dynamic registry of exact protocol versions keyed by family and wire ID. */
public final class ProtocolRegistry {
  private final ConcurrentHashMap<Key, ProtocolVersion> versions = new ConcurrentHashMap<>();

  /** Registers one unique family and protocol-ID pair. */
  public void register(ProtocolVersion version) {
    Key key = new Key(version.family(), version.protocolId());
    if (versions.putIfAbsent(key, version) != null) {
      throw new RegistrationException("Protocol version already registered: " + key);
    }
  }

  /** Resolves an exact protocol version. */
  public Optional<ProtocolVersion> find(String family, int protocolId) {
    return Optional.ofNullable(versions.get(new Key(family, protocolId)));
  }

  /** Returns a weakly consistent immutable version snapshot. */
  public Collection<ProtocolVersion> versions() {
    return List.copyOf(versions.values());
  }

  private record Key(String family, int protocolId) {}
}
