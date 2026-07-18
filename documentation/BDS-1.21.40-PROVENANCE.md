# BDS 1.21.40 clean-room provenance plan

> NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.

## Verified distribution

The user supplied an unchanged official Windows BDS archive outside every Git work tree. On
2026-07-17 it was opened read-only as a ZIP, its expected server executable was confirmed, and the
archive was hashed independently before any extraction:

- version: `1.21.40.03`;
- official acquisition URL:
  `https://www.minecraft.net/bedrockdedicatedserver/bin-win/bedrock-server-1.21.40.03.zip`;
- archive length: `37,129,683` bytes;
- archive SHA-256: `524d062b914b13740b7323486fd49dd0ca7fc87916318b6e0c1deb841379d99a`;
- archive inspection: `System.IO.Compression.ZipArchive.OpenRead` streamed all 8,036 entries
  (596 directories and 7,440 regular files; 123,095,622 uncompressed bytes) without an error;
- manifest inspection instant: `2026-07-17T20:20:04.700586300Z` UTC.

The locally generated provenance manifest contains 7,440 per-file digests, is 1,292,843 bytes,
and has SHA-256 `0ab6b14ca5727884e69de48189266b10aab056d5b15063857aabddd6beed919b`.
It remains outside the repository together with the BDS archive. No registry had been extracted at
the time of this provenance checkpoint.

The repository contains only the independently authored provenance generator, the public archive
identity above, and synthetic test fixtures. Neither CI nor Gradle downloads BDS, and a community
archive is not an acceptable substitute.

## Allowed input and provenance record

The input is either the original BDS ZIP or its unmodified extracted directory. It must be acquired
separately from an HTTPS URL hosted by Microsoft, Mojang, or Minecraft. The generator accepts only
the `1.21.40` release line and records:

- the asserted full BDS version;
- the exact official acquisition URL;
- the UTC instant at which the distribution was inspected;
- the SHA-256 and byte length of the original ZIP, or a deterministic aggregate digest for a
  directory;
- the normalized relative path, byte length, and SHA-256 of each distribution file;
- the manifest schema and the required product disclaimer.

Paths, hashes, and file inventories remain local provenance material. The CLI refuses BDS input and
manifest output located below a directory containing `.git`, and refuses to overwrite a manifest.
Archive content is streamed for hashing and is never extracted by the generator. Symbolic links,
path traversal, non-regular directory entries, excessive file counts, and distributions over the
configured eight-GiB safety budget are rejected.

Example invocation, with all paths outside the BedrockBridge checkout:

```text
gradlew.bat :bedrock-registry-generator:run --args="--input D:\legal-input\bedrock-server-1.21.40.zip --output D:\local-provenance\bds-provenance-1.21.40.json --version 1.21.40.03 --source-url https://www.minecraft.net/.../bedrock-server-1.21.40.03.zip"
```

## Proposed extraction method

The preferred observation boundary is the documented Bedrock protocol emitted by an unmodified,
locally executed BDS process. A later extractor may observe only the protocol-748 StartGame packet
(packet ID 11) on loopback and normalize its item-list entries using the three fields documented
by the authoritative `r/21_u4` publication: item name string, signed 16-bit item ID, and
component-based boolean
([Mojang protocol-748 ItemData definition](https://github.com/Mojang/bedrock-protocol-docs/blob/8dbf811fd0927c505a916e3d1c7d0ff830c0630c/html/ItemData.html)).
The newer protocol branches add fields that are not part of 748 and must not be guessed or copied
back into this generator. The extractor must not inspect process memory, inject code, patch or
modify executables, bypass access controls, disable signature checks, or parse unrelated game
content.

Before implementing that adapter, the exact 1.21.40 package must be available from a retained
official source and the launch/login procedure must be demonstrated without a Microsoft account or
manual client interaction. If either condition cannot be met, extraction remains blocked instead of
guessing registry values or using another bridge.

## Redistribution and licensing risk

The generator source, manifest schema, hashing logic, and synthetic tests are original
BedrockBridge work. Microsoft/Mojang retains rights in the BDS distribution and game data. A list of
vanilla identifiers and numeric runtime IDs may still be protected or contractually restricted even
when observed solely for interoperability. Hashes and local file inventories are provenance, not a
license to redistribute the underlying material.

No BDS file, excerpt, asset, configuration, registry dump, or production provenance manifest is
committed. No textures, localization, recipes, sounds, behavior packs, resource packs, or other game
content are in extraction scope. The default deployment design is a user-generated local artifact,
loaded after checksum and schema validation, so repository redistribution is not technically
required.

## Approval gate before registry publication

Before any real generated item registry is staged or committed, work stops for a new human review.
That review must enumerate the exact fields, their observation source, protocol necessity, likely
license conditions, and the local-artifact alternative. Without explicit approval after that report,
the production registry remains outside the repository.

## Synthetic verification

Ordinary tests build their own small directories and ZIP streams. They verify stable SHA-256
manifests, deterministic sorting, official-source and version validation, traversal rejection,
repository-boundary rejection, and the mandatory disclaimer. These tests contain no BDS-derived
bytes or metadata.

## Loopback observation checkpoint (2026-07-18)

The unchanged `bedrock_server.exe` from the verified distribution was launched in the isolated
external work directory `C:\Users\Gamestormzone\Documents\BDS\work\phase5-loopback-20260717-protocol748`.
The probe completed RakNet negotiation and the protocol-748 NetworkSettings exchange, then sent a
synthetic, locally signed Login request over the negotiated ZLIB connection. The trace proves
`RequestNetworkSettings` (packet 193) sent and `NetworkSettings` (packet 143) received, followed by
`Login` (packet 1) sent; no subsequent clientbound game packet arrived. Only RakNet ACK/control
datagrams (IDs 192/132) arrived until the bounded timeout. No disconnect packet, encryption request,
resource-pack packet, or BDS auth/error line was
observed. The last proven boundary is therefore `LOGIN_SENT`, not a rejected packet reason. No
StartGame frame was observed and no real registry artifact was created. The process was stopped
after the attempt.

This is an authentication/interop boundary, not permission to fabricate a packet or bypass BDS
validation. The repository therefore contains only the observer, extractor, and synthetic tests;
the three-field production registry remains absent and the next successful observation requires a
valid, lawfully obtained Bedrock login flow or an explicit protocol decision.
