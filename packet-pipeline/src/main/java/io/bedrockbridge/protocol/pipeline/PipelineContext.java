package io.bedrockbridge.protocol.pipeline;

import io.bedrockbridge.protocol.PacketDirection;
import io.bedrockbridge.protocol.ProtocolState;
import io.bedrockbridge.protocol.ProtocolVersion;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Per-session pipeline metadata and typed extension attributes. */
public final class PipelineContext {
    private final ProtocolVersion version;
    private final PacketDirection direction;
    private final ProtocolState state;
    private final ConcurrentHashMap<Key<?>, Object> attributes = new ConcurrentHashMap<>();

    /** Creates immutable routing metadata for one pipeline invocation. */
    public PipelineContext(
            ProtocolVersion version, ProtocolState state, PacketDirection direction) {
        this.version = Objects.requireNonNull(version, "version");
        this.state = Objects.requireNonNull(state, "state");
        this.direction = Objects.requireNonNull(direction, "direction");
    }

    /** Returns the negotiated version. */
    public ProtocolVersion version() { return version; }

    /** Returns the current state snapshot. */
    public ProtocolState state() { return state; }

    /** Returns the processing direction. */
    public PacketDirection direction() { return direction; }

    /** Stores a non-null typed attribute. */
    public <T> void put(Key<T> key, T value) {
        attributes.put(key, key.type().cast(value));
    }

    /** Returns a typed attribute or null when absent. */
    public <T> T get(Key<T> key) {
        Object value = attributes.get(key);
        return value == null ? null : key.type().cast(value);
    }

    /** Collision-resistant typed pipeline attribute key. */
    public record Key<T>(String name, Class<T> type) {
        /** Validates key identity. */
        public Key {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
        }
    }
}
