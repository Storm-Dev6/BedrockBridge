package io.bedrockbridge.application.translation;

import io.bedrockbridge.bedrock.BedrockPacketIds;
import io.bedrockbridge.bedrock.BedrockPlayState;
import io.bedrockbridge.bedrock.BedrockProtocol;
import io.bedrockbridge.bedrock.BedrockProtocolLimits;
import io.bedrockbridge.bedrock.BedrockValidationException;
import io.bedrockbridge.bedrock.codec.BedrockBatchCodec;
import io.bedrockbridge.bedrock.codec.BedrockCompressionCodec;
import io.bedrockbridge.bedrock.codec.BedrockPacketFrame;
import io.bedrockbridge.bedrock.codec.BedrockPacketFrameCodec;
import io.bedrockbridge.bedrock.codec.BedrockPlayCodec;
import io.bedrockbridge.bedrock.codec.BedrockPlayCodecFactory;
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
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bridges one ordered RakNet payload to the typed Bedrock play session. */
public final class BedrockConnectedPlayAdapter implements ConnectedFrameHandler {
  private static final int GAME_PACKET = 0xFE;
  private static final Logger LOGGER = LoggerFactory.getLogger(BedrockConnectedPlayAdapter.class);
  private final BedrockJavaSession session;
  private final BedrockProtocolLimits limits;
  private BedrockPlayCodec playCodec;
  private final BedrockPacketFrameCodec frames;
  private final BedrockBatchCodec batches;
  private final BedrockCompressionCodec compression;

  /** Creates an adapter that selects an exact supported codec from RequestNetworkSettings. */
  public BedrockConnectedPlayAdapter(BedrockJavaSession session) {
    this.session = Objects.requireNonNull(session, "session");
    limits = BedrockProtocolLimits.defaults();
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
    LOGGER.info(
        "Bedrock connected batch bytes={} frames={} state={}",
        clear.length,
        incoming.size(),
        session.state());
    List<BedrockPacketFrame> outgoing = new ArrayList<>();
    for (BedrockPacketFrame frame : incoming) {
      BedrockPlayCodec selectedCodec = selectCodec(frame);
      LOGGER.info(
          "Bedrock serverbound play packet id=0x{} bytes={} state={}",
          String.format("%02x", frame.header().packetId()),
          frame.payloadLength(),
          session.state());
      BedrockPlayPacket packet =
          selectedCodec
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
    LOGGER.info("Bedrock clientbound batch frames={} state={}", outgoing.size(), session.state());
    byte[] batch = batches.encode(outgoing);
    byte[] encodedBatch = compression.compress(batch);
    byte[] connected = new byte[encodedBatch.length + 1];
    connected[0] = (byte) GAME_PACKET;
    System.arraycopy(encodedBatch, 0, connected, 1, encodedBatch.length);
    outbound.accept(ByteBuffer.wrap(session.encryptConnected(connected)));
  }

  @Override
  public void close() {
    session.close();
  }

  private BedrockPacketFrame framesForTyped(BedrockPlayPacket packet, BedrockPlayState state) {
    if (playCodec == null) {
      throw new BedrockValidationException("Bedrock play codec has not been negotiated");
    }
    return frames.decode(playCodec.encode(packet, state, 0, 0));
  }

  private BedrockPlayCodec selectCodec(BedrockPacketFrame frame) {
    if (playCodec != null) {
      return playCodec;
    }
    if (session.state() != BedrockPlayState.NETWORK_SETTINGS
        || frame.header().packetId() != BedrockPacketIds.REQUEST_NETWORK_SETTINGS) {
      throw new BedrockValidationException(
          "RequestNetworkSettings must select the Bedrock play codec first");
    }
    ByteBuffer payload = frame.payload().order(ByteOrder.BIG_ENDIAN);
    if (payload.remaining() != Integer.BYTES) {
      throw new BedrockValidationException("RequestNetworkSettings payload size is invalid");
    }
    int networkVersion = payload.getInt();
    playCodec = BedrockPlayCodecFactory.create(BedrockProtocol.playVersion(networkVersion), limits);
    return playCodec;
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
