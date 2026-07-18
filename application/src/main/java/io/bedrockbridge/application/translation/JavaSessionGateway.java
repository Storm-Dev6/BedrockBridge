package io.bedrockbridge.application.translation;

import io.bedrockbridge.application.javawire.JavaWorldState;
import io.bedrockbridge.bedrock.packet.play.BedrockPlayPacket;
import java.io.IOException;
import java.util.List;

/** Narrow Java-upstream contract consumed by the Bedrock session orchestrator. */
public interface JavaSessionGateway extends AutoCloseable {
  /** Returns the clientbound Bedrock control packets unlocked by Java Login Success. */
  List<BedrockPlayPacket> loginPackets();

  /** Returns the empty resource-pack announcement after Bedrock encryption is active. */
  List<BedrockPlayPacket> resourcePackFlowStart();

  /** Returns the bounded Java world snapshot used by the external StartGame builder. */
  JavaWorldState worldState();

  /** Reads the upstream PLAY stream until the documented Play Login world boundary is present. */
  void awaitWorldReady()
      throws IOException, io.bedrockbridge.application.javawire.JavaWireException;

  /** Pumps one Java PLAY packet and returns only translator-approved Bedrock packets. */
  List<BedrockPlayPacket> pumpPlayOnce()
      throws IOException, io.bedrockbridge.application.javawire.JavaWireException;

  @Override
  void close() throws IOException;
}
