package io.bedrockbridge.application.translation;

import io.bedrockbridge.bedrock.packet.play.BedrockPlayPacket;
import java.util.Arrays;
import java.util.List;

/** Typed control output plus an optional already-framed external StartGame packet. */
public final class BedrockSessionOutput {
  private final List<BedrockPlayPacket> packets;
  private final byte[] startGameFrame;

  public BedrockSessionOutput(List<BedrockPlayPacket> packets, byte[] startGameFrame) {
    this.packets = List.copyOf(packets);
    this.startGameFrame = startGameFrame == null ? null : startGameFrame.clone();
  }

  public List<BedrockPlayPacket> packets() {
    return packets;
  }

  /** Returns a defensive copy of the optional StartGame frame. */
  public byte[] startGameFrame() {
    return startGameFrame == null ? null : startGameFrame.clone();
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || (other instanceof BedrockSessionOutput output
            && packets.equals(output.packets)
            && Arrays.equals(startGameFrame, output.startGameFrame));
  }

  @Override
  public int hashCode() {
    return 31 * packets.hashCode() + Arrays.hashCode(startGameFrame);
  }
}
