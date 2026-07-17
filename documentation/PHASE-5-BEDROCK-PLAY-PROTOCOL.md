# Phase 5 Bedrock Play Protocol

## Status

Implementation in progress on `agent/phase-5-bedrock-play-protocol`. Phase A is present on `main`,
and the Java 21 baseline command `gradlew.bat --no-daemon clean check assemble` passes before any
Phase 5 change.

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
