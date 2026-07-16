# BedrockBridge

BedrockBridge egy önálló, Java 21-alapú Minecraft Bedrock–Java bridge. A repository jelenleg a **Phase 2 — RakNet Core** állapotban van: az infrastruktúra mellett elkészült az önálló NIO/UDP és RakNet transportmag; Bedrock- és Java-protokoll packetek még nincsenek.

## Követelmények

- JDK 21
- Git
- internetkapcsolat az első Gradle dependency-letöltéshez
- opcionálisan Docker a későbbi Testcontainers integrációs tesztekhez

## Build és ellenőrzés

```bash
./gradlew clean check
```

A `check` futtatja a JUnit 5 teszteket, Checkstyle-t, Spotless ellenőrzést és Error Prone fordítási analízist. A dependency catalog a Testcontainers tesztplatformot is minden modul számára elérhetővé teszi; a Phase 1 unit tesztek nem igényelnek Docker daemont.

A GitHub Actions a Gradle wrapper integritását külön ellenőrzi, majd a kötelező `clean check` kapu után az `assemble` taskkal a source- és Javadoc artifactokat is elkészíti. A repository Gradle-beállításai nem tartalmaznak gépspecifikus Java elérési utat; a build az aktív `JAVA_HOME` JDK 21 környezetét és a Java toolchaint használja.

## Futtatás

Másold az `application/src/main/resources/bridge.properties.example` fájlt írható helyre, majd:

```bash
./gradlew :application:run --args=/path/to/bridge.properties
```

A Phase 1 alkalmazás kizárólag a konfigurációt validálja, felépíti az infrastruktúra-szolgáltatásokat, lifecycle eseményt és metrikát publikál, majd szabályosan áll le. Portot még nem nyit.

## Modulok

| Modul | Phase 1 felelősség |
|---|---|
| `common` | exception hierarchy, DI container, event bus, scheduler, validációs utility |
| `config` | szigorú properties loader és konfigurációs policy |
| `observability` | SLF4J logging facade és Micrometer alapmetrikák |
| `api` | stabil extension lifecycle/context API és publikus események |
| `application` | composition root, bootstrap és application lifecycle |
| `build-logic` | egységes Java 21, JUnit, Testcontainers, Spotless, Checkstyle és Error Prone policy |
| `packet-buffer` | bounded direct `ByteBuffer` pool és biztonságos lease lifecycle |
| `network-core` | UDP transport kontraktusok, datagrammodell és 24 bites sequence aritmetika |
| `udp-transport` | saját, Netty nélküli Java NIO UDP listener/sender |
| `network-raknet` | frame codec, ACK/NACK, reliability, retransmission, MTU, fragment és ordering |
| `session` | session/connection manager, timeout, keepalive, tick és disconnect lifecycle |

## Projektfázis

Phase 0, Phase 0.5 és Phase 1 elfogadott. Phase 2 kizárólag transport-szintű RakNet kódot tartalmaz. Bedrock login, titkosítás, Java protokoll és gameplay fordítás csak későbbi, külön jóváhagyott fázisban kezdődhet.
