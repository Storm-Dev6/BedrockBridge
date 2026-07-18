# Phase 5 Bedrock Play Protocol

## Status

Implementation is in progress on `agent/phase-5-bedrock-play-protocol`. P5.1 framing/registry and
P5.2 network-settings/login/resource-pack control packets are implemented and validated. The
Bedrock session orchestrator now authenticates a login, opens the selected Java upstream, consumes
the bounded Java Play Login world boundary, and exposes translated Java PLAY pumping. The Java 21
gate `gradlew.bat --no-daemon clean check assemble` is green. StartGame remains fail-closed at the
external protocol-748 registry-artifact boundary until a separately approved local artifact is
available.

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

`BedrockJavaSession` is the current bounded orchestration seam. It decodes typed Bedrock control
packets, applies the configured authentication policy, connects a `JavaSessionGateway`, waits for
the Java Play Login packet before exposing the Bedrock login success, sends the server handshake,
and starts the empty resource-pack flow. A `StartGameFrameProvider` must return one already-framed,
validated protocol-748 StartGame packet; a missing provider, malformed frame, or external-registry
failure produces `BLOCKED_EXTERNAL_OFFICIAL_ARTIFACT` and a deterministic Bedrock Disconnect. After
the StartGame boundary, `pumpJavaOnce()` forwards only translator-approved Java PLAY outputs and
propagates bounded Java disconnect reasons. This seam is covered with a synthetic offline-auth
integration test; it does not retain Microsoft/Xbox tokens or private login keys.

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

### P5.3: play-ready orchestration and external StartGame gate

- Connect Bedrock login/authentication/resource-pack control flow to the Java upstream world-ready
  boundary without inventing registry or StartGame fields.
- Require a version-checked external StartGame frame provider and keep the session fail-closed until
  the approved protocol-748 registry artifact is present.
- Expose one bounded Java PLAY pump for disconnect and translator-approved control output.
- Gate: synthetic network-settings -> login -> packs -> external StartGame gate tests pass and every
  failure path produces a deterministic disconnect.

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

Phase 5 establishes a bounded control-flow and upstream world-state seam, not a claim of playable
Bedrock spawn. Chunk, entity, inventory, movement, chat, command, UI, and Java-edition translation
packets belong to later phases. The live RakNet connected-data adapter now dispatches reassembled,
ordered game payloads into this orchestrator and fragments outbound batches at the negotiated
MTU-safe bound. A real Bedrock spawn additionally requires a configured Bedrock authentication trust
policy, the approved external protocol-748 registry artifact, and a manual client test. Snappy
compression and additional Bedrock 1.21.x protocol numbers are explicit future deltas, not silently
accepted aliases of protocol 748.

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
The composition root also exposes an explicit connected-DATA handler-factory overload; a deployment
must inject its own pinned Bedrock certificate trust policy, upstream connector, and validated
StartGame provider through that seam. The default launcher intentionally leaves the factory unset
until those external inputs exist.

### Work-package record: Bedrock-to-Java session seam

- Commit: `121acff` (`connect Bedrock session flow to Java world gateway`), pushed to
  `agent/phase-5-bedrock-play-protocol`.
- Local validation: `gradlew.bat --no-daemon clean check assemble` and the complete Gradle test
  suite passed under Java 21; `BedrockJavaSessionTest` proves the synthetic control-flow boundary.
- CI: GitHub Actions run `29645478549` completed successfully (build job `88082891050`).
- Result: Bedrock authentication policy, Java world-ready wait, encryption-handshake/resource-pack
  ordering, bounded Java PLAY pump, and safe Java disconnect forwarding are implemented. The
  StartGame provider remains fail-closed on `BLOCKED_EXTERNAL_OFFICIAL_ARTIFACT`.
- Next gate: configure the Bedrock authentication trust policy and perform a manual Bedrock-client
  StartGame/spawn test with the separately approved external registry. No main-branch merge was
  performed.

### Work-package record: connected RakNet DATA pipeline

- The handshake-admitted `BedrockSession` now creates a bounded `RakNetSession`, routes DATA/ACK/NACK
  datagrams through its receive window, split assembler, ordering channels, and recovery queue, and
  flushes acknowledgements immediately.
- Outbound connected payloads are split into 1,440-byte reliable-ordered fragments before enqueueing;
  the application `BedrockConnectedPlayAdapter` decodes the `0xFE` game marker, negotiated zlib/raw
  batch, typed protocol-748 packets, and re-encodes the session output.
- Regression coverage is in `bedrock-session/src/test/.../BedrockSessionTest.java` and
  `application/src/test/.../BedrockConnectedPlayAdapterTest.java`. No StartGame fields or registry
  entries are fabricated.

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

### Java PLAY Login and minimal world model

Clientbound Java 1.21.1 protocol 767 Play Login (`0x2B`) is decoded with bounded counts,
identifier validation, strict booleans, supported game-mode ranges, and the documented optional
death location. The session retains entity ID, hardcore/debug/flat flags, dimension identifiers,
player/view/simulation distances, respawn and secure-chat flags, current dimension, hashed seed,
game mode, previous game mode, portal cooldown, and optional death location. Truncation, invalid
ranges, malformed identifiers, and trailing bytes fail closed with the packet ID and reason. Field
definitions are taken from the public [protocol 767 reference](https://wikivg.booky.dev/Protocol).

`JavaWorldState` records the decoded login, default spawn, chunk-cache center/radius, simulation
distance, time, chunk-batch lifecycle, bounded chunk columns, light updates, and known chunk
coordinates. It does not synthesize block, biome, heightmap, light, or entity data.
The first verified world-state packets are Set Chunk Cache Center (`0x54`), Set Default Spawn
Position (`0x56`), Change Difficulty (`0x0B`), Entity Event (`0x1F`), Set Carried Item (`0x53`),
and Server Data (`0x4B`, retaining only icon byte count and never icon bytes). The bounded Chunk Data
and Update Light codecs are available as `0x27` and `0x2A`; section palettes are retained as
registry-ID references and block-entity NBT is bounded.

### First remaining protocol boundary

The transport reaches Java `PLAY` against the tested Paper 1.21.1 server. The verified trace of the
first ten clientbound PLAY packet IDs is:

`0x2B, 0x0B, 0x38, 0x53, 0x77, 0x1F, 0x41, 0x40, 0x4B, 0x6C`.

In packet names, this is Play Login, Change Difficulty, Player Abilities, Set Carried Item,
Update Recipes, Entity Event, Update Recipe Book, Synchronize Player Position, Server Data, and
System Chat. The trace is from the local Paper `paper-1.21.1-133.jar` server (protocol 767,
`online-mode=false`). The official protocol assigns Commands to `0x11` and Synchronize Player
Position to `0x40`; there is no separate `0x40` Recipe packet in protocol 767.

The codec handles the bounded Play Login and the known keep-alive (`0x26`), disconnect (`0x1D`),
game event (`0x22`), player abilities (`0x38`), synchronize-player-position (`0x40`), system chat
(`0x6C` anonymous NBT component), world-state packets listed above, and chunk batch lifecycle
(`0x0C`/`0x0D`). A position update is answered with Confirm Teleportation (`0x00`), and keep-alives
are echoed with the serverbound PLAY ID (`0x18`). Update Recipe Book (`0x41`) is decoded into its
bounded state and recipe identifiers, Commands (`0x11`) validates the directed graph and known
parser properties, and Update Recipes (`0x77`) is consumed only as a bounded opaque packet because
full recipe/item component semantics are outside this slice. Chunk Data (`0x27`) parses sections,
heightmaps, block entities, palettes, and light masks; Update Light (`0x2A`) validates masks and
2048-byte arrays. Bundle Delimiter (`0x00`) and Spawn Entity (`0x01`) are decoded with bounded
coordinates, UUID, entity type, angles, data, and velocity fields. Update Attributes (`0x75`)
validates the documented attribute IDs, finite doubles, modifier identifiers, and operation range;
Entity Effect (`0x76`) validates effect, amplifier, duration, and flags. An extended Paper trace
reached chunk/light packets and these entity packets. Section Blocks Update (`0x49`), Block Update
(`0x09`), Remove Entities (`0x42`), Set Experience (`0x5C`), and Set Health (`0x5D`) are also
decoded with numeric bounds. Entity Metadata (`0x58`) is retained only as a bounded opaque frame;
its metadata type/value schema is not interpreted and therefore cannot yet be translated to
Bedrock. The first remaining semantic boundary in the extended Paper trace is Update Advancements
(`0x74`), whose advancement display/icon Slot structures require versioned item and registry data.
Unsupported or semantically dependent PLAY packets fail closed with the exact hexadecimal packet ID,
while the trace utility records packet order without decoding unknown payloads. StartGame construction on Bedrock remains fail-closed on
`BLOCKED_EXTERNAL_OFFICIAL_ARTIFACT`.

The synthetic socket harness proves PLAY packet ordering and the keep-alive/position response
encoders. The opt-in Paper harness captures the first received PLAY IDs in order and records the
first unsupported ID/reason, so a real server test never treats an unknown payload as decoded.

## Java Configuration verification

Configuration now sends Client Information and Known Packs, and validates clientbound Registry
Data (`0x07`), Feature Flags (`0x0C`), Update Tags (`0x0D`), keep-alive (`0x04`), and Finish
Configuration (`0x03`). Registry entry NBT is decoded with bounded depth, element counts, string
sizes, and all standard tag kinds. Invalid identifiers, negative tag IDs, malformed NBT, unknown
tag kinds, and unknown packet IDs fail closed with the exact hexadecimal packet ID.

The opt-in test `JavaRealServerManualTest` was run against the existing local Paper
`paper-1.21.1-133.jar` (Java protocol 767) with `online-mode=false`, `server-port=25565`, and
`enforce-secure-profile=true`. The server accepted the handshake and offline Login Start, then the
bridge reached Configuration and sent Client Information. Configuration packet `0x01` (clientbound
plugin message) is bounded and consumed; Registry Data, Feature Flags, Update Tags, keep-alive, and
Finish Configuration also completed. The test reached Java `PLAY`, and the Paper log recorded the
offline player joining and leaving normally. No unsupported packet was encountered in this run; the
server process was stopped and its original `online-mode=true` setting restored afterward.

For manual reproduction, temporarily set `online-mode=false` in the local Java server's
`server.properties`, keep `server-port=25565`, and run:

```text
$env:BEDROCKBRIDGE_REAL_JAVA='true'
$env:BEDROCKBRIDGE_TRACE_LIMIT='10'
./gradlew.bat --no-daemon :application:test --tests io.bedrockbridge.application.javawire.JavaRealServerManualTest
```

The bridge properties must still include every required field from
`application/src/main/resources/bridge.properties.example`, including an externally generated
protocol-748 registry path, version, and SHA-256. CI never downloads or stores a Java server binary.

### Manual Bedrock path

The bridge can be exercised with a real Bedrock client only after a user-generated, validated
protocol-748 registry is available outside the work-tree. Copy the example properties, set the
Bedrock listener and Java upstream, point the registry path/protocol/digest at that external file,
start Paper with `online-mode=false`, then run the fat JAR:

```text
java -jar application/build/libs/BedrockBridge-0.1.0-SNAPSHOT.jar path/to/bridge.properties
```

Connect the Bedrock client to the configured UDP listener. The expected path is Network Settings,
Login/auth-chain validation, empty resource-pack exchange, Java upstream login/configuration/PLAY,
and then the Bedrock StartGame gate. Without the external registry, the gate reports
`BLOCKED_EXTERNAL_OFFICIAL_ARTIFACT` and no guessed item data is sent. The next required external
input is therefore either that registry artifact or a real Bedrock-client observation; CI never
downloads BDS or a Java server.

## Static multi-upstream routing

Named upstream definitions use the unbounded `bridge.upstream.<name>.*` property namespace. Each
entry requires `address` and `port`, with optional `connect-timeout-ms` and `read-timeout-ms`.
Listener mappings use `bridge.listener.<bedrock-port>.upstream`; the runtime starts one UDP
listener per mapping and `connectJavaUpstream(port, username)` resolves that listener to its named
Java endpoint. There is no hardcoded seven-server limit; the example contains seven named
upstreams (`lobby`, `survival`, `skyblock`, `creative`, `minigames`, `events`, `testing`) and seven
additional Bedrock ports. The legacy single-upstream properties remain valid and create a `default`
mapping automatically. Dynamic server selection is intentionally not part of this slice.

## External registry check

The registry import interface is `ExternalItemRegistryLoader`; its strict implementation accepts
only the three-field NDJSON artifact, an exact protocol version, and a caller-supplied SHA-256.
The artifact must remain outside the Git work-tree. Validate it offline with the generator module:

```text
./gradlew.bat --no-daemon :bedrock-registry-generator:run --args="--artifact C:/external/items-748.ndjson --protocol 748 --sha256 <64-lowercase-hex>"
```

The command prints protocol, artifact SHA-256, byte count, item count, `duplicates`, and
`missing-required` fields. `BridgeLauncher` invokes the same `RegistryCheckCli.validate` preflight
before admitting a session or allowing the StartGame path. It never downloads BDS or stores the
artifact in Gradle/CI outputs.
