package io.bedrockbridge.application.translation;

import io.bedrockbridge.bedrock.BedrockPlayState;
import io.bedrockbridge.bedrock.BedrockProtocolLimits;
import io.bedrockbridge.bedrock.BedrockValidationException;
import io.bedrockbridge.bedrock.codec.BedrockBatchCodec;
import io.bedrockbridge.bedrock.codec.BedrockCompressionCodec;
import io.bedrockbridge.bedrock.codec.BedrockPacketFrame;
import io.bedrockbridge.bedrock.codec.BedrockPacketFrameCodec;
import io.bedrockbridge.bedrock.codec.BedrockPlayCodec;
import io.bedrockbridge.bedrock.codec.BedrockProtocol748PacketRegistry;
import io.bedrockbridge.bedrock.codec.CompressionAlgorithm;
import io.bedrockbridge.bedrock.codec.CompressionSettings;
import io.bedrockbridge.bedrock.packet.play.BedrockPlayPacket;
import io.bedrockbridge.bedrock.packet.play.DisconnectPacket;
import io.bedrockbridge.bedrock.packet.play.NetworkSettingsPacket;
import io.bedrockbridge.bedrock.packet.play.ResourcePackStackPacket;
import io.bedrockbridge.bedrock.packet.play.ResourcePacksInfoPacket;
import io.bedrockbridge.bedrock.packet.play.ServerToClientHandshakePacket;
import io.bedrockbridge.bedrock.session.ConnectedFrameHandler;
import io.bedrockbridge.protocol.PacketDirection;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Bridges one ordered RakNet payload to the typed Bedrock play session. */
public final class BedrockConnectedPlayAdapter implements ConnectedFrameHandler {
  private static final int GAME_PACKET = 0xFE;
  private final BedrockJavaSession session;
  private final BedrockProtocolLimits limits;
  private final BedrockPlayCodec playCodec;
  private final BedrockPacketFrameCodec frames;
  private final BedrockBatchCodec batches;
  private final BedrockCompressionCodec compression;

  /** Creates an adapter for the exact protocol-748, zlib/512 NetworkSettings contract. */
  public BedrockConnectedPlayAdapter(BedrockJavaSession session) {
    this.session = Objects.requireNonNull(session, "session");
    limits = BedrockProtocolLimits.defaults();
    playCodec =
        new BedrockPlayCodec(
            io.bedrockbridge.bedrock.BedrockProtocol.PLAY_VERSION_748,
            limits,
            BedrockProtocol748PacketRegistry.create(limits));
    frames = new BedrockPacketFrameCodec(limits);
    batches = new BedrockBatchCodec(limits);
    compression =
        new BedrockCompressionCodec(
            new CompressionSettings(
                CompressionAlgorithm.ZLIB,
                512,
                limits.maximumConnectedPayloadBytes(),
                limits.maximumDecompressedBatchBytes(),
                limits.maximumCompressionRatio()));
  }

  @Override
  public void handle(ByteBuffer payload, Consumer<ByteBuffer> outbound) {
    Objects.requireNonNull(payload, "payload");
    Objects.requireNonNull(outbound, "outbound");
    byte[] received = new byte[payload.remaining()];
    payload.duplicate().get(received);
    byte[] clear = session.decryptConnected(received);
    if (clear.length < 2 || Byte.toUnsignedInt(clear[0]) != GAME_PACKET) {
      throw new IllegalArgumentException("Connected payload is not a Bedrock game batch");
    }
    List<BedrockPacketFrame> incoming =
        batches.decode(decodeBatchPayload(Arrays.copyOfRange(clear, 1, clear.length)));
    List<BedrockPacketFrame> outgoing = new ArrayList<>();
    for (BedrockPacketFrame frame : incoming) {
      BedrockPlayPacket packet =
          playCodec
              .decode(frames.encode(frame), session.state(), PacketDirection.SERVERBOUND)
              .packet();
      BedrockSessionOutput result = session.receive(packet);
      for (BedrockPlayPacket typed : result.packets()) {
        outgoing.add(framesForTyped(typed, encodingState(typed)));
      }
      if (result.startGameFrame() != null) {
        outgoing.add(frames.decode(result.startGameFrame()));
      }
    }
    if (outgoing.isEmpty()) {
      return;
    }
    byte[] batch = batches.encode(outgoing);
    byte[] encodedBatch = compression.compress(batch);
    byte[] connected = new byte[encodedBatch.length + 1];
    connected[0] = (byte) GAME_PACKET;
    System.arraycopy(encodedBatch, 0, connected, 1, encodedBatch.length);
    outbound.accept(ByteBuffer.wrap(session.encryptConnected(connected)));
  }

  private BedrockPacketFrame framesForTyped(BedrockPlayPacket packet, BedrockPlayState state) {
    return frames.decode(playCodec.encode(packet, state, 0, 0));
  }

  private byte[] decodeBatchPayload(byte[] payload) {
    try {
      return compression.decompress(payload);
    } catch (BedrockValidationException compressedFailure) {
      return payload.clone();
    }
  }

  private BedrockPlayState encodingState(BedrockPlayPacket packet) {
    if (packet instanceof NetworkSettingsPacket) {
      return BedrockPlayState.NETWORK_SETTINGS;
    }
    if (packet instanceof ServerToClientHandshakePacket) {
      return BedrockPlayState.AUTHENTICATING;
    }
    if (packet instanceof ResourcePacksInfoPacket || packet instanceof ResourcePackStackPacket) {
      return BedrockPlayState.RESOURCE_PACKS;
    }
    if (packet instanceof DisconnectPacket) {
      return session.state();
    }
    return session.state();
  }
}
