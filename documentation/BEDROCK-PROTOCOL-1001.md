# Bedrock protocol 1001 compatibility work package

## Target and clean-room provenance

This work package adds an exact early-session catalog for Minecraft Bedrock network protocol
`1001`, published by Mojang on the `r/26_u3` documentation branch for Minecraft `1.26.30` and used
by the `26.33` hotfix client. The implementation was derived from Mojang's public packet diagrams
and JSON schemas, without source or generated packet files from another bridge.

- Mojang protocol documentation: <https://github.com/Mojang/bedrock-protocol-docs/tree/r/26_u3>
- inspected branch commit: `5cd260a8a3573d24cbb93332a3d278e134d43734`
- Minecraft 26.33 hotfix notes:
  <https://feedback.minecraft.net/hc/en-us/articles/47262667617677-Minecraft-Bedrock-Edition-26-33-Hotfix-Changelog>

## Supported boundary

The bridge advertises protocol `1001` during RakNet offline discovery and selects an exact play
codec after reading `RequestNetworkSettings`. Protocol `748` remains accepted for direct clients;
the two versions use separate registrations and fail closed for unknown network versions.

The discovery response uses RakNet's unsigned 16-bit big-endian MOTD byte length. A live 26.33
phone trace exposed the previous VarUInt prefix: the client repeated `UnconnectedPing` (`0x01`)
without sending `OpenConnectionRequest1` (`0x05`) and reported U-000. The corrected response has
the prefix `00 5F` before the 95-byte `MCPE;BedrockBridge;1001;...` advertisement.

The protocol-1001 catalog covers the documented connection-control path:

- RequestNetworkSettings and NetworkSettings;
- Login and PlayStatus;
- ServerToClientHandshake and ClientToServerHandshake;
- Disconnect's protocol-1001 discriminated message representation;
- the valid empty ResourcePacksInfo, ResourcePackStack, and ResourcePackClientResponse flow.

Protocol-neutral Java packet values are bound to an exact wire layout by the selected registry.
This allows schemas that are identical in 748 and 1001 to share immutable values without claiming
that their wire registrations are interchangeable.

## Fail-closed StartGame boundary

The existing external item registry and StartGame encoder are verified only for protocol `748`.
They are not sent to a protocol-1001 client. A 1001 session that reaches the spawn boundary is
rejected with `START_GAME_UNAVAILABLE_PROTOCOL_1001_REGISTRY` until a separately observed and
validated protocol-1001 registry artifact and StartGame layout are available.

This boundary prevents a 748 frame from being mislabeled as 1001 and gives the live phone test a
deterministic next development point.

## Verification

The Java 21 verification command is:

```text
gradlew.bat --no-daemon clean check assemble
```

It covers exact protocol-1001 byte vectors, version mismatch rejection, both 748 and 1001 adapter
flows through encryption activation, discovery advertisement, lifecycle port isolation, Spotless,
Checkstyle, Error Prone, unit tests, Javadoc, and the application fat JAR.

The assembled JAR was also launched on an isolated UDP port and queried with a RakNet discovery
probe. It returned packet `0x1C`, the `00 5F` length prefix, the complete 95-byte advertisement,
and no trailing bytes. A physical 26.33 client retest remains the next external validation step.
