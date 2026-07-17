package io.bedrockbridge.bedrock;

/** Session-confined Minecraft protocol states after the RakNet connection is established. */
public enum BedrockPlayState {
  NETWORK_SETTINGS,
  LOGIN,
  AUTHENTICATING,
  RESOURCE_PACKS,
  STARTING_PLAY,
  PLAY_READY,
  DISCONNECTING,
  DISCONNECTED
}
