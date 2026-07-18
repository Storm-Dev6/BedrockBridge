package io.bedrockbridge.registry.generator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.bedrockbridge.bedrock.packet.play.NetworkCompressionAlgorithm;
import io.bedrockbridge.bedrock.packet.play.NetworkSettingsPacket;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class BdsLoopbackProbeTest {
  @Test
  void observeStartGameIsPublicAndObservationIsDefensive() throws Exception {
    Method method = BdsLoopbackProbe.class.getMethod("observeStartGame");
    assertTrue(Modifier.isPublic(method.getModifiers()));
    assertTrue(
        Modifier.isPublic(
            BdsLoopbackProbe.Observation.class
                .getMethod("itemRegistry", io.bedrockbridge.bedrock.BedrockProtocolLimits.class)
                .getModifiers()));

    NetworkSettingsPacket settings =
        new NetworkSettingsPacket(512, NetworkCompressionAlgorithm.ZLIB, false, 0, 0.0f);
    byte[] source = {0x0B, 0x01, 0x02};
    BdsLoopbackProbe.Observation observation = new BdsLoopbackProbe.Observation(settings, source);
    source[0] = 0;

    byte[] first = observation.startGameFrame();
    byte[] second = observation.startGameFrame();
    assertArrayEquals(new byte[] {0x0B, 0x01, 0x02}, first);
    assertNotSame(first, second);
    first[1] = 0;
    assertArrayEquals(new byte[] {0x0B, 0x01, 0x02}, observation.startGameFrame());
  }

  @Test
  void observationRejectsMissingFields() {
    NetworkSettingsPacket settings =
        new NetworkSettingsPacket(512, NetworkCompressionAlgorithm.ZLIB, false, 0, 0.0f);
    assertThrows(
        NullPointerException.class, () -> new BdsLoopbackProbe.Observation(settings, null));
    assertThrows(
        NullPointerException.class,
        () -> new BdsLoopbackProbe.Observation(null, new byte[] {0x0B}));
  }
}
