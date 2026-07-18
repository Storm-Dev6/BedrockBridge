package io.bedrockbridge.application.translation;

import io.bedrockbridge.application.javawire.JavaWirePacket;
import io.bedrockbridge.bedrock.packet.play.BedrockPlayPacket;
import io.bedrockbridge.bedrock.packet.play.DisconnectPacket;
import io.bedrockbridge.bedrock.packet.play.PlayStatus;
import io.bedrockbridge.bedrock.packet.play.PlayStatusPacket;
import io.bedrockbridge.bedrock.packet.play.ResourcePacksInfoPacket;
import java.util.List;
import java.util.Objects;

/** Minimal, fail-closed event translation between the Java upstream and Bedrock downstream. */
public final class JavaBedrockTranslator {
  /** Emits the Bedrock login success status only after Java login success. */
  public List<BedrockPlayPacket> onJavaLoginSuccess(JavaWirePacket.LoginSuccess success) {
    Objects.requireNonNull(success, "success");
    return List.of(new PlayStatusPacket(PlayStatus.LOGIN_SUCCESS));
  }

  /** Announces an empty resource-pack catalog; no proprietary pack is synthesized. */
  public List<BedrockPlayPacket> onResourcePackFlowStart() {
    return List.of(new ResourcePacksInfoPacket(false, false, false, List.of()));
  }

  /** Forwards a bounded, non-sensitive Java disconnect reason to Bedrock. */
  public List<BedrockPlayPacket> onJavaDisconnect(String reasonJson) {
    String safeReason =
        reasonJson == null || reasonJson.isBlank() ? "Java upstream disconnected" : reasonJson;
    if (safeReason.length() > 4096) {
      safeReason = safeReason.substring(0, 4096);
    }
    return List.of(new DisconnectPacket(0, false, safeReason, ""));
  }

  /** Echoes Java keep-alive values without translating them into a guessed Bedrock packet. */
  public long onJavaKeepAlive(long payload) {
    return payload;
  }
}
