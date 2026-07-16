package io.bedrockbridge.network.raknet;

import io.bedrockbridge.network.core.UnsignedTriad;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Session-confined ordered/sequenced frame admission for 32 independent channels. */
public final class OrderingChannels {
    private final Channel[] channels = new Channel[32];
    private final int maximumBufferedPerChannel;

    /** Creates channels with a strict per-channel reorder bound. */
    public OrderingChannels(int maximumBufferedPerChannel) {
        if (maximumBufferedPerChannel < 1 || maximumBufferedPerChannel > 4096) {
            throw new IllegalArgumentException("maximumBufferedPerChannel must be between 1 and 4096");
        }
        this.maximumBufferedPerChannel = maximumBufferedPerChannel;
        for (int index = 0; index < channels.length; index++) {
            channels[index] = new Channel();
        }
    }

    /** Returns frames now deliverable in order; stale sequenced frames produce an empty list. */
    public List<RakNetFrame> admit(RakNetFrame frame) {
        if (!frame.reliability().isOrdered()) {
            return List.of(frame);
        }
        Channel channel = channels[frame.orderChannel()];
        if (frame.reliability().isSequenced()) {
            if (frame.orderIndex() == channel.expectedOrder
                    && !UnsignedTriad.isNewer(frame.sequenceIndex(), channel.latestSequence)
                    && channel.hasSequence) {
                return List.of();
            }
            channel.latestSequence = frame.sequenceIndex();
            channel.hasSequence = true;
            return List.of(frame);
        }
        int distance = UnsignedTriad.distance(channel.expectedOrder, frame.orderIndex());
        if (distance >= UnsignedTriad.MODULUS / 2) {
            return List.of();
        }
        if (distance > maximumBufferedPerChannel) {
            throw new IllegalArgumentException("Ordered frame is too far ahead");
        }
        channel.buffered.putIfAbsent(frame.orderIndex(), frame);
        List<RakNetFrame> deliverable = new ArrayList<>();
        RakNetFrame next;
        while ((next = channel.buffered.remove(channel.expectedOrder)) != null) {
            deliverable.add(next);
            channel.expectedOrder = UnsignedTriad.increment(channel.expectedOrder);
        }
        return List.copyOf(deliverable);
    }

    private static final class Channel {
        private final Map<Integer, RakNetFrame> buffered = new HashMap<>();
        private int expectedOrder;
        private int latestSequence;
        private boolean hasSequence;
    }
}
