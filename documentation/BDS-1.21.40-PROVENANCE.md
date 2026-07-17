# BDS 1.21.40 clean-room provenance plan

> NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.

## Current evidence state

No Bedrock Dedicated Server distribution was downloaded, opened, executed, or used to produce a
registry during this work package. Consequently there is no production BDS file hash or extracted
registry to record yet. The official download page currently exposes only the live and preview
channels and does not identify an archived 1.21.40 package. A community archive is not an acceptable
substitute.

The repository contains only the independently authored provenance generator and synthetic test
fixtures. Neither CI nor Gradle downloads BDS. A real manifest must be generated outside every Git
work tree from a user-supplied package whose exact official download URL was retained at acquisition
time.

## Allowed input and provenance record

The input is either the original BDS ZIP or its unmodified extracted directory. It must be acquired
separately from an HTTPS URL hosted by Microsoft, Mojang, or Minecraft. The generator accepts only
the `1.21.40` release line and records:

- the asserted full BDS version;
- the exact official acquisition URL;
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
locally executed BDS process. A later extractor may observe only the protocol-748 StartGame item
registry on loopback and normalize its three documented fields: identifier string, signed 16-bit
runtime ID, and component-based flag. It must not inspect process memory, inject code, patch or
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
