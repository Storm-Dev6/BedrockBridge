package io.bedrockbridge.bedrock.codec;

import io.bedrockbridge.bedrock.BedrockPlayState;
import io.bedrockbridge.bedrock.BedrockProtocolLimits;
import io.bedrockbridge.bedrock.packet.play.BedrockPlayPacket;
import io.bedrockbridge.common.RegistrationException;
import io.bedrockbridge.protocol.PacketDirection;
import io.bedrockbridge.protocol.ProtocolVersion;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable exact-version play packet registry built with fail-fast collision detection. */
public final class BedrockPlayPacketRegistry {
  private final Map<WireKey, BedrockPlayPacketRegistration<?>> byWireKey;
  private final Map<TypeKey, BedrockPlayPacketRegistration<?>> byType;

  private BedrockPlayPacketRegistry(
      Map<WireKey, BedrockPlayPacketRegistration<?>> byWireKey,
      Map<TypeKey, BedrockPlayPacketRegistration<?>> byType) {
    this.byWireKey = Map.copyOf(byWireKey);
    this.byType = Map.copyOf(byType);
  }

  /** Creates a bounded mutable builder whose result is immutable. */
  public static Builder builder(BedrockProtocolLimits limits) {
    return new Builder(limits);
  }

  /** Resolves an exact packet registration for the current wire context. */
  public Optional<BedrockPlayPacketRegistration<?>> find(
      ProtocolVersion version, BedrockPlayState state, PacketDirection direction, int packetId) {
    return Optional.ofNullable(byWireKey.get(new WireKey(version, state, direction, packetId)));
  }

  /** Resolves an exact version and packet type for encoding. */
  public Optional<BedrockPlayPacketRegistration<?>> find(
      ProtocolVersion version, Class<? extends BedrockPlayPacket> packetType) {
    return Optional.ofNullable(byType.get(new TypeKey(version, packetType)));
  }

  /** Startup-only builder that rejects wire and Java type collisions atomically. */
  public static final class Builder {
    private final int maximumRegistrations;
    private final Map<WireKey, BedrockPlayPacketRegistration<?>> byWireKey = new HashMap<>();
    private final Map<TypeKey, BedrockPlayPacketRegistration<?>> byType = new HashMap<>();
    private int registrations;

    private Builder(BedrockProtocolLimits limits) {
      maximumRegistrations = Objects.requireNonNull(limits, "limits").maximumRegistryEntries();
    }

    /** Registers one packet layout in each declared legal state. */
    public <T extends BedrockPlayPacket> Builder register(
        BedrockPlayPacketRegistration<T> registration) {
      Objects.requireNonNull(registration, "registration");
      if (registrations >= maximumRegistrations) {
        throw new RegistrationException("Bedrock packet registry exceeds configured limit");
      }
      TypeKey typeKey = new TypeKey(registration.version(), registration.packetType());
      if (byType.containsKey(typeKey)) {
        throw new RegistrationException("Bedrock packet type already registered: " + typeKey);
      }
      for (BedrockPlayState state : registration.states()) {
        WireKey wireKey =
            new WireKey(
                registration.version(), state, registration.direction(), registration.packetId());
        if (byWireKey.containsKey(wireKey)) {
          throw new RegistrationException("Bedrock packet wire key already registered: " + wireKey);
        }
      }
      byType.put(typeKey, registration);
      for (BedrockPlayState state : registration.states()) {
        byWireKey.put(
            new WireKey(
                registration.version(), state, registration.direction(), registration.packetId()),
            registration);
      }
      registrations++;
      return this;
    }

    /** Freezes the currently registered catalog into immutable maps. */
    public BedrockPlayPacketRegistry build() {
      return new BedrockPlayPacketRegistry(byWireKey, byType);
    }
  }

  private record WireKey(
      ProtocolVersion version, BedrockPlayState state, PacketDirection direction, int packetId) {
    private WireKey {
      Objects.requireNonNull(version, "version");
      Objects.requireNonNull(state, "state");
      Objects.requireNonNull(direction, "direction");
    }
  }

  private record TypeKey(ProtocolVersion version, Class<? extends BedrockPlayPacket> packetType) {
    private TypeKey {
      Objects.requireNonNull(version, "version");
      Objects.requireNonNull(packetType, "packetType");
    }
  }
}
