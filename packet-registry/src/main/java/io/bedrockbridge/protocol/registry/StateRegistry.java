package io.bedrockbridge.protocol.registry;

import io.bedrockbridge.protocol.ProtocolState;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

/** Registry of legal protocol state transitions. */
public final class StateRegistry {
    private final EnumMap<ProtocolState, EnumSet<ProtocolState>> transitions =
            new EnumMap<>(ProtocolState.class);

    /** Creates an empty, fail-closed transition registry. */
    public StateRegistry() {
        for (ProtocolState state : ProtocolState.values()) {
            transitions.put(state, EnumSet.noneOf(ProtocolState.class));
        }
    }

    /** Allows one directed state transition. */
    public void allow(ProtocolState from, ProtocolState to) {
        transitions.get(from).add(to);
    }

    /** Returns whether a directed transition is legal. */
    public boolean permits(ProtocolState from, ProtocolState to) {
        return transitions.get(from).contains(to);
    }

    /** Returns an immutable snapshot of allowed targets. */
    public Set<ProtocolState> targets(ProtocolState from) {
        return Set.copyOf(transitions.get(from));
    }
}
