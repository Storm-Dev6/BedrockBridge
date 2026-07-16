# Phase 2 — RakNet Core implementation

## Hatókör és státusz

A Phase 2 saját, clean-room, Netty nélküli transportmagot valósít meg Java 21 NIO felett. Nem tartalmaz Bedrock packetet, login chain-t, titkosítást, Java protokollt vagy gameplay fordítást. A modul review-ra vár; Phase 3 külön jóváhagyás nélkül nem kezdhető.

## Modulok

| Modul | Felelősség |
|---|---|
| `packet-buffer` | fix méretű direct buffer pool, bounded retention, idempotens lease release |
| `network-core` | UDP datagram/transport API és wrap-aware unsigned 24 bites aritmetika |
| `udp-transport` | single-selector NIO UDP receive loop, bounded MPSC send queue és szabályos leállás |
| `network-raknet` | frame/ACK codec, receive window, fragmentáció, ordering, MTU, RTT/RTO, recovery és queue |
| `session` | endpoint routing, session lifecycle, tick, keepalive, timeout, ACK/NACK és disconnect |

## Adatút

```text
DatagramChannel → NioUdpTransport → SessionManager
  → RakNetSession → ReceiveWindow → FrameCodec
  → SplitPacketAssembler → OrderingChannels → frame consumer

frame producer → PacketQueue → FrameCodec → RecoveryQueue
  → NioUdpTransport → DatagramChannel
```

Az UDP I/O egy platform threaden fut; virtual thread nem indokolt, mert a selector nem blokkol feladatonként. Session lookup `ConcurrentHashMap`, a session algoritmusok session-confined/szinkronizált állapotot használnak. Minden queue, receive window, recovery entry, fragment assembly és buffer cache explicit korlátos.

## Reliability és kontrollforgalom

- A 24 bites indexek fél-intervallumos, wrap-aware aritmetikát használnak.
- A receive window egyszer fogad el egy sequence-et, és távoli future vagy régi értéket elutasít.
- A hiányzó datagramok NACK, az elfogadott datagramok ACK singleton range-ekben, legfeljebb 512 rekordos batchben távoznak.
- Reliable datagram immutable másolata a recovery queue-ban marad ACK-ig.
- A Jacobson/Karels estimator bounded RTO-t ad; timeout és NACK retransmissiont indít exponenciális backoffal.
- Retry exhaustion eltávolítja a recovery entryt és `RETRY_EXHAUSTED` okkal kontrolláltan bontja a sessiont.

## Fragment, ordering és MTU

A `FrameFragmenter` az MTU payload budget szerint legfeljebb 4096 részre bont. A reassembler split ID, reliability és channel kulcsot használ; byte-, assembly- és lifetime budgetet kényszerít ki, eltérő duplikátumot elutasít. Az ordered channel future frame-et bounded mapben tart, a hiány beérkezésekor folytonosan ürít. Sequenced frame-ből csak a legfrissebb epoch értelmezhető. Az `MtuPolicy` kizárólag a lokális maximum és a megfigyelt probe közös, bizonyított minimumát engedi.

## Kapacitás és teljesítmény

- A `SessionManager` konfigurálható maximuma 1 000 000, production célja legalább 1000 egyidejű session.
- A socket hot path nem használ reflectiont vagy Nettyt.
- A receive buffer direct és pooled; outbound datagram snapshot azért saját byte array, hogy az aszinkron send queue ownershipja egyértelmű legyen.
- A queue-k boundedek és telítettségnél `false` visszatéréssel backpressure-t jeleznek.
- Egy selector thread és egy konfigurálható tick scheduler szolgál ki minden sessiont; sessionönként nincs thread.

## Tesztstratégia

Modulonként unit teszt ellenőrzi a pool reuse-t, 24 bites wrapet, valódi loopback UDP küldést, frame/ACK round-tripet, sliding window deduplikációt, out-of-order reassemblyt, orderinget, fragment payload budgetet, recovery ACK-ot, MTU-t, session létrehozást és timeoutot. A teljes Gradle gate: `./gradlew --no-daemon clean check assemble`.

## Phase boundary

**STOP:** Phase 3 vagy Bedrock-protokoll implementáció nem kezdődhet a Phase 2 review-ja, zöld CI-je és explicit elfogadása előtt.
