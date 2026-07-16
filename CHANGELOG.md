# Changelog

A projekt a [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) formátumot és Semantic Versioninget követ.

## [Unreleased]

### Added

- Java 21 multi-module Gradle Kotlin DSL build convention pluginekkel és version cataloggal.
- CI minőségi kapuk: JUnit 5, Spotless, Checkstyle és Error Prone.
- Testcontainers tesztplatform-függőségek a jövőbeli infrastruktúra-integrációkhoz.
- Típusos exception hierarchy, dependency container, event bus és bounded scheduler.
- Szigorú properties-alapú configuration loader és production validator.
- SLF4J/Logback logging és Micrometer lifecycle metrics.
- Stabil plugin API alap, standalone bootstrap és determinisztikus application lifecycle.
- Konkurens shutdown rekurzió nélküli koordinációja és listenerhiba esetén is garantált erőforrás-takarítás.
- Gradle wrapper validation és teljes artifact-generálás a CI buildben.
- Hordozható Gradle runtime policy és a Phase 1 `clean check` elfogadási parancs explicit CI végrehajtása.
- Saját, Netty nélküli Java NIO UDP listener és bounded sender queue.
- Direct `ByteBuffer` pool, UDP transport API és wrap-aware 24 bites sequence aritmetika.
- RakNet frame codec, ACK/NACK range codec, sliding receive window és prioritásos packet queue.
- Bounded split packet fragmenter/reassembler, MTU policy és 32 ordering/sequencing channel.
- RTT/RTO estimator, recovery queue, ACK/NACK-alapú retransmission és retry limit.
- Session- és connection manager periodikus tickkel, keepalive-val, timeouttal és disconnect lifecycle-lal.
- Edition-semleges generikus `Packet` API protocol version, state, direction és encode/decode szerződéssel.
- Bounds-checked packet reader/writer, codec/encoder/decoder/factory és pooled packet allocator.
- Switch nélküli dinamikus packet, codec, serializer, deserializer, protocol, version, state és ID registry.
- Explicit compatibility matrix és fail-closed atomic protocol state machine.
- Immutable inbound/outbound packet pipeline, typed context és dinamikus packet dispatcher.
- Registry-driven protocol session packet processor trailing-byte és metadata validációval.
- JMH benchmarkok packet encode/decode, registry lookup és pipeline throughput mérésére.
- Bedrock/RakNet transport handshake packet catalog a ping/pong, open connection, connection request, incoming és disconnect packetekkel.
- Offline magic, IPv4/IPv6 address és teljes datagram serializer/deserializer bounds és trailing-byte validációval.
- Fail-closed RakNet protocol version negotiation, MTU/security/GUID validation és handshake state machine.
- Bedrock UDP session bootstrap, connection timeout, connected ping/pong keepalive válasz és determinisztikus disconnect lifecycle.
- Strict, bounded JSON és compact ES384 JWT parser/verifier dependency nélküli login feldolgozáshoz.
- Pinned-root identity chain, időablak-, identity-, client-data- és replay-validáció minimalizált auth eredménnyel.
- P-384 ephemeral ECDH és aláírt server handshake JWT egyedi session salt használatával.
- AES-256/CFB8 packet encryption monotonic counterrel és constant-time nyolcbyte-os integrity ellenőrzéssel.
- Bounded zlib/no-compression codec abszolút méret-, ratio-, trailing-byte- és truncation védelemmel.
- Fail-closed authentication state machine, amely a ciphert csak kliens handshake acknowledgement után aktiválja.
