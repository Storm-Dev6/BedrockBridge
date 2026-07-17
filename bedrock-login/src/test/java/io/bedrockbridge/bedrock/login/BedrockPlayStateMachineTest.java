package io.bedrockbridge.bedrock.login;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.bedrockbridge.bedrock.BedrockPlayState;
import io.bedrockbridge.bedrock.BedrockProtocol;
import io.bedrockbridge.bedrock.BedrockValidationException;
import io.bedrockbridge.bedrock.packet.play.ClientToServerHandshakePacket;
import io.bedrockbridge.bedrock.packet.play.LoginPacket;
import io.bedrockbridge.bedrock.packet.play.RequestNetworkSettingsPacket;
import io.bedrockbridge.bedrock.packet.play.ResourcePackClientResponsePacket;
import io.bedrockbridge.bedrock.packet.play.ResourcePackResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class BedrockPlayStateMachineTest {
  @Test
  void reachesPlayReadyThroughTheEmptyPackVerticalFlow() {
    var flow = new BedrockPlayStateMachine();

    flow.receive(new RequestNetworkSettingsPacket(BedrockProtocol.NETWORK_PROTOCOL_748));
    assertEquals(BedrockPlayState.LOGIN, flow.state());
    flow.receive(new LoginPacket(BedrockProtocol.NETWORK_PROTOCOL_748, new byte[] {1}));
    assertEquals(BedrockPlayState.AUTHENTICATING, flow.state());
    flow.receive(new ClientToServerHandshakePacket());
    assertEquals(BedrockPlayState.RESOURCE_PACKS, flow.state());
    flow.receive(response(ResourcePackResponse.DOWNLOADING_FINISHED, List.of()));
    flow.receive(response(ResourcePackResponse.RESOURCE_PACK_STACK_FINISHED, List.of()));
    assertEquals(BedrockPlayState.STARTING_PLAY, flow.state());
    flow.startGameSent();

    assertEquals(BedrockPlayState.PLAY_READY, flow.state());
  }

  @Test
  void acceptsProgressForARealResourcePackBeforeTheStack() {
    var flow = authenticatedFlow();

    flow.receive(response(ResourcePackResponse.DOWNLOADING, List.of("pack-id")));
    flow.receive(response(ResourcePackResponse.DOWNLOADING_FINISHED, List.of()));
    flow.receive(response(ResourcePackResponse.RESOURCE_PACK_STACK_FINISHED, List.of()));

    assertEquals(BedrockPlayState.STARTING_PLAY, flow.state());
  }

  @Test
  void rejectsWrongVersionsDuplicatePacketsAndReorderedPackResponses() {
    var flow = new BedrockPlayStateMachine();
    assertThrows(
        BedrockValidationException.class,
        () -> flow.receive(new RequestNetworkSettingsPacket(749)));
    assertEquals(BedrockPlayState.NETWORK_SETTINGS, flow.state());
    flow.receive(new RequestNetworkSettingsPacket(BedrockProtocol.NETWORK_PROTOCOL_748));
    assertThrows(
        BedrockValidationException.class,
        () -> flow.receive(new RequestNetworkSettingsPacket(BedrockProtocol.NETWORK_PROTOCOL_748)));

    var packs = authenticatedFlow();
    assertThrows(
        BedrockValidationException.class,
        () ->
            packs.receive(response(ResourcePackResponse.RESOURCE_PACK_STACK_FINISHED, List.of())));
    assertEquals(BedrockPlayState.RESOURCE_PACKS, packs.state());
    packs.receive(response(ResourcePackResponse.DOWNLOADING_FINISHED, List.of()));
    assertThrows(
        BedrockValidationException.class,
        () -> packs.receive(response(ResourcePackResponse.DOWNLOADING_FINISHED, List.of())));
  }

  @Test
  void cancellationAndExplicitDisconnectFollowTerminalStateRules() {
    var cancelled = authenticatedFlow();
    cancelled.receive(response(ResourcePackResponse.CANCEL, List.of()));
    assertEquals(BedrockPlayState.DISCONNECTING, cancelled.state());
    cancelled.completeDisconnect();
    assertEquals(BedrockPlayState.DISCONNECTED, cancelled.state());
    assertThrows(BedrockValidationException.class, cancelled::completeDisconnect);

    var explicit = new BedrockPlayStateMachine();
    explicit.beginDisconnect();
    explicit.completeDisconnect();
    assertEquals(BedrockPlayState.DISCONNECTED, explicit.state());
  }

  private static BedrockPlayStateMachine authenticatedFlow() {
    var flow = new BedrockPlayStateMachine();
    flow.receive(new RequestNetworkSettingsPacket(BedrockProtocol.NETWORK_PROTOCOL_748));
    flow.receive(new LoginPacket(BedrockProtocol.NETWORK_PROTOCOL_748, new byte[] {1}));
    flow.receive(new ClientToServerHandshakePacket());
    return flow;
  }

  private static ResourcePackClientResponsePacket response(
      ResourcePackResponse response, List<String> packs) {
    return new ResourcePackClientResponsePacket(response, packs);
  }
}
