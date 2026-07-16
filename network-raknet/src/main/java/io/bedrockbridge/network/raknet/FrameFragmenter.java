package io.bedrockbridge.network.raknet;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Splits an oversized logical frame into independently reliable MTU-sized fragments. */
public final class FrameFragmenter {
    /** Splits a frame using a positive payload budget and caller-supplied split identifier. */
    public List<RakNetFrame> fragment(RakNetFrame frame, int maximumPayload, int splitId) {
        if (maximumPayload < 1) {
            throw new IllegalArgumentException("maximumPayload must be positive");
        }
        ByteBuffer payload = frame.payload().duplicate();
        if (payload.remaining() <= maximumPayload) {
            return List.of(frame);
        }
        int count = Math.ceilDiv(payload.remaining(), maximumPayload);
        if (count > 4096) {
            throw new IllegalArgumentException("Frame requires too many fragments");
        }
        List<RakNetFrame> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int length = Math.min(maximumPayload, payload.remaining());
            ByteBuffer part = payload.slice(payload.position(), length);
            payload.position(payload.position() + length);
            result.add(new RakNetFrame(
                    frame.reliability(),
                    frame.reliableIndex(),
                    frame.sequenceIndex(),
                    frame.orderIndex(),
                    frame.orderChannel(),
                    new RakNetFrame.SplitInfo(count, splitId, index),
                    part));
        }
        return List.copyOf(result);
    }
}
