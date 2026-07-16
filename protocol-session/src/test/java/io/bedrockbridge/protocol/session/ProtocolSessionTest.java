package io.bedrockbridge.protocol.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.bedrockbridge.common.LifecycleException;
import io.bedrockbridge.protocol.ProtocolState;
import io.bedrockbridge.protocol.registry.StateRegistry;
import org.junit.jupiter.api.Test;

class ProtocolSessionTest {
    @Test
    void stateMachineAllowsOnlyRegisteredTransition() {
        StateRegistry registry = new StateRegistry();
        registry.allow(ProtocolState.HANDSHAKE, ProtocolState.LOGIN);
        var machine = new ProtocolStateMachine(registry, ProtocolState.HANDSHAKE);
        machine.transition(ProtocolState.LOGIN);
        assertEquals(ProtocolState.LOGIN, machine.state());
        assertThrows(LifecycleException.class, () -> machine.transition(ProtocolState.PLAY));
    }
}
