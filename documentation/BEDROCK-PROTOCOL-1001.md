# Bedrock protocol 1001 compatibility work package

## Target and clean-room provenance

This work package adds an exact early-session catalog for Minecraft Bedrock network protocol
`1001`, published by Mojang on the `r/26_u3` documentation branch and used by the `26.33` hotfix
client and dedicated server. The implementation was derived from Mojang's public packet diagrams,
JSON schemas, and an isolated observation of the official server, without source or generated
packet files from another bridge.

- Mojang protocol documentation: <https://github.com/Mojang/bedrock-protocol-docs/tree/r/26_u3>
- inspected branch commit: `5cd260a8a3573d24cbb93332a3d278e134d43734`
- Minecraft 26.33 hotfix notes:
  <https://feedback.minecraft.net/hc/en-us/articles/47262667617677-Minecraft-Bedrock-Edition-26-33-Hotfix-Changelog>
- official Bedrock Dedicated Server: `bedrock-server-1.26.33.2.zip`;
- observed server archive SHA-256:
  `a0b0af71e398cd761d74c9d64c82681d3c65b4dc78a51e85babc953c554984e6`.

## Supported boundary

The bridge advertises protocol `1001` during RakNet offline discovery and selects an exact play
codec after reading `RequestNetworkSettings`. Protocol `748` remains accepted for direct clients;
the two versions use separate registrations and fail closed for unknown network versions.

The discovery response uses RakNet's unsigned 16-bit big-endian MOTD byte length. A live 26.33
phone trace first exposed the previous VarUInt prefix. After that framing fix, the client still
repeated `UnconnectedPing` (`0x01`) without sending `OpenConnectionRequest1` (`0x05`) and reported
U-000. An isolated official server observation identified the remaining compatibility fields: the
advertised version is `1.26.33`, followed by the observed trailer `0;1;0;` after the IPv6 port.

For the default bridge advertisement, the resulting UTF-8 payload is 101 bytes, so its RakNet
length prefix is `00 65`. The full value is
`MCPE;BedrockBridge;1001;1.26.33;0;100;<guid>;BedrockBridge;Survival;1;19132;19133;0;1;0;`.

The RakNet server GUID is generated from the complete nonzero 64-bit value space on each bridge
startup and is rendered as an unsigned decimal value in the advertisement. This matches repeated
observations of the official server and prevents clients from retaining stale compatibility state
under a process-independent fixed server identity.

A subsequent phone trace still stopped at repeated discovery pings. Windows reported the listener
as an IPv6 wildcard socket even though `bridge.bind-address=0.0.0.0` requested IPv4. The UDP
transport now opens the configured protocol family explicitly, retains a datagram when a
non-blocking send temporarily returns zero, and logs completed handshake transmissions. An
isolated IPv4 probe verified a `127.0.0.1` listener, the complete 136-byte `0x1c` response, and a
matching `UDP handshake datagram transmitted` record.

The next real-client trace completed both offline connection requests and then sent a connected
RakNet DATA datagram with flag byte `0x84`. DATA is a flag family (`0x80` through `0x9f` when the
ACK/NACK bits are clear), not the single value `0x80`. Connected routing and session decoding now
classify this family before processing the embedded connection request.

After the RakNet connection reached `CONNECTED`, the client immediately sent a connected keepalive
packet before its first Bedrock game batch. Connected control payloads such as `ConnectedPing` and
`DisconnectNotification` are now decoded by the RakNet control state machine instead of being
misclassified as `0xfe` game batches. A ping receives a framed `ConnectedPong` without disturbing
the play session.

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

The assembled JAR is launched on an isolated UDP port and queried with a RakNet discovery probe
before deployment. A physical 26.33 client retest remains the next external validation step.
