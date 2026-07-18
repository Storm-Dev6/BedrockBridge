package io.bedrockbridge.application.translation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.bedrockbridge.application.javawire.JavaWirePacket;
import io.bedrockbridge.bedrock.packet.play.DisconnectPacket;
import io.bedrockbridge.bedrock.packet.play.PlayStatus;
import io.bedrockbridge.bedrock.packet.play.PlayStatusPacket;
import io.bedrockbridge.bedrock.packet.play.ResourcePacksInfoPacket;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JavaBedrockTranslatorTest {
  private final JavaBedrockTranslator translator = new JavaBedrockTranslator();

  @Test
  void translatesLoginResourcePackDisconnectAndKeepAlive() {
    var login =
        translator.onJavaLoginSuccess(
            new JavaWirePacket.LoginSuccess(UUID.randomUUID(), "Alex", true));
    assertEquals(new PlayStatusPacket(PlayStatus.LOGIN_SUCCESS), login.getFirst());
    assertEquals(
        new ResourcePacksInfoPacket(false, false, false, java.util.List.of()),
        translator.onResourcePackFlowStart().getFirst());
    var disconnect =
        (DisconnectPacket) translator.onJavaDisconnect("{\"text\":\"bye\"}").getFirst();
    assertEquals("{\"text\":\"bye\"}", disconnect.message());
    assertEquals(42L, translator.onJavaKeepAlive(42L));
  }
}
