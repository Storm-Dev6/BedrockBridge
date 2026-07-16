package io.bedrockbridge.network.raknet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RakNetCoreTest {
    @Test
    void frameCodecRoundTripsReliableOrderedSplitFrame() {
        var frame = new RakNetFrame(
                Reliability.RELIABLE_ORDERED,
                0xFF_FFFE,
                0,
                12,
                2,
                new RakNetFrame.SplitInfo(2, 7, 1),
                ByteBuffer.wrap(new byte[] {4, 5, 6}));
        ByteBuffer encoded = ByteBuffer.allocate(128);
        var codec = new RakNetFrameCodec();
        codec.encode(frame, encoded);
        encoded.flip();
        RakNetFrame decoded = codec.decode(encoded);
        assertEquals(frame.reliability(), decoded.reliability());
        assertEquals(frame.reliableIndex(), decoded.reliableIndex());
        assertEquals(frame.split(), decoded.split());
        assertEquals(frame.payload(), decoded.payload());
    }

    @Test
    void ackCodecRoundTripsSingletonAndRange() {
        var codec = new AckCodec(8);
        ByteBuffer encoded = ByteBuffer.allocate(64);
        List<AckRange> ranges = List.of(new AckRange(1, 1), new AckRange(5, 9));
        codec.encode(ranges, encoded);
        encoded.flip();
        assertEquals(ranges, codec.decode(encoded));
    }

    @Test
    void receiveWindowDeduplicatesAndAdvancesContiguousPrefix() {
        ReceiveWindow window = new ReceiveWindow(64, 10);
        assertEquals(ReceiveWindow.Result.ACCEPTED, window.accept(11));
        assertEquals(ReceiveWindow.Result.DUPLICATE, window.accept(11));
        assertEquals(ReceiveWindow.Result.ACCEPTED, window.accept(10));
        assertEquals(12, window.base());
    }

    @Test
    void splitAssemblerCompletesInIndexOrder() {
        var assembler = new SplitPacketAssembler(4, 1024, Duration.ofSeconds(1));
        Instant now = Instant.EPOCH;
        RakNetFrame second = split(1, new byte[] {3, 4});
        RakNetFrame first = split(0, new byte[] {1, 2});
        assertTrue(assembler.add(second, now).isEmpty());
        RakNetFrame complete = assembler.add(first, now).orElseThrow();
        byte[] result = new byte[complete.payload().remaining()];
        complete.payload().get(result);
        assertTrue(java.util.Arrays.equals(new byte[] {1, 2, 3, 4}, result));
    }

    @Test
    void orderingBuffersUntilMissingFrameArrives() {
        OrderingChannels channels = new OrderingChannels(8);
        assertTrue(channels.admit(ordered(1)).isEmpty());
        List<RakNetFrame> ready = channels.admit(ordered(0));
        assertEquals(2, ready.size());
        assertEquals(0, ready.get(0).orderIndex());
        assertEquals(1, ready.get(1).orderIndex());
    }

    @Test
    void recoveryAcknowledgementUpdatesQueue() {
        var estimator = new RttEstimator(Duration.ofMillis(20), Duration.ofSeconds(2));
        var recovery = new RecoveryQueue(8, 3, estimator);
        Instant sent = Instant.EPOCH;
        recovery.track(4, ByteBuffer.wrap(new byte[] {1}), sent);
        assertTrue(recovery.acknowledge(4, sent.plusMillis(100)));
        assertEquals(0, recovery.size());
        assertFalse(recovery.acknowledge(4, sent.plusMillis(110)));
    }

    @Test
    void mtuNeverExceedsObservedProbe() {
        MtuPolicy policy = new MtuPolicy(576, 1400, 1492);
        assertEquals(1200, policy.negotiate(1450, 1200));
    }

    @Test
    void fragmenterRespectsPayloadBudget() {
        RakNetFrame frame = new RakNetFrame(
                Reliability.RELIABLE_ORDERED,
                1,
                0,
                1,
                0,
                null,
                ByteBuffer.wrap(new byte[10]));
        List<RakNetFrame> fragments = new FrameFragmenter().fragment(frame, 4, 5);
        assertEquals(3, fragments.size());
        assertEquals(4, fragments.get(0).payload().remaining());
        assertEquals(2, fragments.get(2).payload().remaining());
    }

    @Test
    void mtuDiscoveryConvergesFromAcknowledgedProbes() {
        MtuDiscovery discovery =
                new MtuDiscovery(1200, 1400, 16, Duration.ofMillis(100));
        Instant now = Instant.EPOCH;
        int probe;
        while ((probe = discovery.nextProbe(now).orElse(-1)) != -1) {
            discovery.acknowledge(probe);
        }
        assertEquals(1400, discovery.discoveredMtu());
    }

    private static RakNetFrame split(int index, byte[] payload) {
        return new RakNetFrame(
                Reliability.RELIABLE_ORDERED,
                index,
                0,
                0,
                0,
                new RakNetFrame.SplitInfo(2, 9, index),
                ByteBuffer.wrap(payload));
    }

    private static RakNetFrame ordered(int index) {
        return new RakNetFrame(
                Reliability.RELIABLE_ORDERED,
                index,
                0,
                index,
                0,
                null,
                ByteBuffer.wrap(new byte[] {(byte) index}));
    }
}
