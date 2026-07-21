package io.bedrockbridge.application.translation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.bedrockbridge.application.javawire.JavaWirePacket;
import io.bedrockbridge.application.javawire.JavaWorldState;
import io.bedrockbridge.bedrock.BedrockProtocol;
import io.bedrockbridge.bedrock.BedrockProtocolLimits;
import io.bedrockbridge.bedrock.BedrockValidationException;
import io.bedrockbridge.bedrock.auth.BedrockIdentity;
import io.bedrockbridge.bedrock.codec.BedrockPacketFrame;
import io.bedrockbridge.bedrock.codec.BedrockPacketFrameCodec;
import io.bedrockbridge.registry.generator.ExternalItemRegistry;
import io.bedrockbridge.registry.generator.ObservedItem;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegistryBackedStartGameFrameProviderTest {
  @Test
  void encodesOnlyValidatedRegistryEntriesIntoStartGame() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp384r1"));
    KeyPair keyPair = generator.generateKeyPair();
    ExternalItemRegistry registry =
        new ExternalItemRegistry(
            "748",
            List.of(new ObservedItem("minecraft:test", (short) 300, true)),
            42,
            "0".repeat(64));
    RegistryBackedStartGameFrameProvider provider =
        new RegistryBackedStartGameFrameProvider(
            registry, BedrockProtocolLimits.defaults(), "BedrockBridge-test");
    JavaWorldState world = new JavaWorldState();
    world.apply(
        new JavaWirePacket.PlayLogin(
            7,
            false,
            List.of("minecraft:overworld"),
            20,
            8,
            8,
            false,
            true,
            false,
            0,
            "minecraft:overworld",
            123L,
            1,
            -1,
            false,
            false,
            "",
            null,
            0,
            false));
    byte[] encoded =
        provider.build(
            BedrockProtocol.PLAY_VERSION_748,
            new BedrockIdentity(UUID.randomUUID(), "Player", "", "", keyPair.getPublic()),
            world);
    BedrockPacketFrame frame =
        new BedrockPacketFrameCodec(BedrockProtocolLimits.defaults()).decode(encoded);
    assertEquals(11, frame.header().packetId());
    assertEquals(0, frame.header().senderSubClientId());
    assertEquals(0, frame.header().targetSubClientId());
    assertThrows(
        BedrockValidationException.class,
        () ->
            provider.build(
                BedrockProtocol.PLAY_VERSION_1001,
                new BedrockIdentity(UUID.randomUUID(), "Player", "", "", keyPair.getPublic()),
                world));
  }

  @Test
  void refusesNon748Registry() {
    ExternalItemRegistry registry =
        new ExternalItemRegistry(
            "767",
            List.of(new ObservedItem("minecraft:test", (short) 1, false)),
            1,
            "0".repeat(64));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RegistryBackedStartGameFrameProvider(
                registry, BedrockProtocolLimits.defaults(), "test"));
  }
}
