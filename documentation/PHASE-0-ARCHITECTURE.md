# BedrockBridge — Phase 0 szoftverarchitektúra

**Státusz:** elfogadva; a Phase 1 foundation implementáció elkészült és review-ra vár
**Célplatform:** Minecraft Bedrock és Java Edition 1.21.x
**Tervezett futtatókörnyezet:** Java 21
**A dokumentum határa:** kizárólag architektúra; implementációs kódot nem tartalmaz

## 1. A projekt célja és hatóköre

### 1.1 Cél

A BedrockBridge egy önálló, szerveroldali átjáró, amely lehetővé teszi, hogy támogatott Minecraft Bedrock 1.21.x kliensek módosítás nélkül csatlakozzanak támogatott Minecraft Java 1.21.x szerverekhez. A bridge a Bedrock UDP/RakNet kapcsolatot fogadja, külön Java TCP kapcsolatot nyit, majd állapottartó módon fordítja a hálózati protokollt, az entitásokat, a világállapotot, az inventory-műveleteket és a hitelesítési identitást.

### 1.2 A megoldandó probléma

A két kiadás protokollja nem huzal-kompatibilis:

- eltérő transzportot (Bedrock: UDP/RakNet; Java: TCP) és csomagkeretezést használ;
- eltérő login-, titkosítási, tömörítési és session-életciklusa van;
- azonos játékelemekhez eltérő runtime ID-kat, registryket és metaadatokat rendel;
- az entity-, chunk-, inventory- és interakciós modellek szemantikája sem egy az egyben feleltethető meg;
- az egyes 1.21.x patch verziók packet ID-i, mezői és registry-adatállománya változhat.

A rendszer nem byte-szintű proxy: két önálló kapcsolat között, normalizált belső modellen keresztül végez szemantikus fordítást.

### 1.3 Hatókör

**A kezdeti kiadás része:**

- standalone proxy üzemmód;
- egy konfigurálható Java upstream és több párhuzamos Bedrock session;
- státusz/lista, login, konfiguráció és play állapot kezelése;
- online és explicit fejlesztői/offline upstream mód, biztonságos alapbeállítással;
- alapvető világ-, chunk-, blokk-, entity-, player-, chat-, inventory- és movement-fordítás;
- erőforráscsomag-életciklus minimális támogatása;
- verzióadapterek támogatott 1.21.x protokollpárokhoz;
- metrikák, strukturált naplózás, diagnosztika és adminisztrációs API;
- opcionális Java szerverplugin a proxy és a backend közötti megbízható kiegészítő csatornához.

**Nem része az első stabil kiadásnak:**

- Bedrock vagy Java kliens/szerver binárisának módosítása;
- Mojang/Microsoft szolgáltatások emulációja vagy hitelesítés megkerülése;
- minden harmadik féltől származó mod, plugin és egyedi packet automatikus támogatása;
- több upstream közötti teljes proxyhálózat, lobby/routing rendszer;
- tökéletes grafikai vagy mechanikai azonosság ott, ahol a két kiadás képessége eltér.

### 1.4 Megkülönböztető tulajdonságok és függetlenség

A tervezett rendszer erősen típusos, verziózott protokolladaptereket, kanonikus köztes modellt, sessionönkénti actor-szerű feldolgozást és determinisztikus replay teszteket használ. A fordítási szabályok nem a hálózati kódban, hanem doménmodulokban vannak; így egy patch verzió vagy részrendszer izoláltan cserélhető.

Az implementáció clean-room jellegű: nyilvánosan dokumentált protokollviselkedésből, jogszerűen készített saját hálózati megfigyelésekből és saját tesztvektorokból készül. Nem másol harmadik fél forráskódot, mappinget vagy védett eszközt; minden külső könyvtár licencét és eredetét SBOM-ban tartjuk nyilván. A „független” nem jelent külső függőségek nélküli rendszert: a kriptográfiai, naplózási és tesztkönyvtárakat tudatosan használjuk, de a bridge fordítási logikája saját implementáció.

## 2. High-level architektúra

```text
┌──────────────────┐
│  Bedrock Client  │
└────────┬─────────┘
         │ UDP / RakNet, Bedrock packets
┌────────▼──────────────────────────────┐
│ Bedrock Network Layer                │
│ listener, RakNet, framing, crypto    │
└────────┬──────────────────────────────┘
         │ typed, versioned packet events
┌────────▼──────────────────────────────┐
│ Protocol Translation Engine          │
│ session state + canonical model      │
│ auth │ world │ entity │ inventory    │
└────────┬──────────────────────────────┘
         │ typed, versioned packet commands
┌────────▼──────────────────────────────┐
│ Java Network Layer                   │
│ TCP, framing, compression, crypto    │
└────────┬──────────────────────────────┘
         │ Java protocol
┌────────▼─────────┐
│ Minecraft Server│
└──────────────────┘

Keresztmetszeti szolgáltatások:
configuration │ lifecycle │ observability │ security │ extension API
```

### 2.1 Fő komponensek

1. **Bootstrap és lifecycle:** konfigurációbetöltés, dependency wiring, listener indítás, szabályos leállítás.
2. **Bedrock Network Layer:** UDP endpoint, RakNet megbízhatóság/rendezés, Bedrock frame-ek, tömörítés és titkosított session. Hibás adatot itt, még doménobjektummá alakítás előtt korlátoz.
3. **Java Network Layer:** upstream TCP kliens, VarInt frame-ek, tömörítés, opcionális titkosítás, protokollállapot-gép és reconnect nélküli fail-fast viselkedés.
4. **Session runtime:** egy Bedrock kliens és egy Java kapcsolat életciklusának tulajdonosa. Sessionönként soros mailbox biztosítja, hogy a state mutation determinisztikus legyen; a socket I/O nem blokkol.
5. **Protocol Translation Engine:** packetet irányba, protokollverzióba és sessionállapotba illő translatorhoz routol. A közvetlen packet→packet gyors út mellett kanonikus doménmodellt használ, amikor a szemantika eltér.
6. **Doménfordítók:** entity, inventory, world/chunk, player, chat és resource-pack részrendszerek. Saját mapping- és állapotcache-sel rendelkeznek, de sessionhatáron túl nem tárolnak játékállapotot.
7. **Authentication:** Bedrock login chain ellenőrzése, Java upstream identitásstratégia és a hitelesített identitás biztonságos átadása. A titkokat nem naplózza.
8. **Versioning és data registry:** támogatási mátrix, adapterválasztás, blokk/item/biome/entity mappingek és induláskori konzisztencia-ellenőrzés.
9. **Observability és admin:** metrikák, trace-k, korrelációs/session ID-k, health/readiness és kontrollált admin végpontok.
10. **Server plugin:** opcionális, hitelesített plugin channel a backend-specifikus identitás- és képességátadáshoz; a bridge alapműködése nem függ tőle.

### 2.2 Adatfolyam és concurrency

- A network event loop csak dekódol, validál és immutable eseményt tesz a session mailboxába.
- A session executor sorrendben módosítja a `SessionContext` állapotát és meghívja a translatorokat.
- CPU-igényes chunk-konverzió bounded worker poolon fut; eredménye sorszámozva kerül vissza a mailboxba.
- Minden queue véges. Túlterheléskor prioritásos backpressure lép életbe: az eldobható update-ek összevonhatók, kritikus login/inventory üzenetek nem dobhatók; tartós túlterhelés kontrollált bontást okoz.
- Egy session állapota más sessionből nem érhető el. Megosztott adat csak immutable mapping, konfiguráció és metrika.

## 3. Moduláris architektúra

| Modul | Felelősség | Közvetlen belső függőség | Fő publikus API | Kapcsolat |
|---|---|---|---|---|
| `bridge-common` | értékobjektumok, hibák, idő, utility-kontraktusok | nincs | `BridgeId`, `ProtocolVersion`, `Result`, `Clock` | minden modul minimális alapja |
| `bridge-config` | típusos konfiguráció, validáció, migráció | `common` | `BridgeConfiguration`, `ConfigurationLoader`, `ConfigurationValidator` | bootstrapnak szolgáltat immutable konfigurációt |
| `protocol-common` | packet/codec absztrakció és buffer limitek | `common` | `Packet`, `PacketCodec`, `PacketRegistry`, `ProtocolState` | mindkét protokoll alapja |
| `protocol-bedrock` | verziózott Bedrock packetek és codec-ek | `protocol-common` | `BedrockPacket`, `BedrockCodec`, `BedrockProtocolCatalog` | Bedrock network és translator használja |
| `protocol-java` | verziózott Java packetek és codec-ek | `protocol-common` | `JavaPacket`, `JavaCodec`, `JavaProtocolCatalog` | Java network és translator használja |
| `network-bedrock` | UDP/RakNet, framing, compression, crypto | `protocol-bedrock`, `common` | `BedrockListener`, `BedrockChannel`, `BedrockNetworkEvent` | session runtime felé eseményeket küld |
| `network-java` | TCP kliens, framing, compression, crypto | `protocol-java`, `common` | `JavaConnector`, `JavaChannel`, `JavaNetworkEvent` | sessionenként upstream kapcsolatot ad |
| `auth` | lánc/token ellenőrzés és identity policy | `common`, protocolmodulok | `AuthenticationManager`, `Identity`, `IdentityForwarder` | session login kapuja |
| `mapping` | verziózott registry- és mapping-adatok | `common` | `MappingRepository`, `MappingSet`, `MappingValidator` | minden doménfordító olvassa |
| `translation-core` | routing, context, pipeline, adapterválasztás | protocolmodulok, `mapping`, `common` | `PacketTranslator`, `TranslationPipeline`, `SessionContext`, `TranslatorRegistry` | doménfordítókat koordinál |
| `translation-world` | chunk, blokk, biome, dimension, world event | `translation-core`, `mapping` | `WorldTranslator`, `ChunkTranslator`, `BlockStateMapper` | play csomagokat fordít |
| `translation-entity` | entity lifecycle, runtime ID, metadata, effect | `translation-core`, `mapping` | `EntityTranslator`, `EntityTracker`, `MetadataMapper` | player/world fordítóval együttműködik |
| `translation-inventory` | item stack, window, slot, transaction | `translation-core`, `mapping` | `InventoryTranslator`, `InventoryState`, `TransactionReconciler` | authoritative állapotot egyeztet |
| `translation-player` | input, movement, abilities, teleport, action | `translation-core`, `entity` | `PlayerTranslator`, `MovementReconciler` | session és entity state-et használ |
| `translation-chat` | chat/command/system message és policy | `translation-core` | `ChatTranslator`, `CommandTranslator`, `ChatPolicy` | auth identitással és szerverrel kapcsolódik |
| `translation-resourcepack` | pack handshake és képesség-illesztés | `translation-core` | `ResourcePackTranslator`, `PackCatalog` | Bedrock kliens beléptetését vezérli |
| `session` | session-életciklus, mailbox és state machine | network, auth, translation | `SessionManager`, `BedrockSession`, `SessionFactory` | összeköti a két hálózati oldalt |
| `bridge-api` | stabil extension SPI és események | `common` | `BridgeExtension`, `BridgeEvent`, `ExtensionContext` | belső implementációt nem tesz publikussá |
| `server-plugin` | opcionális backend plugin channel | `common`; `api` csak kontraktusként | `PluginMessageCodec`, `BackendHandshake` | bridge nélkül is biztonságosan letiltható |
| `observability` | log, metric, trace, health | `common` | `BridgeMetrics`, `HealthService`, `DiagnosticContext` | keresztmetszeti integráció |
| `application` | composition root és process lifecycle | valamennyi runtime modul | `BedrockBridge`, `BridgeLauncher` | kizárólag itt történik wiring |
| `test-support` | fixture, fake clock/channel, packet DSL | publikus/SPI felületek | `SessionHarness`, `PacketFixture`, `ReplayRunner` | tesztmodulok közös eszköztára |

**Függőségi szabály:** a protocol és network modulok nem függhetnek translation modultól; a doménfordítók nem függhetnek az `application` modultól; modulok egymás belső package-eit nem importálhatják. A ciklusokat Gradle- és architecture teszt tiltja.

## 4. Tervezett repository-struktúra

```text
/
├── README.md                         # projektbelépő, státusz és támogatási mátrix
├── LICENSE
├── SECURITY.md                       # sérülékenység-bejelentési folyamat
├── CONTRIBUTING.md                   # fejlesztői és Git szabályok
├── settings.gradle.kts               # moduljegyzék
├── build.gradle.kts                  # közös build policy
├── gradle/                           # wrapper és convention plugin catalog
├── build-logic/                      # Java, teszt, publikálás convention pluginok
├── application/                      # standalone futtatható composition root
├── common/                           # bridge-common
├── config/                           # konfiguráció és séma
├── protocol/
│   ├── common/                       # packet/codec alapszerződések
│   ├── bedrock/                      # Bedrock verzióadapterek
│   └── java/                         # Java verzióadapterek
├── network/
│   ├── bedrock/                      # UDP/RakNet transport
│   └── java/                         # Java TCP transport
├── authentication/                   # identity és token policy
├── mapping/
│   ├── schema/                       # mapping formátum és validátor
│   └── data/                         # verziózott, eredetkövetett adatállomány
├── translator/
│   ├── core/
│   ├── world/
│   ├── entity/
│   ├── inventory/
│   ├── player/
│   ├── chat/
│   └── resource-pack/
├── session/                          # session state machine és koordináció
├── api/                              # stabil bővítmény API/SPI
├── server-plugin/                    # opcionális Java backend plugin
├── observability/                    # metric, trace és health
├── tests/
│   ├── compatibility/                # protokollmátrix kontrakttesztek
│   ├── integration/                  # komponensek közötti tesztek
│   ├── end-to-end/                   # valódi kliens/szerver harness
│   ├── performance/                  # JMH és terhelési forgatókönyvek
│   ├── fuzz/                         # codec és state machine fuzzing
│   └── fixtures/                     # anonimizált, licenctiszta golden adatok
├── documentation/
│   ├── PHASE-0-ARCHITECTURE.md       # jelen terv
│   ├── adr/                          # Architecture Decision Recordok
│   ├── protocol/                     # saját protokolljegyzetek és forrásjegyzék
│   ├── operations/                   # telepítés és runbook
│   └── diagrams/                     # forrásból generált diagramok
└── tools/                             # mapping-generator és diagnosztikai CLI-k
```

Minden Java modul szabványos `src/main/java`, `src/main/resources`, `src/test/java` felépítést kap. Generált forrás nem kerül kézzel módosításra; bemenete, generátora és provenance metaadata viszont verziókövetett. A `tests` könyvtár csak rendszer-szintű suite-okat tartalmaz, az egységtesztek a tulajdonos modul mellett maradnak.

## 5. Kezdeti class design

### 5.1 UML-jellegű áttekintés

```text
BedrockBridge 1 ─── 1 SessionManager
SessionManager 1 ── * BedrockSession
BedrockSession 1 ── 1 BedrockChannel
BedrockSession 1 ── 1 JavaConnection
BedrockSession 1 ── 1 SessionContext
BedrockSession 1 ── 1 TranslationPipeline
TranslationPipeline 1 ── * PacketTranslator
TranslationPipeline ─── EntityTranslator
TranslationPipeline ─── InventoryTranslator
AuthenticationManager ──> BedrockSession (login decision)
PacketTranslator ────────> MappingRepository
EntityTranslator 1 ───── 1 EntityTracker (session scoped)
InventoryTranslator 1 ── 1 InventoryState (session scoped)
```

### 5.2 Osztálykontraktusok

#### `BedrockBridge`

- **Mezők:** `BridgeConfiguration configuration`, `SessionManager sessionManager`, `BedrockListener listener`, `HealthService healthService`, `AtomicReference<LifecycleState> state`.
- **Metódusok:** `start(): CompletionStage<Void>`, `stop(ShutdownReason): CompletionStage<Void>`, `state(): LifecycleState`, `health(): BridgeHealth`.
- **Szabály:** composition root; nem tartalmaz packet- vagy játéklogikát. Az indítás idempotens állapotátmenetekkel működik.

#### `SessionManager`

- **Mezők:** `ConcurrentMap<SessionId, BedrockSession> sessions`, `SessionFactory sessionFactory`, `CapacityPolicy capacityPolicy`.
- **Metódusok:** `accept(BedrockChannel): CompletionStage<BedrockSession>`, `find(SessionId): Optional<BedrockSession>`, `disconnect(SessionId, DisconnectReason)`, `shutdown(): CompletionStage<Void>`, `snapshot(): SessionSnapshot`.
- **Kapcsolat:** a listener hívja; sessionöket létrehoz és nyilvántart, de azok belső állapotát nem módosítja.

#### `BedrockSession`

- **Mezők:** `SessionId id`, `BedrockChannel bedrockChannel`, `JavaConnection javaConnection`, `SessionContext context`, `TranslationPipeline pipeline`, `SessionMailbox mailbox`, `SessionState state`.
- **Metódusok:** `onBedrockPacket(BedrockPacket)`, `onJavaPacket(JavaPacket)`, `authenticate(LoginData)`, `transition(SessionState)`, `disconnect(DisconnectReason)`, `snapshot()`.
- **Kapcsolat:** két kapcsolat egyetlen tulajdonosa. Minden publikus esemény a mailboxon serializálódik.

#### `JavaConnection`

- **Mezők:** `JavaChannel channel`, `JavaProtocolCatalog catalog`, `JavaConnectionState state`, `CompressionSettings compression`, `UpstreamAddress remote`.
- **Metódusok:** `connect(UpstreamAddress)`, `send(JavaPacket)`, `setCompression(int)`, `enableEncryption(EncryptionContext)`, `close(DisconnectReason)`, `events()`.
- **Kapcsolat:** egy sessionhöz tartozik; a hálózati réteg absztrakciója, fordítást nem végez.

#### `PacketTranslator<S, T>`

- **Mezők:** az interface állapotmentes; az állapot a `TranslationContext` része.
- **Metódusok:** `sourceType(): Class<S>`, `supports(TranslationKey): boolean`, `translate(S, TranslationContext): TranslationResult<T>`.
- **Kapcsolat:** a `TranslatorRegistry` irány és verzió alapján választja ki; eredménye lehet nulla, egy vagy több kimenő packet és kontrollesemény.

#### `EntityTranslator`

- **Mezők:** `EntityTracker tracker`, `EntityMapping mapping`, `MetadataMapper metadataMapper`.
- **Metódusok:** `spawn(...)`, `updateMetadata(...)`, `move(...)`, `remove(...)`, `reset()`.
- **Kapcsolat:** Java UUID/entity ID és Bedrock runtime/unique ID között sessionlokális megfeleltetést tart.

#### `InventoryTranslator`

- **Mezők:** `InventoryState inventoryState`, `ItemMapper itemMapper`, `TransactionReconciler reconciler`.
- **Metódusok:** `openContainer(...)`, `applyServerUpdate(...)`, `handleClientTransaction(...)`, `closeContainer(...)`, `resynchronize()`.
- **Kapcsolat:** a Java szervert tekinti authoritative forrásnak; request/response ID-k és revisionök alapján akadályozza a duplikálást.

#### `AuthenticationManager`

- **Mezők:** `BedrockChainVerifier chainVerifier`, `JavaIdentityProvider javaIdentityProvider`, `AuthenticationPolicy policy`, `ReplayGuard replayGuard`.
- **Metódusok:** `authenticateBedrock(LoginData): CompletionStage<AuthResult>`, `prepareJavaIdentity(Identity): CompletionStage<JavaIdentity>`, `forwardIdentity(Identity, JavaConnection)`, `revoke(SessionId)`.
- **Kapcsolat:** sikeres eredmény nélkül a session nem léphet loginból configuration/play állapotba.

### 5.3 Állapotgépek és invariánsok

```text
Bedrock session:
NEW → RAKNET_CONNECTED → AUTHENTICATING → UPSTREAM_CONNECTING
    → CONFIGURING → PLAY → DISCONNECTING → CLOSED
                         ↘ FAILED → DISCONNECTING

Java connection:
DISCONNECTED → HANDSHAKE → LOGIN → CONFIGURATION → PLAY → CLOSED
```

- Tiltott átmenet programozási hiba, nem hallgatólagos no-op.
- Packet csak az adott protokollállapot registryjével dekódolható.
- A `PLAY` feltétele a sikeres Bedrock auth, Java login/configuration és mapping-kompatibilitás.
- Egy inventory tranzakció legfeljebb egyszer commitolható; egy entity runtime ID egy aktív sessionben egyértelmű.
- Bontáskor előbb az új munka felvétele áll le, majd a két csatorna és a session-erőforrások felszabadulnak.

## 6. Fejlesztési roadmap

### Phase 1 — Projektalapok

- **Cél:** reprodukálható multi-module build és minőségi kapuk.
- **Szükséges fájlok:** `settings.gradle.kts`, gyökér `build.gradle.kts`, `build-logic/`, Gradle wrapper, modul buildfájlok, `README.md`, `LICENSE`, `CONTRIBUTING.md`, CI workflow, alap `BridgeConfiguration` és logging bootstrap.
- **Nehézség:** alacsony–közepes.
- **Tesztelés:** wrapper/build smoke test Java 21-en; modulhatár-teszt; formatter, static analysis, dependency lock/verification; minimális alkalmazás-életciklus teszt.
- **Kilépési feltétel:** tiszta checkoutból egy paranccsal buildelhető, nincs gameplay vagy network funkció.

### Phase 2 — Protokoll- és hálózati alap

- **Cél:** limitált, robusztus Bedrock UDP/RakNet listener és Java TCP kliens; képes kézfogásig eljutni.
- **Szükséges fájlok:** protocol codec/registry osztályok, verziókatalógusok, `network/bedrock`, `network/java`, buffer/timeout konfiguráció, packet fixture-ek.
- **Nehézség:** magas, különösen RakNet reliability, ordering és MTU miatt.
- **Tesztelés:** codec round-trip/golden teszt; fragmentáció, reorder, loss és timeout szimuláció; malformed packet fuzzing; loopback integrációs teszt; heap/allocation benchmark.
- **Kilépési feltétel:** támogatott verzió felismerhető, státusz és determinisztikus kapcsolatbontás működik terhelés alatt.

### Phase 3 — Hitelesítés és session-életciklus

- **Cél:** ellenőrzött Bedrock identitás, konfigurálható Java upstream login és teljes állapotgép.
- **Szükséges fájlok:** `AuthenticationManager`, verifier/provider/policy osztályok, `SessionManager`, `BedrockSession`, `JavaConnection`, secret konfiguráció és auth tesztvektorok.
- **Nehézség:** magas; kriptográfia, külső szolgáltatás és biztonságos identity forwarding.
- **Tesztelés:** érvényes/lejárt/hibás aláírás; replay; clock skew; auth service timeout; állapotgép property teszt; titok-redakció logteszt; offline mód explicit opt-in teszt.
- **Kilépési feltétel:** csak ellenőrzött, policy szerint engedélyezett session jut tovább; minden hiba tisztán bont és auditálható.

### Phase 4 — Fordítási mag és verziózás

- **Cél:** kétirányú pipeline, kanonikus modell, mapping repository és első támogatott 1.21.x protokollpár login/configuration fordítása.
- **Szükséges fájlok:** `TranslationPipeline`, registry/context/result, mapping schema/data/generator, verzióadapterek, ADR-ek és compatibility suite.
- **Nehézség:** magas.
- **Tesztelés:** packet routing contract; mapping teljesség/injektivitás ahol elvárt; ismeretlen mező/packet policy; cross-version golden replay; adapter isolation teszt.
- **Kilépési feltétel:** támogatási mátrix géppel ellenőrzött, inkompatibilis verzió érthető hibával elutasított.

### Phase 5 — Gameplay támogatás

- **Cél:** játszható vertikális szelet, majd részrendszerenként növekvő lefedettség.
- **Szükséges fájlok:** world/chunk, entity, player, inventory, chat és resource-pack translatorok; trackerek; mapping adatok; E2E harness.
- **Nehézség:** nagyon magas; a szemantikai különbségek és állapotszinkron dominálnak.
- **Tesztelés:** prioritás szerint (1) spawn/movement/chunk, (2) block interaction, (3) inventory/container, (4) combat/entity metadata, (5) chat/command és dimension change; replay/golden, valódi szerver E2E, hosszú soak és differenciális invariáns-teszt. Tesztelendő a teleport ack, chunk határ, reconnect, halál/respawn és inventory resync.
- **Kilépési feltétel:** dokumentált feature-mátrix kritikus útjai hiba nélkül működnek, adatduplikáció vagy state leak nélkül.

### Phase 6 — API, plugin és üzemeltethetőség

- **Cél:** stabil, minimális extension SPI, opcionális backend plugin és production observability.
- **Szükséges fájlok:** `api/`, `server-plugin/`, event model, plugin channel schema, metric/health/tracing, operations dokumentáció és mintakonfiguráció.
- **Nehézség:** közepes–magas.
- **Tesztelés:** API binary compatibility; rosszindulatú plugin message; backend plugin nélküli működés; metric cardinality; readiness és graceful shutdown integráció.
- **Kilépési feltétel:** az API verziózott, a plugin opcionális, dashboard/runbook rendelkezésre áll.

### Phase 7 — Production stabilizáció és release

- **Cél:** biztonságos, mérhető és visszagörgethető stabil kiadás.
- **Szükséges fájlok:** hardening beállítások, threat model, SBOM, release automation, container/service példák, migration/runbook, támogatási mátrix és changelog.
- **Nehézség:** magas.
- **Tesztelés:** több száz/méretezési cél szerinti konkurens session load test; 24–72 órás soak; fuzz kampány; dependency és secret scan; CPU/memória regressziós budget; chaos (packet loss, upstream restart, auth outage); külső security review.
- **Kilépési feltétel:** SLO-k és release checklist teljesülnek, nincs nyitott kritikus sérülékenység, rollback próbált.

Az implementáció minden fázis előtt külön jóváhagyást igényel. A Phase 1 sem kezdődik el a jelen Phase 0 elfogadása előtt.

## 7. Technikai döntések

### 7.1 Java 21

- hosszú támogatási idejű (LTS) alap és modern JVM teljesítmény/diagnosztika;
- recordok, sealed típusok és pattern matching jól modellezik az immutable packeteket és zárt állapotgépeket;
- virtual thread használható blokkoló külső műveleteknél, miközben a hálózati event loop külön marad;
- erős tooling, JFR/JMH és Minecraft Java ökoszisztéma-kompatibilitás.

Nem használunk preview feature-t stabil modulban. A heapen kívüli/network buffer életciklus explicit, és teljesítménydöntést mérés alapján hozunk.

### 7.2 Gradle Kotlin DSL

A típustámogatott build script, convention plugin és version catalog egységesíti a sok modult. Dependency locking és verification biztosítja a reprodukálhatóságot. A `build-logic` elkerüli a gyökérscript másolását, a Gradle Wrapper pedig rögzíti az eszközverziót.

### 7.3 Moduláris architektúra

A protokoll, transport és gameplay változási üteme eltér. Az izoláció csökkenti a regressziós felületet, kikényszeríti a publikus szerződéseket, külön benchmarkolhatóvá teszi a hot pathot és lehetővé teszi egy verzióadapter célzott cseréjét. Kezdetben egy repository és egy processz marad: nem vezetünk be hálózati microservice-határokat indok nélkül.

### 7.4 Verziókompatibilitás

- A kapcsolat elején pontos Bedrock és Java protocol ID alapján `TranslationKey` választ adapterpárt.
- A támogatott párok explicit mátrixban szerepelnek; nincs „best effort” ismeretlen verzióra.
- A közös szemantika kanonikus modellben él, a huzaleltérés verziómodulban.
- Patch-adapter örökölhet immutable közös mappinget, de eltérését deklarálnia és tesztelnie kell.
- A Java upstream verziót konfiguráció és handshake eredmény együtt ellenőrzi. Nem támogatott kombináció még világba lépés előtt bont.
- Új verzió felvétele: protokoll-diff → ADR/impact → codec/mapping → golden fixture → compatibility és E2E kapu.

### 7.5 Packet-változások kezelése

Minden packet definíció `(edition, protocolVersion, state, direction, packetId)` kulccsal regisztrált. A codec-ek szigorú mezősorrendet, méret- és kollekciólimitet alkalmaznak. Az opcionális/új mezőket feature flag vagy verziózott codec kezeli, nem szétszórt verzió-`if` ág. Ismeretlen kritikus packet kontrollált bontást, ismert és deklaráltan figyelmen kívül hagyható packet mérőszámot eredményez. A generált codec/mapping csak validált sémából készül, kézi override pedig indoklással és teszttel engedett.

### 7.6 További döntések

- **I/O:** event-driven, nem blokkoló hálózat; a konkrét transport library ADR-ben, mérési spike után választandó.
- **Belső modell:** immutable parancsok/események; mutable state kizárólag session scope-ban.
- **Hibakezelés:** típusos protokoll- és policy-hibák; kliensnek sanitizált ok, logban korrelációs ID.
- **Konfiguráció:** fail-fast sémaellenőrzés; titok environment/secret providerből, nem repositoryból.
- **API-stabilitás:** csak `bridge-api` publikus; belső package-ek változhatnak. SemVer és binary compatibility ellenőrzés.

## 8. Kockázatelemzés

| Kockázat | Típus / hatás | Mérséklés és ellenőrzés |
|---|---|---|
| Hiányos vagy gyorsan változó protokollismeret | technikai, magas | explicit támogatási mátrix, saját capture/replay, golden fixture, kis adapterek, korai protokoll-spike |
| RakNet loss/reorder/fragmentáció hibái | technikai, magas | szimulált hálózat, property/fuzz teszt, bounded retransmit és szigorú timeout |
| Chunk-konverzió CPU- és allocation-költsége | teljesítmény, magas | profilozás/JFR/JMH, immutable mapping cache, bounded worker pool, prioritás/backpressure; cache csak mért haszonnal |
| Sok session memóriaigénye vagy leak | teljesítmény, magas | session budget, bounded queue/cache, leak detector, soak test, bontási invariánsok |
| Event loop blokkolása | teljesítmény, magas | blokkoló hívások külön executoron, latency metric és watchdog, code review szabály |
| Patch verzió eltérő ID/registry | kompatibilitás, magas | pontos handshake, startup mapping-validáció, páronkénti CI-mátrix, fail-closed |
| Java plugin/mod egyedi viselkedése | kompatibilitás, közepes | capability negotiation, dokumentált limit, extension API; ismeretlen custom payload alapból izolált/tiltott |
| Bedrock–Java mechanikai eltérés | kompatibilitás, magas | authoritative upstream, reconciliation, dokumentált fallback és feature-mátrix |
| Hamisított login, token replay | biztonság, kritikus | aláírás/lánc/audience/time ellenőrzés, nonce/replay cache, TLS a külső authhoz, fail-closed |
| UDP amplification és packet flood | biztonság/DoS, kritikus | cookie/handshake validáció, IP/session rate limit, pre-auth byte budget, maximális packet/fragment/collection méret |
| Parser crash vagy memóriakimerítés | biztonság, kritikus | defensive codec, fuzzing, nesting/length limit, minimális pre-auth allocation |
| Identity forwarding meghamisítása | biztonság, kritikus | opcionális plugin channel kölcsönös hitelesítése, rövid életű aláírt envelope, replay protection; megbízható hálózati határ |
| Titkok vagy személyes adat naplózása | biztonság/jogi, magas | strukturált allowlist logging, token redakció, minimális retention, hozzáférés-szabályozás |
| Supply-chain sérülékenység/licenc | biztonság/jogi, magas | dependency verification/locking, SBOM, licenc-allowlist, rendszeres scan és provenance |
| Nem kontrollált queue-növekedés | rendelkezésre állás, magas | minden queue bounded, prioritás és overload bontás, queue-depth riasztás |
| Upstream vagy auth kiesés | rendelkezésre állás, közepes | szigorú timeout/circuit breaker, érthető disconnect, health jelzés; nincs végtelen retry login közben |

Kiadás előtt külön threat-model workshop szükséges trust boundarykkel, abuse case-ekkel és adatmegőrzési döntésekkel. Kriptográfiai primitívet nem implementálunk saját kezűleg.

## 9. Coding standard

### 9.1 Java és package-ek

- gyökér package: `io.bedrockbridge`; modul/domén szerint például `io.bedrockbridge.protocol.bedrock`;
- publikus API kizárólag `io.bedrockbridge.api` alatt; `internal` package nem használható modulon kívül;
- package név kisbetűs, egyes számú doménfogalom; wildcard import és ciklikus függés tilos;
- Java 21, UTF-8, 4 szóköz, 120 karakteres célzott sorhossz; formatter automatikusan ellenőrzi;
- osztály/interface `UpperCamelCase`, metódus/mező `lowerCamelCase`, konstans `UPPER_SNAKE_CASE`;
- interface neve képességet ír le, nincs `I` prefix; implementáció csak valódi alternatívák esetén kap technológiai suffixet;
- packet record immutable; `null` helyett explicit optionalitás vagy üres kollekció; mutable globális állapot tilos;
- import körül `try/catch` soha nem használható; kivételt csak ott kezelünk, ahol helyreállítás vagy kontextusadás történik;
- hálózati input minden hosszát/enumját/állapotát validálni kell; logban token, kulcs és teljes személyes payload tilos.

### 9.2 Dokumentáció

- minden publikus API-hoz Javadoc: szerződés, thread-safety, ownership, hibák és `@since`;
- nem triviális protokollmező mellett verzió és saját dokumentációs hivatkozás;
- architekturális, biztonsági, dependency- vagy formátumdöntéshez számozott ADR;
- diagram szöveges forrásból generált; feature- és compatibility-mátrix minden release-ben frissül;
- komment a döntés „miért”-jét magyarázza, nem a kódot ismétli.

### 9.3 Teszt- és minőségi szabályok

- változó fordítási szabályhoz kötelező unit/contract teszt és szükség szerint golden fixture;
- hibajavításhoz előbb reprodukáló regressziós teszt;
- idő, véletlen és I/O injektálható; teszt nem függhet valódi külső szolgáltatástól a külön E2E suite-on kívül;
- kritikus codec és state machine fuzz/property tesztet kap;
- lefedettség önmagában nem cél: a kritikus invariánsok és irány/version ágak lefedése release gate.

### 9.4 Commitok és review

- Conventional Commits: `type(scope): imperative summary`, legfeljebb kb. 72 karakteres tárgy;
- engedett fő típusok: `feat`, `fix`, `docs`, `test`, `refactor`, `perf`, `build`, `ci`, `chore`, `security`;
- példák: `feat(protocol): add packet registry`, `fix(network): resolve connection timeout`, `docs(architecture): record version adapter policy`;
- egy commit egy koherens változás; generált fájl bemenetével/generátorával együtt kerül be;
- review nélkül nincs merge; protocol/security változáshoz domain owner jóváhagyás; minden CI-kapu kötelező;
- breaking API változás `!` jelölést és `BREAKING CHANGE` törzset kap.

## 10. Git workflow

### 10.1 Branch modell

- **`main`:** védett, mindig kiadható; csak ellenőrzött pull request vagy release merge kerülhet rá. Release tag: aláírt `vMAJOR.MINOR.PATCH`.
- **`development`:** védett integrációs ág a következő kiadáshoz. Feature PR-ek célja; rendszeresen szinkronizáljuk `main`-nel. Stabilizáció után release PR megy innen `main`-re.
- **`feature/<issue>-<slug>`:** rövid életű ág `development` ágról, például `feature/142-packet-registry`.
- **`fix/<issue>-<slug>`:** nem sürgős javítás `development` ágról.
- **`hotfix/<issue>-<slug>`:** production hiba esetén `main` ágról; merge `main`-re, majd kötelező visszamerge `development`-be.
- **`release/<version>`:** csak szükség esetén rövid stabilizáció; új feature nem kerülhet rá.

### 10.2 Pull request folyamat

1. Issue/ADR rögzíti a célt és acceptance criteria-t.
2. Kis, review-zható commitok készülnek; a szerző rebase-eli a célágra.
3. PR sablon tartalmazza: összefoglaló, kockázat, tesztek, compatibility/security hatás, rollback és dokumentáció.
4. CI futtat formatot, compile/unit/integration tesztet, modulhatár- és kompatibilitás-ellenőrzést, dependency/licenc/secret scant; releváns változásnál benchmark/fuzz/E2E is kötelező.
5. Legalább egy jóváhagyás, kritikus területnél CODEOWNERS jóváhagyás szükséges.
6. Alapértelmezett merge mód squash, a végső üzenet Conventional Commit. Release/hotfix történeténél indokolt merge commit megengedett.
7. `main` merge után automatizált artifact, checksum, SBOM, changelog és aláírt tag készül; telepítés fokozatos/canary, dokumentált rollbackkel.

### 10.3 Verziózás

A bridge SemVer-t használ. A támogatott Minecraft protokollok nem a bridge verziószámai, hanem a release artifact metaadatában és a támogatási mátrixban szerepelnek. Mapping vagy adapter javítása patch release lehet; publikus API-törés major release. Minden artifact buildje reprodukálható és visszaköthető commit SHA-hoz.

## 11. Phase 0 elfogadási kapu

Az architektúra akkor tekinthető elfogadottnak, ha a tulajdonosok jóváhagyták:

- a hatókört és a kezdeti feature-/verziómátrix elvét;
- a clean-room és licenc/provenance követelményeket;
- a modulhatárokat, session concurrency modellt és állapotgépeket;
- az online/offline auth és identity forwarding biztonsági policy-ját;
- a roadmap kilépési feltételeit, a teljesítménycélok későbbi számszerűsítésének módját;
- a Git-, review-, release- és kompatibilitási folyamatot.

**Stop pont:** ez a dokumentum lezárja a Phase 0-t. Sem Phase 1 scaffold, sem hálózati vagy fordítási implementáció nem kezdhető meg kifejezett architektúra-jóváhagyás előtt.
