package io.bedrockbridge.bedrock.login;

import io.bedrockbridge.bedrock.BedrockPlayState;
import io.bedrockbridge.bedrock.BedrockProtocol;
import io.bedrockbridge.bedrock.BedrockValidationException;
import io.bedrockbridge.bedrock.packet.play.BedrockPlayPacket;
import io.bedrockbridge.bedrock.packet.play.ClientToServerHandshakePacket;
import io.bedrockbridge.bedrock.packet.play.LoginPacket;
import io.bedrockbridge.bedrock.packet.play.RequestNetworkSettingsPacket;
import io.bedrockbridge.bedrock.packet.play.ResourcePackClientResponsePacket;
import io.bedrockbridge.bedrock.packet.play.ResourcePackResponse;
import io.bedrockbridge.protocol.ProtocolVersion;
import java.util.Objects;

/**
 * Session-confined state validator for the protocol-748 network-settings, login, authentication,
 * resource-pack, and play-start sequence.
 */
public final class BedrockPlayStateMachine {
  private BedrockPlayState state = BedrockPlayState.NETWORK_SETTINGS;
  private ResourcePackStage resourcePackStage = ResourcePackStage.NONE;
  private ProtocolVersion protocolVersion;

  /** Applies one legal serverbound control packet without choosing outbound packet ordering. */
  public void receive(BedrockPlayPacket packet) {
    Objects.requireNonNull(packet, "packet");
    if (packet instanceof RequestNetworkSettingsPacket request) {
      receiveNetworkSettings(request);
      return;
    }
    if (packet instanceof LoginPacket login) {
      receiveLogin(login);
      return;
    }
    if (packet instanceof ClientToServerHandshakePacket) {
      receiveClientHandshake();
      return;
    }
    if (packet instanceof ResourcePackClientResponsePacket response) {
      receiveResourcePackResponse(response);
      return;
    }
    throw violation("Packet is not a play-flow control packet");
  }

  /** Marks a successfully encoded and queued StartGame packet as the play-ready boundary. */
  public void startGameSent() {
    require(BedrockPlayState.STARTING_PLAY);
    state = BedrockPlayState.PLAY_READY;
  }

  /** Begins deterministic protocol disconnect handling from any live state. */
  public void beginDisconnect() {
    if (state == BedrockPlayState.DISCONNECTING || state == BedrockPlayState.DISCONNECTED) {
      throw violation("Disconnect has already started");
    }
    resourcePackStage = ResourcePackStage.NONE;
    state = BedrockPlayState.DISCONNECTING;
  }

  /** Completes transport cleanup after a protocol disconnect was initiated. */
  public void completeDisconnect() {
    require(BedrockPlayState.DISCONNECTING);
    state = BedrockPlayState.DISCONNECTED;
  }

  /** Returns the current protocol flow state. */
  public BedrockPlayState state() {
    return state;
  }

  /** Returns the exact network protocol selected by RequestNetworkSettings. */
  public ProtocolVersion protocolVersion() {
    if (protocolVersion == null) {
      throw violation("Bedrock network version has not been negotiated");
    }
    return protocolVersion;
  }

  private void receiveNetworkSettings(RequestNetworkSettingsPacket request) {
    require(BedrockPlayState.NETWORK_SETTINGS);
    protocolVersion = BedrockProtocol.playVersion(request.clientNetworkVersion());
    state = BedrockPlayState.LOGIN;
  }

  private void receiveLogin(LoginPacket login) {
    require(BedrockPlayState.LOGIN);
    requireVersion(login.clientNetworkVersion());
    state = BedrockPlayState.AUTHENTICATING;
  }

  private void receiveClientHandshake() {
    require(BedrockPlayState.AUTHENTICATING);
    resourcePackStage = ResourcePackStage.AWAITING_DOWNLOAD_STATUS;
    state = BedrockPlayState.RESOURCE_PACKS;
  }

  private void receiveResourcePackResponse(ResourcePackClientResponsePacket packet) {
    require(BedrockPlayState.RESOURCE_PACKS);
    ResourcePackResponse response = packet.response();
    switch (response) {
      case CANCEL -> {
        resourcePackStage = ResourcePackStage.NONE;
        state = BedrockPlayState.DISCONNECTING;
      }
      case DOWNLOADING -> {
        requireResourcePackStage(ResourcePackStage.AWAITING_DOWNLOAD_STATUS);
      }
      case DOWNLOADING_FINISHED -> {
        requireResourcePackStage(ResourcePackStage.AWAITING_DOWNLOAD_STATUS);
        resourcePackStage = ResourcePackStage.AWAITING_STACK_STATUS;
      }
      case RESOURCE_PACK_STACK_FINISHED -> {
        requireResourcePackStage(ResourcePackStage.AWAITING_STACK_STATUS);
        resourcePackStage = ResourcePackStage.NONE;
        state = BedrockPlayState.STARTING_PLAY;
      }
      default -> throw violation("Unknown resource-pack response");
    }
  }

  private void requireVersion(int version) {
    if (!BedrockProtocol.playVersion(version).equals(protocolVersion)) {
      throw violation("Unsupported Bedrock network version: " + version);
    }
  }

  private void require(BedrockPlayState expected) {
    if (state != expected) {
      throw violation("Expected play state " + expected + " but was " + state);
    }
  }

  private void requireResourcePackStage(ResourcePackStage expected) {
    if (resourcePackStage != expected) {
      throw violation("Expected resource-pack stage " + expected + " but was " + resourcePackStage);
    }
  }

  private BedrockValidationException violation(String message) {
    return new BedrockValidationException(message);
  }

  private enum ResourcePackStage {
    NONE,
    AWAITING_DOWNLOAD_STATUS,
    AWAITING_STACK_STATUS
  }
}
