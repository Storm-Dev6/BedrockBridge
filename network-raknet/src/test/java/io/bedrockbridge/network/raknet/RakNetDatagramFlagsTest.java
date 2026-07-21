package io.bedrockbridge.network.raknet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RakNetDatagramFlagsTest {
  @Test
  void acceptsOptionalDataFlagsWithoutConfusingControlDatagrams() {
    assertTrue(RakNetDatagramFlags.isData(0x80));
    assertTrue(RakNetDatagramFlags.isData(0x84));
    assertTrue(RakNetDatagramFlags.isData(0x9C));
    assertFalse(RakNetDatagramFlags.isData(0xA0));
    assertFalse(RakNetDatagramFlags.isData(0xC0));
    assertFalse(RakNetDatagramFlags.isData(0x01));
    assertFalse(RakNetDatagramFlags.isData(-1));
    assertFalse(RakNetDatagramFlags.isData(0x180));
  }

  @Test
  void recognizesAllConnectedEnvelopeTypes() {
    assertTrue(RakNetDatagramFlags.isConnected(0x84));
    assertTrue(RakNetDatagramFlags.isConnected(0xA0));
    assertTrue(RakNetDatagramFlags.isConnected(0xC0));
    assertFalse(RakNetDatagramFlags.isConnected(0x07));
  }
}
