package io.bedrockbridge.network.raknet;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Shared bounded codec for ACK and NACK range records. */
public final class AckCodec {
    private final int maximumRecords;

    /** Creates a codec with a positive anti-amplification record limit. */
    public AckCodec(int maximumRecords) {
        if (maximumRecords < 1 || maximumRecords > 4096) {
            throw new IllegalArgumentException("maximumRecords must be between 1 and 4096");
        }
        this.maximumRecords = maximumRecords;
    }

    /** Encodes compact singleton/range records. */
    public void encode(List<AckRange> ranges, ByteBuffer output) {
        if (ranges.size() > maximumRecords) {
            throw new IllegalArgumentException("Too many ACK records");
        }
        output.putShort((short) ranges.size());
        for (AckRange range : ranges) {
            boolean singleton = range.start() == range.end();
            output.put((byte) (singleton ? 1 : 0));
            RakNetFrameCodec.putTriad(output, range.start());
            if (!singleton) {
                RakNetFrameCodec.putTriad(output, range.end());
            }
        }
    }

    /** Decodes compact records while enforcing the configured record limit. */
    public List<AckRange> decode(ByteBuffer input) {
        if (input.remaining() < 2) {
            throw new IllegalArgumentException("Truncated ACK header");
        }
        int count = Short.toUnsignedInt(input.getShort());
        if (count > maximumRecords) {
            throw new IllegalArgumentException("Too many ACK records");
        }
        List<AckRange> ranges = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            if (!input.hasRemaining()) {
                throw new IllegalArgumentException("Truncated ACK record");
            }
            boolean singleton = input.get() != 0;
            int start = RakNetFrameCodec.getTriad(input);
            int end = singleton ? start : RakNetFrameCodec.getTriad(input);
            ranges.add(new AckRange(start, end));
        }
        return List.copyOf(ranges);
    }
}
