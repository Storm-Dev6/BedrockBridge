package io.bedrockbridge.registry.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.bedrockbridge.bedrock.BedrockProtocolLimits;
import io.bedrockbridge.bedrock.codec.BedrockBinaryWriter;
import io.bedrockbridge.bedrock.codec.BedrockPacketFrame;
import io.bedrockbridge.bedrock.codec.BedrockPacketFrameCodec;
import io.bedrockbridge.bedrock.codec.BedrockPacketHeader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ItemRegistryArtifactTest {
  @Test
  void writesOnlyTheThreeAllowedFieldsInStableOrder() throws Exception {
    byte[] bytes =
        ItemRegistryArtifact.render(
            List.of(
                new ObservedItem("minecraft:zombie", (short) 32, false),
                new ObservedItem("minecraft:air", (short) 0, false)));
    String content = new String(bytes, StandardCharsets.UTF_8);
    assertEquals(2, content.lines().count());
    assertTrue(
        content.startsWith(
            "{\"itemName\":\"minecraft:air\",\"itemId\":0,\"componentBased\":false}\n"));
    assertTrue(
        content
            .lines()
            .allMatch(
                line ->
                    line.matches(
                        "\\{\\\"itemName\\\":\\\"[^\\\"]+\\\",\\\"itemId\\\":-?\\d+,\\\"componentBased\\\":(true|false)\\}")));
  }

  @Test
  void rejectsDuplicateNamesAndIds() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ItemRegistryArtifact.validate(
                List.of(
                    new ObservedItem("minecraft:air", (short) 0, false),
                    new ObservedItem("minecraft:air", (short) 1, false))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ItemRegistryArtifact.validate(
                List.of(
                    new ObservedItem("minecraft:air", (short) 0, false),
                    new ObservedItem("minecraft:stone", (short) 0, false))));
  }

  @Test
  void extractsOnlyItemFieldsFromSyntheticStartGameFrame() {
    BedrockProtocolLimits limits = BedrockProtocolLimits.defaults();
    BedrockBinaryWriter writer = new BedrockBinaryWriter(1024);
    writer.writeBytes(new byte[] {0x41, 0x42, 0x43});
    writer.writeUnsignedVarInt(2);
    writer.writeString("minecraft:air", 512);
    writer.writeShortLE(0);
    writer.writeBoolean(false);
    writer.writeString("minecraft:stone", 512);
    writer.writeShortLE(1);
    writer.writeBoolean(false);
    writer.writeString("00000000-0000-0000-0000-000000000000", 128);
    writer.writeBoolean(false);
    writer.writeString("1.21.40.03", 128);
    writer.writeBytes(new byte[8 + 16 + 3]);
    byte[] frame =
        new BedrockPacketFrameCodec(limits)
            .encode(
                new BedrockPacketFrame(new BedrockPacketHeader(11, 0, 0), writer.toByteArray()));

    List<ObservedItem> result = StartGameItemListExtractor.extract(frame, limits);
    assertEquals(
        List.of(
            new ObservedItem("minecraft:air", (short) 0, false),
            new ObservedItem("minecraft:stone", (short) 1, false)),
        result);
  }
}
