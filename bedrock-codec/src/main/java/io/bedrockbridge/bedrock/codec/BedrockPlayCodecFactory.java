package io.bedrockbridge.bedrock.codec;

import io.bedrockbridge.bedrock.BedrockProtocol;
import io.bedrockbridge.bedrock.BedrockProtocolLimits;
import io.bedrockbridge.bedrock.BedrockValidationException;
import io.bedrockbridge.protocol.ProtocolVersion;
import java.util.Objects;

/** Creates an exact-version Bedrock play codec from the independently defined catalogs. */
public final class BedrockPlayCodecFactory {
  private BedrockPlayCodecFactory() {}

  /** Resolves the supported catalog without aliases or best-effort packet guessing. */
  public static BedrockPlayCodec create(ProtocolVersion version, BedrockProtocolLimits limits) {
    ProtocolVersion checkedVersion = Objects.requireNonNull(version, "version");
    BedrockProtocolLimits checkedLimits = Objects.requireNonNull(limits, "limits");
    if (checkedVersion.equals(BedrockProtocol.PLAY_VERSION_748)) {
      return new BedrockPlayCodec(
          checkedVersion, checkedLimits, BedrockProtocol748PacketRegistry.create(checkedLimits));
    }
    if (checkedVersion.equals(BedrockProtocol.PLAY_VERSION_1001)) {
      return new BedrockPlayCodec(
          checkedVersion, checkedLimits, BedrockProtocol1001PacketRegistry.create(checkedLimits));
    }
    throw new BedrockValidationException(
        "Unsupported Bedrock play codec version: " + checkedVersion.protocolId());
  }
}
