package io.bedrockbridge.protocol.registry;

import io.bedrockbridge.common.RegistrationException;
import io.bedrockbridge.protocol.Packet;
import io.bedrockbridge.protocol.PacketKey;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe dynamic packet registry with atomic collision rejection. */
public final class DynamicPacketRegistry implements PacketRegistry {
    private final ConcurrentHashMap<PacketKey, PacketRegistration<?>> byKey =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<? extends Packet>, PacketRegistration<?>> byType =
            new ConcurrentHashMap<>();

    @Override
    public <T extends Packet> void register(PacketRegistration<T> registration) {
        Objects.requireNonNull(registration, "registration");
        if (byKey.putIfAbsent(registration.key(), registration) != null) {
            throw new RegistrationException("Packet key already registered: " + registration.key());
        }
        if (byType.putIfAbsent(registration.packetType(), registration) != null) {
            byKey.remove(registration.key(), registration);
            throw new RegistrationException(
                    "Packet type already registered: " + registration.packetType().getName());
        }
    }

    @Override
    public Optional<PacketRegistration<?>> find(PacketKey key) {
        return Optional.ofNullable(byKey.get(Objects.requireNonNull(key, "key")));
    }

    @Override
    public Optional<PacketRegistration<?>> find(Class<? extends Packet> packetType) {
        return Optional.ofNullable(byType.get(Objects.requireNonNull(packetType, "packetType")));
    }
}
