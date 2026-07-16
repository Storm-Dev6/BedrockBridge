package io.bedrockbridge.common;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe, type-keyed dependency container used by the application composition root. */
public final class ServiceContainer {
    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    /** Registers exactly one service for its public contract. */
    public <T> ServiceContainer register(Class<T> contract, T service) {
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(service, "service");
        if (!contract.isInstance(service)) {
            throw new RegistrationException(
                    service.getClass().getName() + " does not implement " + contract.getName());
        }
        if (services.putIfAbsent(contract, service) != null) {
            throw new RegistrationException("Service already registered: " + contract.getName());
        }
        return this;
    }

    /** Resolves a required service or fails immediately. */
    public <T> T require(Class<T> contract) {
        Objects.requireNonNull(contract, "contract");
        Object service = services.get(contract);
        if (service == null) {
            throw new RegistrationException("Service not registered: " + contract.getName());
        }
        return contract.cast(service);
    }
}
