package io.bedrockbridge.registry.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.bedrockbridge.common.ConfigurationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class VersionedExternalItemRegistryLoaderTest {
  @Test
  void parsesOnlyTheApprovedThreeFieldsAndPinsDigest() throws Exception {
    byte[] bytes =
        ItemRegistryArtifact.render(List.of(new ObservedItem("minecraft:stone", (short) 1, false)));
    String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    ExternalItemRegistry registry =
        new VersionedExternalItemRegistryLoader().parse(bytes, "748", sha256);
    assertEquals("748", registry.protocolVersion());
    assertEquals(1, registry.items().size());
    assertEquals(1, registry.items().getFirst().itemId());
  }

  @Test
  void rejectsUnknownFieldsAndDigestMismatch() throws Exception {
    VersionedExternalItemRegistryLoader loader = new VersionedExternalItemRegistryLoader();
    byte[] unknown =
        "{\"itemName\":\"minecraft:stone\",\"itemId\":1,\"componentBased\":false,\"extra\":1}\n"
            .getBytes(StandardCharsets.UTF_8);
    assertThrows(ConfigurationException.class, () -> loader.parse(unknown, "748", "0".repeat(64)));
    byte[] valid =
        ItemRegistryArtifact.render(List.of(new ObservedItem("minecraft:stone", (short) 1, false)));
    assertThrows(ConfigurationException.class, () -> loader.parse(valid, "748", "0".repeat(64)));
  }
}
