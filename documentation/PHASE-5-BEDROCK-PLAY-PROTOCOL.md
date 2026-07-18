# Phase 5 Bedrock Play Protocol

## Status

Implementation is in progress on `agent/phase-5-bedrock-play-protocol`. P5.1 framing/registry and
P5.2 network-settings/login/resource-pack control packets are implemented and validated. The Java
21 gate `gradlew.bat --no-daemon clean check assemble` is green. P5.3 is intentionally paused at
the StartGame item-list boundary until a locally generated registry is explicitly approved.

## Supported protocol

Phase 5 targets exactly one Bedrock wire version:

- Minecraft Bedrock 1.21.40, the Bundles of Bravery release;
- Mojang release line `r/21_u4`;
- Bedrock network protocol `748`;
- RakNet transport protocol `11`.

Version negotiation is exact and fail closed. Network protocol values other than `748` receive a
version-appropriate failure status and disconnect; codecs never guess a nearby layout. Later 1.21.x
versions will be added as explicit version deltas after this vertical slice is green.

The correspondence between the public 1.21.40 release and Mojang's adjacent `r/21_u4` protocol
publication is an explicit project inference from the primary sources below. The authoritative wire
identity used by code and tests is the unambiguous Mojang network protocol number `748`.

## Primary sources and clean-room provenance

Only public, first-party protocol facts are used:

- [Mojang Bedrock protocol documentation](https://github.com/Mojang/bedrock-protocol-docs)
- [Mojang protocol 748 publication](https://github.com/Mojang/bedrock-protocol-docs/pull/15)
- [Mojang protocol 748 documentation tree](https://github.com/Mojang/bedrock-protocol-docs/tree/ca7b330f4a94e907d82bfe0b1595adff56c8f0c9)
- [Minecraft Bedrock 1.21.40 release notes](https://feedback.minecraft.net/hc/en-us/articles/31222183227149-Minecraft-Bedrock-Edition-1-21-40-Bundles-of-Bravery)

The Mojang repository is consulted as documentation only. No source file, generated packet file,
mapping, registry dump, or runtime dependency is copied into BedrockBridge. Packet classes, codecs,
limits, registries, state transitions, and test vectors are independently authored here. GeyserMC,
Floodgate, and every other Bedrock-to-Java bridge remain prohibited both as source material and as
dependencies.

## Phase 5 acceptance slice

A connected RakNet session must progress through network-settings negotiation, login decoding and
authentication handoff, an empty resource-pack exchange, typed StartGame construction, and a
play-ready state. The implementation must also send a protocol Disconnect on every rejected path
where the wire state permits it.

The first packet catalog contains the following protocol-748 packets:

| ID | Packet | Direction | Legal state |
|---:|---|---|---|
| 1 | Login | serverbound | `LOGIN` |
| 2 | PlayStatus | clientbound | `LOGIN`, `PLAY` |
| 3 | ServerToClientHandshake | clientbound | `AUTHENTICATING` |
| 4 | ClientToServerHandshake | serverbound | `AUTHENTICATING` |
| 5 | Disconnect | clientbound | all connected play states |
| 6 | ResourcePacksInfo | clientbound | `RESOURCE_PACKS` |
| 7 | ResourcePackStack | clientbound | `RESOURCE_PACKS` |
| 8 | ResourcePackClientResponse | serverbound | `RESOURCE_PACKS` |
| 11 | StartGame | clientbound | `STARTING_PLAY` |
| 143 | NetworkSettings | clientbound | `NETWORK_SETTINGS` |
| 193 | RequestNetworkSettings | serverbound | `NETWORK_SETTINGS` |

Packet identity includes protocol version, play state, direction, packet ID, and Java type. Startup
must reject every ID or type collision before a session can be admitted.

## Architecture

Phase 5 extends the current modules instead of introducing an overlapping protocol module:

- `bedrock-common` owns the protocol-748 identity, play states, immutable limits, and structured
  failure categories;
- `bedrock-packets` owns typed packet and value models without transport or session behavior;
- `bedrock-codec` owns Bedrock little-endian primitives, packet headers, exact-version registry,
  bounded frames, batches, and negotiated zlib/no-compression handling;
- `bedrock-auth` continues to own strict login-chain parsing and identity validation;
- `bedrock-login` orchestrates the play login and resource-pack state transitions after RakNet;
- `bedrock-session` connects reassembled RakNet payloads to the play codec and session-confined
  orchestrator;
- edition-neutral `packet-*` and `protocol-*` modules remain free of Bedrock-specific semantics.

Dependency direction stays packet model -> codec -> orchestration -> session adapter. Network I/O
does not mutate play state directly, and no play module depends on translation, the application, or
future Java protocol modules.

```text
reassembled RakNet payload
          |
          v
bounded Bedrock frame/batch decoder
          |
          v
version + state + direction registry lookup
          |
          v
session-confined play orchestrator
          |
          +--> authentication boundary
          +--> resource-pack handshake
          +--> typed StartGame factory
          |
          v
bounded batch/compression encoder -> RakNet sender
```

## Play state model

The Bedrock play state is independent from the existing RakNet handshake state:

```text
NETWORK_SETTINGS
  -> LOGIN
  -> AUTHENTICATING
  -> RESOURCE_PACKS
  -> STARTING_PLAY
  -> PLAY_READY
  -> DISCONNECTING
  -> DISCONNECTED
```

Only declared forward transitions are legal. Duplicate, reordered, unknown, wrong-direction, and
wrong-state packets produce a structured violation and deterministic disconnect. `DISCONNECTED` is
terminal. Session teardown clears authentication material, pending pack data, decompression state,
and queued outbound buffers.

`BedrockPlayStateMachine` validates the complete inbound control sequence independently of outbound
packet construction. It distinguishes the resource-pack download and stack acknowledgements while
remaining in `RESOURCE_PACKS`, rejects reordered completion, and
reaches `PLAY_READY` only after the caller confirms that StartGame was encoded and queued. Outbound
login-status ordering is intentionally owned by the later orchestrator and is not inferred from
packet IDs.

## Wire model

Every game packet begins with Mojang's unsigned-varint header. Bits 0-9 contain the packet ID, bits
10-11 the sender sub-client ID, and bits 12-13 the target sub-client ID. Both sub-client IDs are
validated as two-bit values and retained in the decoded frame.

A connected game payload uses the Bedrock game-packet marker followed by a negotiated compression
payload. The uncompressed batch is a sequence of unsigned-varint byte lengths followed by exactly
that many packet bytes. Lengths are checked before slicing or allocation. A decoder rejects empty
entries, truncated entries, integer overflow, trailing partial entries, too many packets, and any
packet over its configured budget.

Protocol integer endianness and signed encodings are expressed by Bedrock-specific reader/writer
methods. Fields explicitly documented as big-endian (network versions and play status) are never
routed through little-endian helpers. Signed Bedrock varints use ZigZag coding; unsigned variants
reject overflow and non-canonical encodings.

The Login connection request is decoded as two signed little-endian 32-bit byte lengths followed by
the chain JSON and client-data JWT UTF-8 bytes. The decoder uses an isolated buffer view, validates
both lengths before slicing, reports malformed UTF-8, rejects trailing data, and hands the decoded
strings to the existing strict JSON/JWT authentication boundary.

Compression is immutable after a successful NetworkSettings exchange. Protocol 748 initially
supports zlib and explicit no-compression; Snappy is rejected until an audited implementation and
vectors are added. Decompression must finish a single stream, consume all compressed input, and
respect both absolute output and expansion-ratio budgets.

## Configurable security limits

Defaults are deliberately conservative and are constructor-injected so deployment configuration
can lower them without changing codecs:

| Limit | Default |
|---|---:|
| connected payload bytes | 2 MiB |
| encoded packet bytes | 1 MiB |
| packets per batch | 512 |
| decompressed batch bytes | 4 MiB |
| compression expansion ratio | 64:1 |
| login connection-request bytes | 1 MiB |
| general UTF-8 string bytes | 32 KiB |
| resource packs per list | 256 |
| resource-pack identifier bytes | 1 KiB |
| StartGame block/item entries | 65,536 each |

All size arithmetic is widened before addition. Limits are enforced before copying, allocating, or
iterating attacker-controlled counts. Exceptions and diagnostics contain category, packet ID,
protocol, direction, and state but never JWTs, keys, raw login payloads, or resource-pack content
keys.

## Implementation work packages

### P5.1: framing and registration

- Add the protocol-748 identity, packet IDs, play state graph, limits, and violation categories.
- Add Bedrock primitive readers/writers and exact packet-header encode/decode.
- Add the versioned play packet registry with collision checks.
- Add bounded packet frames and batch encode/decode with known vectors and malformed-input tests.
- Gate: affected modules pass unit tests, Spotless, Checkstyle, Error Prone, and assembly.

### P5.2: negotiation, login, and resource packs

- Implement RequestNetworkSettings, NetworkSettings, Login, PlayStatus, Disconnect,
  ResourcePacksInfo, ResourcePackStack, and ResourcePackClientResponse models/codecs.
- Integrate negotiated zlib/no-compression batches and the existing authentication boundary.
- Implement empty-pack and required-pack state paths, including refusal and invalid-response
  disconnects.
- Gate: byte round trips, independent known vectors, truncated/oversize/unknown/state tests, and
  login/resource-pack integration tests pass.

### P5.3: typed StartGame and play-ready orchestration

- Implement typed protocol-748 StartGame values, including level settings, experiments, movement
  authority, registries, UUIDs, and bounded NBT values needed by the packet.
- Add a deterministic minimal StartGame factory and play-ready orchestration.
- Connect the play session to reassembled RakNet payloads without changing transport ownership.
- Gate: a full network-settings -> login -> packs -> StartGame -> play-ready test passes and every
  failure path cleans up.

### P5.4: hardening and publication

- Add deterministic lightweight fuzz/property loops for varints, headers, batches, compression,
  strings, and packet dispatch.
- Run the full Java 21 `clean check assemble` gate and scan for prohibited markers.
- Self-review limits, state transitions, log redaction, dependency direction, and wrapper integrity.
- Commit and push logical units, verify GitHub Actions after every push, fix CI regressions, and open
  an unmerged pull request only when the Phase 5 gate is green.

## Test strategy

Tests use independently written byte vectors derived from field definitions, plus encode/decode
round trips. Round trips never stand alone as proof of wire correctness. Negative tests cover
truncation at every boundary, overlong and non-canonical varints, oversized strings/counts, unknown
IDs, registry collisions, wrong directions/states, invalid compression streams, compression bombs,
malformed login payloads, invalid resource-pack responses, duplicate transitions, and cleanup after
disconnect.

Fuzz/property tests are deterministic, seed-recorded, bounded in duration, and run in the ordinary
Gradle test lifecycle. They complement targeted vectors rather than hiding failures behind retries.

## Known scope boundary

Phase 5 establishes a production-quality Bedrock play-ready vertical slice, not all gameplay
translation. Chunk, entity, inventory, movement, chat, command, UI, and Java-edition translation
packets belong to later phases. Snappy compression and additional Bedrock 1.21.x protocol numbers
are also explicit future deltas, not silently accepted aliases of protocol 748.

## Current approval boundary

The current branch does not contain a StartGame packet implementation or a vanilla item registry.
The protocol-748 StartGame item list requires every vanilla item; an empty or guessed list would not
be interoperable. The external provenance manifest and BDS runtime verification are complete, but
the registry artifact remains local and uncommitted. Before implementing or staging that artifact,
the exact fields, source observation, necessity, redistribution risk, and local-artifact loading
path must be reviewed and approved separately. The external unchanged-BDS loopback probe now
performs the complete RakNet and NetworkSettings exchange and submits a synthetic Login, but the
1.21.40.03 server did not return a clientbound game packet during the bounded observation window.
No StartGame frame or production registry was therefore produced; the observer reports this
authentication/interop boundary without bypassing BDS validation.

The bounded diagnostic trace records RakNet handshake replies, RequestNetworkSettings packet 193,
NetworkSettings packet 143, Login packet 1, packet lengths, state transitions, and every subsequent
RakNet datagram ID. After Login,
only ACK/control datagrams were received; there was no packet 3 encryption request, packet 5
disconnect, resource-pack response, or StartGame packet. External BDS stdout/stderr was filtered
to lifecycle/authentication keywords and contained no rejection reason. This leaves the exact BDS
decision opaque while proving the last successful boundary and avoids treating a timeout as a
protocol fact.

## MVP continuation boundary

The real protocol-748 item registry is explicitly marked
`BLOCKED_EXTERNAL_OFFICIAL_ARTIFACT`. The observer and offline-login generator remain diagnostic
tools only; they do not block proxy development and never fabricate registry data.

The standalone application now fails closed unless a user-owned, versioned external registry path,
protocol version, and SHA-256 are configured. With a validated artifact it starts the Bedrock UDP
RakNet admission runtime, decodes Login connection requests, applies the configured online or
offline authentication policy, and exposes a typed Java-upstream state foundation for handshake,
status, login, configuration, and play. The developer distribution includes
`BedrockBridge-<version>.jar`; it contains no registry or Microsoft/Xbox credential material.

## Java upstream vertical slice

The branch now contains a clean-room Java TCP codec and blocking transport for the Java 1.21.1
wire (protocol 767): VarInt framing, big-endian fixed fields, UTF-8 bounds, optional zlib packet
compression, handshake, status/ping, offline login, login acknowledgement, known-packs,
configuration keep-alive, finish-configuration acknowledgement, and bounded disconnect reasons.
The field definitions were independently implemented from the published protocol description
([Java 1.21.1 protocol reference](https://wikivg.booky.dev/Protocol)); no bridge implementation or
proprietary test data is used.

The application composition root exposes the configured upstream address and port to a session
through `BedrockBridge.connectJavaUpstream(username)`. A successful offline upstream login yields
the downstream `LOGIN_SUCCESS` PlayStatus and an empty resource-pack catalog through the translator;
Java keep-alives are echoed and a bounded Java JSON reason is forwarded as a Bedrock disconnect.
Online-mode Java encryption/authentication is deliberately not emulated: an encryption request or
unsupported packet fails closed, without requesting or storing credentials.

### Manual socket test

Use a local Java 1.21.1 server with `online-mode=false` and the normal Java TCP port (25565), then
copy `application/src/main/resources/bridge.properties.example` and set:

* `bridge.upstream-address=127.0.0.1` and `bridge.upstream-port=25565`;
* a user-generated, externally stored protocol-748 registry path, protocol, and SHA-256;
* `bridge.offline-auth-mode=allow-self-signed` only when testing synthetic/offline Bedrock login.

Start with:

```text
java -jar application/build/libs/BedrockBridge-0.1.0-SNAPSHOT.jar path/to/bridge.properties
```

The repository integration harness uses real loopback TCP sockets and synthetic Java packet
fixtures. A real Bedrock client additionally requires the external registry and a manual client
test; the registry is never downloaded by Gradle or CI.

### First remaining protocol boundary

The transport reaches Java `PLAY` only for a server whose configuration stream is limited to the
implemented known-packs, keep-alive, and finish-configuration packets. A normal vanilla/Paper
1.21.1 server will next send additional configuration packets (notably registry data, feature
flags, tags, and client information). These are intentionally rejected as unsupported rather than
decoded from guessed fields. Consequently, the first remaining blocking state for a real server is
the configuration registry-data/feature stream, followed by StartGame construction on Bedrock,
which remains fail-closed on `BLOCKED_EXTERNAL_OFFICIAL_ARTIFACT`.

## Java Configuration verification

Configuration now sends Client Information and Known Packs, and validates clientbound Registry
Data (`0x07`), Feature Flags (`0x0C`), Update Tags (`0x0D`), keep-alive (`0x04`), and Finish
Configuration (`0x03`). Registry entry NBT is decoded with bounded depth, element counts, string
sizes, and all standard tag kinds. Invalid identifiers, negative tag IDs, malformed NBT, unknown
tag kinds, and unknown packet IDs fail closed with the exact hexadecimal packet ID.

The opt-in test `JavaRealServerManualTest` was run against the existing local Paper
`paper-1.21.1-133.jar` (Java protocol 767) with `online-mode=false`, `server-port=25565`, and
`enforce-secure-profile=true`. The server accepted the handshake and offline Login Start, then the
bridge reached Configuration and sent Client Information. The first real server packet not yet
implemented was Configuration packet `0x01` (clientbound plugin message); the test stopped there
with `unsupported configuration packet id=0x01`. This is the current documented boundary; the
server process was stopped and its original `online-mode=true` setting restored afterward.

For manual reproduction, temporarily set `online-mode=false` in the local Java server's
`server.properties`, keep `server-port=25565`, and run:

```text
$env:BEDROCKBRIDGE_REAL_JAVA='true'
./gradlew.bat --no-daemon :application:test --tests io.bedrockbridge.application.javawire.JavaRealServerManualTest
```

The bridge properties must still include every required field from
`application/src/main/resources/bridge.properties.example`, including an externally generated
protocol-748 registry path, version, and SHA-256. CI never downloads or stores a Java server binary.

## Static multi-upstream routing

Named upstream definitions use the unbounded `bridge.upstream.<name>.*` property namespace. Each
entry requires `address` and `port`, with optional `connect-timeout-ms` and `read-timeout-ms`.
Listener mappings use `bridge.listener.<bedrock-port>.upstream`; the runtime starts one UDP
listener per mapping and `connectJavaUpstream(port, username)` resolves that listener to its named
Java endpoint. There is no hardcoded seven-server limit; the example contains seven named
upstreams (`lobby`, `survival`, `skyblock`, `creative`, `minigames`, `events`, `testing`) and seven
additional Bedrock ports. The legacy single-upstream properties remain valid and create a `default`
mapping automatically. Dynamic server selection is intentionally not part of this slice.
