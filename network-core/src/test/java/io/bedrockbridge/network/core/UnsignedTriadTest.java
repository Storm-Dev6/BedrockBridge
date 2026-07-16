package io.bedrockbridge.network.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UnsignedTriadTest {
    @Test
    void comparesAcrossWrapBoundary() {
        assertEquals(0, UnsignedTriad.increment(0xFF_FFFF));
        assertTrue(UnsignedTriad.isNewer(0, 0xFF_FFFF));
        assertEquals(2, UnsignedTriad.distance(0xFF_FFFF, 1));
    }
}
