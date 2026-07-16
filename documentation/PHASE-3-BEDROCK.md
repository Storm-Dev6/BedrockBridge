# Phase 3 — Bedrock Protocol Foundation

## Státusz és hatókör

A Phase 3 elfogadott. Célja kizárólag egy Bedrock kliens RakNet transportkapcsolatának felépítése a saját Phase 2 stack és Phase 2.5 framework felett. A login és encryption a Phase 4 külön rétege; játékállapot továbbra sincs.

## Modulok

| Modul | Felelősség |
|---|---|
| `bedrock-common` | offline magic, RakNet version, packet ID és validation exception |
| `bedrock-packets` | handshake packet modellek és IPv4/IPv6 address codec |
| `bedrock-codec` | packet registry, teljes datagram codec és semantic validator |
| `bedrock-login` | version negotiation és session-confined handshake state machine |
| `bedrock-session` | UDP admission, session bootstrap, routing, timeout és cleanup |

## Támogatott packetek

| ID | Packet | Irány | Szerep |
|---:|---|---|---|
| `0x00` | `ConnectedPing` | serverbound | kapcsolat aktivitás és keepalive kérés |
| `0x03` | `ConnectedPong` | clientbound | ping timestamp visszaigazolása |
| `0x05` | `OpenConnectionRequest1` | serverbound | RakNet version és datagram-MTU próba |
| `0x06` | `OpenConnectionReply1` | clientbound | version elfogadás, server GUID és MTU |
| `0x07` | `OpenConnectionRequest2` | serverbound | endpoint, végleges MTU és client GUID |
| `0x08` | `OpenConnectionReply2` | clientbound | endpoint/MTU elfogadás |
| `0x09` | `ConnectionRequest` | serverbound | connected identity és timestamp kérés |
| `0x10` | `ConnectionRequestAccepted` | clientbound | cím- és timestamp szinkronizáció |
| `0x13` | `NewIncomingConnection` | serverbound | transportkapcsolat végleges kliens ack |
| `0x15` | `DisconnectNotification` | serverbound | kontrollált peer disconnect |

## Datagram codec és validáció

A `BedrockDatagramCodec` a packet ID-t és az offline packetek 16 byte-os magic értékét kezeli, majd a Phase 2.5 `PacketProcessor` segítségével factory/codec alapján dolgozik. Decode után trailing byte nem maradhat. A request 1 MTU-ja a datagram paddingból származik. Az address codec támogatja az invertált IPv4 és a RakNet IPv6 struktúrát.

A `BedrockPacketValidator` fail-closed módon ellenőrzi a RakNet version 11-et, az MTU policy-t, a nemnulla client GUID-ot és a letiltott transport security flaget. Az unknown packet ID, hibás magic, rövid payload, invalid boolean/address és state-en kívüli packet kapcsolatbontást okoz.

## Handshake state machine

```text
NEW
 → MTU_NEGOTIATED          OpenConnectionRequest1 / Reply1
 → OFFLINE_ACCEPTED        OpenConnectionRequest2 / Reply2
 → CONNECTION_REQUESTED    ConnectionRequest / Accepted
 → CONNECTED               NewIncomingConnection
 → DISCONNECTED            timeout, validation error vagy notification
```

A második MTU kérés nem emelheti az első körben bizonyított MTU-t, a client GUID nem változhat, és packet nem ugorhat át állapotot. `CONNECTED` állapotban a ping azonnali pong választ kap. Inaktív session konfigurált timeout után megszűnik.

## Session bootstrap

A `BedrockSessionBootstrap` a bounded UDP transportból endpoint szerint hoz létre sessiont, konfigurált maximum session limit mellett. Az offline requesteket közvetlenül dekódolja és válaszolja meg. A Phase 2 RakNet reassembly által előállított connected payload a `receiveConnected` belépési ponton érkezik. A periodikus tick eltávolítja a timeoutolt/bontott sessionöket; shutdown minden sessiont bont és lezárja a transportot.

## Tesztek

Modulonként unit teszt ellenőrzi a magic/version konstansokat, IPv4 address round-tripet, request padding/MTU codec-et, offline magicet, unsupported version elutasítását, a teljes állapotsorrendet, session reply-t és timeoutot. A production source külön Java 21 `-Xlint:all -Werror` és doclint ellenőrzést kap.

## Kizárások és stop pont

Nincs play, inventory, entity, chunk, command vagy text packet; nincs Minecraft login packet, encryption, Xbox Live vagy resource pack flow. **Phase 4 külön, explicit jóváhagyás nélkül nem kezdhető.**
