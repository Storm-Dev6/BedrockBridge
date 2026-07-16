package io.bedrockbridge.protocol.session;

import io.bedrockbridge.common.LifecycleException;
import io.bedrockbridge.protocol.ProtocolState;
import io.bedrockbridge.protocol.registry.StateRegistry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Atomic protocol state machine governed by a fail-closed transition registry. */
public final class ProtocolStateMachine {
    private final StateRegistry transitions;
    private final AtomicReference<ProtocolState> state;

    /** Creates a machine in the supplied initial state. */
    public ProtocolStateMachine(StateRegistry transitions, ProtocolState initialState) {
        this.transitions = Objects.requireNonNull(transitions, "transitions");
        state = new AtomicReference<>(Objects.requireNonNull(initialState, "initialState"));
    }

    /** Returns the current state. */
    public ProtocolState state() {
        return state.get();
    }

    /** Atomically performs one legal transition. */
    public void transition(ProtocolState target) {
        Objects.requireNonNull(target, "target");
        while (true) {
            ProtocolState current = state.get();
            if (!transitions.permits(current, target)) {
                throw new LifecycleException(
                        "Illegal protocol transition: " + current + " -> " + target);
            }
            if (state.compareAndSet(current, target)) {
                return;
            }
        }
    }
}
