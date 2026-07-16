# Phase 1 — Foundation implementation

## Státusz

**Implementálva; review-ra vár.** Phase 2 nem kezdhető a build és acceptance gate jóváhagyása előtt.

## Megvalósított modulok

- `common`: exception hierarchy, argument checks, explicit type-keyed dependency injection, synchronous ordered event bus és fixed-size scheduler.
- `config`: immutable configuration record, UTF-8 properties loader, unknown-key rejection és cross-field production policy.
- `observability`: backendfüggetlen SLF4J belépő és alacsony kardinalitású Micrometer lifecycle metrikák.
- `api`: minimális, immutable extension context és process lifecycle események.
- `application`: composition root, CLI bootstrap, egyszer indítható és idempotensen leállítható lifecycle.
- `build-logic`: Java 21 convention, JUnit 5/Testcontainers test platform, Spotless, Checkstyle és Error Prone.

## Dependency injection döntés

A Phase 1 nem vezet be reflection-alapú DI frameworköt. A `ServiceContainer` kizárólag a composition rootban használható, contractonként pontosan egy instance-t regisztrál, duplikált vagy hiányzó bindingra fail-fast hibát ad. A doménosztályok továbbra is explicit constructor injectiont használnak. Ez megőrzi a startup determinisztikusságát és nem teszi service locatorrá a belső kódot.

## Lifecycle

```text
NEW → STARTING → RUNNING → STOPPING → STOPPED
              ↘ FAILED ──────↗
```

A startup csak `NEW` állapotból érvényes. A scheduler és az observability a bridge előtt készül el. A sikeres indulás eseményt és metrikát publikál. A shutdown hook ugyanazt az idempotens `close` útvonalat használja; új munka a scheduler leállása után nem vehető fel.

## Konfigurációs szerződés

A properties loader minden kötelező kulcsot megkövetel, ismeretlen kulcsot elutasít, a numerikus és boolean értékeket szigorúan parse-olja. A record strukturális tartományt ellenőriz, a validator pedig cross-field/security policy-t. Titok nincs a Phase 1 konfigurációban.

## Minőségi kapu

Kötelező helyi parancs: `./gradlew --no-daemon clean check`. A CI pontosan ezt az elfogadási kaput futtatja, majd az `assemble` taskkal elkészíti a source/Javadoc artifactokat. A kapu fordítja az összes modult, futtatja az összes unit tesztet, valamint a Spotless-, Checkstyle- és Error Prone ellenőrzést. A CI JDK 21-et és külön hivatalos Gradle wrapper validation actiont használ, hiba esetén pedig megőrzi a tesztriportokat.

## Kizárások

Nincs UDP listener, RakNet, Bedrock/Java codec vagy packet, authentication, mapping, registry és gameplay fordítás. Ezek Phase 2 vagy későbbi feladatok.
