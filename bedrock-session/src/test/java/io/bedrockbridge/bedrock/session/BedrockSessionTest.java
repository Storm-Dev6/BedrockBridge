package io.bedrockbridge.bedrock.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.bedrockbridge.bedrock.codec.BedrockDatagramCodec;
import io.bedrockbridge.bedrock.codec.BedrockPacketRegistry;
import io.bedrockbridge.bedrock.codec.BedrockPacketValidator;
import io.bedrockbridge.bedrock.login.BedrockLoginState;
import io.bedrockbridge.bedrock.login.BedrockLoginStateMachine;
import io.bedrockbridge.bedrock.login.ProtocolVersionNegotiator;
import io.bedrockbridge.bedrock.packet.OpenConnectionRequest1;
import io.bedrockbridge.network.raknet.MtuPolicy;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BedrockSessionTest {
    @Test
    void bootstrapsFirstRequestAndTimesOut() {
        var codec = new BedrockDatagramCodec(
                BedrockPacketRegistry.create(),
                new BedrockPacketValidator(new MtuPolicy(576, 1492, 1492)));
        InetSocketAddress remote = new InetSocketAddress("127.0.0.1", 19132);
        List<ByteBuffer> sent = new ArrayList<>();
        var session = new BedrockSession(
                remote,
                codec,
                new BedrockLoginStateMachine(7, remote, new ProtocolVersionNegotiator()),
                Duration.ofSeconds(10),
                sent::add,
                Instant.EPOCH);
        ByteBuffer request = ByteBuffer.allocate(1200);
        codec.encode(new OpenConnectionRequest1(11, 1200), request);
        request.flip();
        session.receive(request, Instant.EPOCH);
        assertFalse(sent.isEmpty());
        assertEquals(BedrockLoginState.MTU_NEGOTIATED, session.state());
        session.tick(Instant.EPOCH.plusSeconds(10));
        assertEquals(BedrockLoginState.DISCONNECTED, session.state());
    }
}
