# Phase 2.5 — Protocol Framework

## Státusz és hatókör

A Phase 2.5 review-ra váró, teljesen edition-semleges protokoll-keretrendszer. Nem definiál Bedrock-, Java-, login-, encryption-, resource-pack- vagy gameplay packetet. Phase 3 külön elfogadás nélkül nem kezdhető.

## Modulok

| Modul | Felelősség |
|---|---|
| `protocol-common` | `Packet`, metadata értékobjektumok, reader/writer API |
| `packet-codec` | ByteBuffer reader/writer, encoder/decoder/codec/factory és allocator |
| `packet-registry` | packet/codec/serializer/deserializer/ID/protocol/version/state registry és compatibility |
| `packet-pipeline` | stage/context, inbound/outbound pipeline és dispatcher |
| `protocol-session` | state machine, protocol session és packet processor |
| `protocol-benchmarks` | JMH throughput suite |

## Packet szerződés

Minden packet megadja a nemnegatív ID-t, pontos `ProtocolVersion`-t, `ProtocolState`-et és `PacketDirection`-t, továbbá `encode(PacketWriter)` és `decode(PacketReader)` műveletet. A frameworkben nincs edition-specifikus típus vagy feltétel. Decode előtt a factory friss példányt készít; a codec ebbe olvas, majd a processor elutasít minden trailing byte-ot. Encode előtt a packet teljes metadata kulcsát a regisztrációhoz egyezteti.

## Registry és versioning

A teljes packet kulcs `(family, protocolId, state, direction, packetId)`. A `DynamicPacketRegistry` két concurrent indexet tart packet key és packet class szerint, és atomikusan elutasít minden kulcs- vagy típusütközést. A codec, serializer, deserializer és packet-ID registry ugyanennek az authoritative registrynek read-only vetülete, ezért nem divergálhat.

A `ProtocolRegistry` és `VersionRegistry` family + wire protocol ID szerint dolgozik; marketing verzió csak a `ProtocolVersion.name`. A `CompatibilityMatrix` explicit directed pairt engedélyez, ismeretlen pár fail-closed inkompatibilis. Ez alkalmas Bedrock 1.21.x és Java 1.21.x pontos protocol ID-jainak későbbi regisztrálására anélkül, hogy a framework bármelyiket ismerné.

## State machine

A rendelkezésre álló állapotok: `HANDSHAKE`, `STATUS`, `LOGIN`, `CONFIGURATION`, `PLAY`, `DISCONNECTED`. A `StateRegistry` csak explicit directed átmeneteket enged. A `ProtocolStateMachine` CAS ciklussal, atomikusan vált, tiltott átmenetre típusos lifecycle hibát ad. A `ProtocolSession` minden packet verzióját, állapotát és irányát a pipeline előtt ellenőrzi.

## Codec és I/O biztonság

Az I/O ByteBuffer-backed és bounds-checked. A VarInt maximum öt byte és csak nemnegatív érték; a UTF-8 string byte-limitje dekódolás előtt ellenőrzött; slice csak teljes rendelkezésre álló hosszból készül. A writer overflow előtt hibát ad. A `PacketAllocator` maximum packet méretet kényszerít ki, és a Phase 2 direct buffer poolt használja.

## Pipeline és dispatch

Az inbound/outbound pipeline immutable stage snapshotot futtat sorrendben. Stage packetet transzformálhat vagy explicit eldobhat. A `PipelineContext` immutable routing metadata mellett runtime-típus ellenőrzött attribute keyt ad. A `PacketDispatcher` exact packet class szerint, concurrent mapből dispatchol; sem registry, sem dispatcher nem használ packet switch-case-et.

## Benchmarkok

A `ProtocolFrameworkBenchmark` JMH `Throughput` módban, művelet/másodperc egységben méri:

- packet encode;
- factory + packet decode;
- teljes kulcsú registry lookup;
- egy stage-es pipeline throughput.

A benchmark fixture edition-semleges és ugyanazt a production `PacketProcessor`/registry/pipeline kódot használja. Benchmark eredmény nem funkcionalitási teszt és nem része a normál unit-test gate-nek.

## Tesztek és acceptance

Modulonként unit teszt ellenőrzi a metadata értékeket, bounds-checked VarInt/string I/O-t, dinamikus registryt és collision policy-t, pipeline/dispatcher sorrendet, valamint state machine tiltott/engedett átmeneteit. A teljes gate `./gradlew --no-daemon clean check assemble`; a JMH suite külön futtatandó a benchmark artifact classpathján.

## Phase boundary

**STOP:** konkrét Bedrock vagy Java packet, login, encryption, resource pack és fordítási kód nem kezdhető a Phase 2.5 review-ja, zöld CI-je és explicit jóváhagyása előtt.
