# BedrockBridge — Phase 0.5 Engineering Specification

**Státusz:** review-ra váró műszaki specifikáció
**Előfeltétel:** elfogadott Phase 0 architektúra
**Korlát:** ez a dokumentum nem engedélyez implementációt; Phase 1 csak a 0.5 elfogadási kapu után indulhat

## 1. Cél, alapelvek és megfelelőség

Ez a specifikáció a BedrockBridge 1.21.x hálózati és fordítási részrendszereinek implementálható szerződése. A „MUST”, „SHOULD” és „MAY” szavak rendre kötelező, ajánlott és opcionális követelményt jelölnek. A konkrét protocol ID-k és kriptográfiai paraméterek a verziókatalógus validált adatai lesznek, nem kerülnek hardcode-olásra ebben a tervben.

Alapelvek:

- saját RakNet state machine és saját mapping/registry adatmodell készül;
- más bridge/proxy projekt mappingfájlját, generált registryjét vagy forráskódját nem importáljuk;
- minden hálózati hossz, index, állapotátmenet és erőforrás-foglalás korlátos;
- a Java szerver authoritative; a kliensből érkező szándékot fordítjuk, nem fogadunk el kliensoldali állapotot tényként;
- protokollverzió és packet state/direction minden codec-választás része;
- a mérési célok release gate-ek, de kontrollált baseline mérés után ADR-rel finomíthatók.

## 2. Saját RakNet implementáció

### 2.1 Rétegek és komponensek

```text
UDP DatagramEndpoint
  → OfflineHandshake (ping/pong, open-connection, cookie/rate policy)
  → RakNetFrameCodec (datagram sequence, frame flags, bounds)
  → ReliabilityEngine (ACK/NACK, resend, deduplication)
  → SplitPacketAssembler (bounded fragment reassembly)
  → OrderingEngine (channel/order/sequence semantics)
  → BedrockBatchCodec
```

Fő belső kontraktusok: `RakNetPeer`, `ReceiveWindow`, `SendWindow`, `AckRangeSet`, `RecoveryQueue`, `RttEstimator`, `MtuPolicy`, `OrderingChannel`, `SplitPacketAssembler`. Ezek network-internal típusok; a session csak teljes, validált Bedrock batch-et lát.

### 2.2 Megbízhatósági módok

A frame codec a huzalon jelzett reliability típust zárt enumként kezeli:

| Mód | Delivery | Duplicate | Sorrend | Követett mező |
|---|---|---|---|---|
| unreliable | best effort | lehetséges | nincs | nincs |
| unreliable sequenced | best effort | régi eldobható | monoton csatornánként | sequence + order index/channel |
| reliable | újraküldött | kiszűrt | nincs | reliable message index |
| reliable ordered | újraküldött | kiszűrt | szigorú | reliable + order index/channel |
| reliable sequenced | újraküldött | kiszűrt | csak legújabb | reliable + sequence + order/channel |

Nem támogatott/reserved reliability érték malformed packet. A 24 bites triad sorszámok összehasonlítása wrap-aware fél-intervallumos aritmetikát használ; nyers egész összehasonlítás tilos. A receive window kiszűri a duplikátumot, korlátozza a túl távoli future indexet, és csak teljes/reassembled frame után ad át payloadot.

### 2.3 ACK/NACK

- Minden elfogadott datagram sequence bekerül az ACK accumulatorba; a hiányok csak a legmagasabb megfigyelt sequence és a receive window alapján lesznek NACK-ok.
- Range encoding előtt az indexek rendezettek és összevontak. Egy control datagram rekord- és byte-száma konfigurált maximum alatt marad; a maradék következő datagramba kerül.
- ACK eltávolítja a datagramhoz tartozó recovery entryt és RTT mintát ad, kizárva az újraküldött mintát (Karn-szabály).
- NACK az érintett, még élő recovery entryt azonnali újraküldésre jelöli, de ugyanarra az entryre minimum pacing intervallum vonatkozik.
- ACK/NACK ismeretlen vagy már lezárt sequence-re idempotens no-op plusz alacsony kardinalitású metrika; ablakon kívüli tömeges rekord abuse score-t növel.

### 2.4 Fragmentálás és reassembly

- Küldéskor a payload a választott MTU-ból az UDP/IP, RakNet datagram- és frame-overhead levonása után darabolódik. Minden fragment saját reliability frame-ben utazik azonos split ID-val, deklarált `splitCount` és egyedi `splitIndex` mellett.
- Fogadáskor kötelező: `1 ≤ splitCount ≤ maxFragments`, `0 ≤ splitIndex < splitCount`, pozitív teljes payload budget és konzisztens reliability/order metaadat.
- A kulcs peer + split ID + reliability/order identitás; split ID önmagában nem elegendő.
- Duplikált, azonos fragment idempotens; eltérő tartalmú duplikátum elutasítja az assemblyt. Completion csak minden index pontos meglétekor történik.
- Sessionönként korlátos az aktív assemblyk száma és összbyte-ja. Határidő után az assembly törlődik; LRU eviction csak metrikával, kritikus nyomásnál kapcsolatbontással.
- A tömörítés dekódolása kizárólag teljes batch reassembly után következik.

### 2.5 MTU kezelés

- A listener konfigurált `minMtu`, `preferredMtu`, `maxMtu` tartományt alkalmaz. Az open-connection próba eredménye nem lépheti túl sem a beérkezett datagram bizonyított méretét, sem a szerver maximumát.
- IPv4/IPv6 overhead külön számítandó; nincs IP fragmentationre építő működés. Production default konzervatív, útvonalváltozásnál a peer nem emelhet önkényesen MTU-t.
- Túlméretes bejövő datagram még mély dekódolás előtt eldobandó. Ismétlődése rate-limit/bontás alapja.
- Küldéskor minden encoder előre számol, majd a kész datagram méretét assertion/metric ellenőrzi. MTU black-hole esetén timeout és kisebb MTU-val egyszeri, korlátozott renegotiation engedhető, végtelen fallback nem.

### 2.6 Ordering channel és sequencing

- A csatornák száma fix, konfiguráció által nem növelhető protokollmaximum fölé. Minden channel saját `expectedOrderIndex`, reorder buffert és `latestSequenceIndex` értéket tart.
- Ordered frame: régi index duplicate; várt index azonnal átadható, majd a folytonos buffered sor ürül; jövőbeli index csak bounded distance és byte budget mellett pufferelhető.
- Sequenced frame: ugyanazon order epochban csak a wrap-aware legújabb sequence adható át; régebbi eldobandó. Új order index új sequence epochot nyit.
- A reliable index a deduplikációt, az ordering index az átadási sorrendet szolgálja; a kettőt nem szabad összekeverni.
- Channel starvation ellen round-robin ürítés és session mailbox budget működik, miközben egy channelen belül a sorrend sérthetetlen.

### 2.7 Resend, RTT és congestion policy

- Minden reliability-t igénylő kiküldött datagram immutable recovery entryt kap: sequence, encoded bytes/frame refs, első/utolsó küldés, retry count és deadline.
- RTT estimator Jacobson/Karels jellegű `SRTT`/`RTTVAR`; `RTO = clamp(SRTT + 4×RTTVAR, minRto, maxRto)`. Újraküldéskor exponenciális backoff és jitter alkalmazandó.
- NACK gyors retransmitet kér; timer timeout a kieső ACK-ot kezeli. Egy logical frame egyszerre csak egy aktív resend döntés tárgya lehet.
- `maxRetries`, session recovery-byte budget és absolute lifetime kötelező. Kimerüléskor kontrollált timeout disconnect történik.
- Küldési pacing és AIMD-szerű congestion window védi a socketet; retransmit nem előzheti korlátlanul a kritikus új control forgalmat. Kezdeti konstansokat loss/latency benchmarkkal kell kalibrálni.

### 2.8 RakNet tesztkapu

Determinista virtual clockkal tesztelendő: wrap-around, range merge, loss/reorder/duplicate, ACK loss, NACK storm, fragment collision/timeout, összes reliability mód, több ordering channel, IPv4/IPv6 MTU, resend exhaustion. Property test igazolja, hogy ordered payload sosem cserél sorrendet és reliable payload legfeljebb egyszer kerül felfelé. Coverage-guided fuzz célpont minden offline/frame/control decoder.

## 3. Bedrock protokoll

### 3.1 Packetmodell és codec

A packet kulcsa `BedrockProtocolVersion + connectionState + direction + packetId`. A packet immutable record; a codec kézi vagy saját sémából generált, de minden esetben explicit endian/VarInt/string/array limiteket használ. A batch feldolgozás sorrendje: reassembly → decrypt/authenticate → decompress → batch length framing → packet decode → state validation. A kimenő út ennek fordítottja.

`BedrockPacketRegistry` verziónként immutable és induláskor ellenőrzi a duplikált ID/típus párokat. Trailing byte csak packet-specifikus explicit szabállyal engedett. Ismeretlen packet policy: login/configuration alatt fail-closed; play alatt csak katalógusban `safeToIgnore` jelöléssel hagyható el.

### 3.2 Login chain

1. A login packet protocol ID-ját a listener támogatási mátrixa ellenőrzi.
2. A JWT lánc struktúrája, elemszáma, tokenmérete és JSON mélysége limitált.
3. Minden aláírás és kulcskapcsolat szabványos crypto providerrel ellenőrzendő; az elvárt root/trust policy konfigurált és verziózott.
4. Kötelező claim policy: issuer/trust chain, audience ahol alkalmazható, `nbf`/`exp` clock skew-val, identity kulcs, title identity és szükséges extra-data mezők.
5. A client-data token aláírását a láncból származó identity key ellenőrzi. A skin/cosmetic payload külön size/type validációt kap, és nem válik autorizációs adattá.
6. A token fingerprint + nonce/időablak bekerül a `ReplayGuard`-ba. Ugyanaz a bizonyíték második aktív loginhoz nem használható.
7. Csak immutable, minimalizált `AuthenticatedBedrockIdentity` kerül tovább; nyers token nem naplózható és auth után nem marad hosszú életű state-ben.

Az offline/development policy külön indítási flaget, warningot és loopback/trusted network korlátozást kíván; production profilban nem lehet implicit fallback.

### 3.3 Titkosítás

- A login chain által hitelesített publikus kulccsal és friss ephemeral kulcspárral történik a session key agreement; a pontos curve/KDF/cipher és handshake packet a kiválasztott Bedrock verzió adapterének része.
- CSPRNG generálja az ephemeral kulcsot és tokent; kulcs-újrahasználat tilos. Shared secretből domain-separated KDF vezet le kulcsot/IV-anyagot.
- A handshake proof ellenőrzése constant-time összehasonlítást használ. Titkosított packet az encryption-ready átmenet előtt, plaintext packet utána protokollsértés.
- Counter/nonce monotonicitás és overflow kötelezően ellenőrzött; auth/tag/hash hiba fail-closed disconnect, oracle-szerű részletes klienshiba nélkül.
- Kulcsanyag csak session scope-ban él, log/dump redakcióval és bontáskori referenciatörléssel. Saját kriptográfiai primitív implementálása tilos.

### 3.4 Tömörítés

- A compression algorithm és threshold a támogatott verzió capability-jéből/handshake-ből származik; algoritmus nem találgatható.
- Dekódolás streaming, `maxCompressedBatchBytes`, `maxDecompressedBatchBytes`, ratio és packet-count limittel. Zip bomb gyanú azonnal bont.
- Dekódolás után a teljes input elfogyasztása és a batch frame-határok ellenőrzendők. Hibás streamből nincs részleges packetátadás.
- Encoder csak threshold felett tömörít, ha az egyeztetett algoritmus engedi; algoritmusonként golden és adversarial fixture szükséges.

### 3.5 Resource pack flow

Állapotgép: `INFO_SENT → CLIENT_RESPONSE → STACK_SENT → DOWNLOADING/APPLYING → COMPLETED`, bármely pontból `DECLINED/FAILED`. A session csak sikeres/engedélyezett pack policy után indulhat játékba.

- A `PackCatalog` saját manifestből ad UUID-t, verziót, SHA-256 hash-t, méretet, kötelező/opcionális flaget és tartalomforrást.
- Kliens által kért pack/chunk csak a meghirdetett katalógusból, index- és méretellenőrzéssel szolgálható ki; path input nem érhet fájlrendszert.
- Chunk response mérete bounded, letöltés session- és IP-szinten rate-limited. Hash/size eltérés deployment hiba és readiness failure.
- Ismételt/out-of-order response idempotens vagy state violation; policy szerint sanitizált disconnect.

## 4. Java protokoll

### 4.1 Keretezés és állapot

A Java packet kulcsa `JavaProtocolVersion + state + direction + packetId`. TCP streamen VarInt frame length, opcionális compression envelope, majd packet ID/payload következik. A VarInt byte-szám, frame length, string/array/NBT mélység és teljes allocation előre limitált. A connection state machine egyetlen session mailboxban vált állapotot.

### 4.2 Handshake

- A bridge kliensként pontos upstream protocol ID-t, sanitizált host routing adatot, portot és `LOGIN` next state-et küld; státuszlekérdezés külön rövid életű kapcsolat.
- A konfigurált verzió és az upstream által ténylegesen jelzett viselkedés eltérése fail-fast kompatibilitási hiba.
- Host mezőbe identity adat csak külön, hitelesített server-plugin protokoll esetén kerülhet; hagyományos string forwarding alapból tiltott.

### 4.3 Login

Folyamat: login start → opcionális encryption request/response → opcionális compression → login plugin/cookie policy → login success → acknowledged/configuration. Minden opcionális üzenet verzióadapter és capability alapján kezelt.

- Online upstreamhez a `JavaIdentityProvider` jogszerű, támogatott credential/session stratégiát használ; nincs hitelesítésmegkerülés.
- Plugin request csak allowlist channelen, bounded payload mellett válaszolható. Ismeretlen kötelező request login failure.
- Compression state az azt bekapcsoló packet utáni következő frame-től érvényes, pontos mailbox-sorrenddel.
- Login success UUID/name egyeztetendő az identity policy-val, mielőtt downstream play identity készül.

### 4.4 Configuration

A configuration állapot registry data, feature flags, tags, known packs és egyéb verziófüggő konfigurációs packetek fogadója. A `JavaRegistrySnapshotBuilder` tranzakciósan épít immutable snapshotot: csak finish előtt végzett teljességi és mapping compatibility ellenőrzés után publikálható. Resource-pack/policy válasz és client information a Bedrock capabilityből származik. Configuration↔play visszalépés csak az adott Java adapter által deklarált átmenettel lehetséges.

### 4.5 Play

- A play packetek domain routerhez kerülnek: world, entity, inventory, player, chat vagy control.
- Keepalive azonnali prioritású, ID-korrelált és timeoutolt; teleport/config ack nem veszhet el backpressure miatt.
- Custom payload alapból drop/reject policy; csak méretkorlátos allowlist adapter vagy hitelesített saját plugin channel engedett.
- Disconnect packet sanitizált üzenetté fordul; TCP EOF/protocol error determinisztikusan lezárja a Bedrock sessiont.

### 4.6 Compression és encryption

Java compressionnél a threshold után minden frame tartalmaz uncompressed-length VarIntet. Nulla érték nyers payloadot jelent; nem nulla értéknek el kell érnie a thresholdot és a dekompresszált pontos méretet. Mindkét méret és ratio bounded.

Online-mode encryption requestnél server ID/public key/verify token validált. CSPRNG session secret készül, a verify token és secret a protokoll által előírt publikus kulcsos csomagolással küldendő, majd a stream cipher pontos frame-határok között aktiválódik. Kulcs vagy token soha nem logolható. A konkrét session-service hívás timeoutot, circuit breakert, redakciót és tesztelhető provider interfészt kap.

## 5. Translation Engine

### 5.1 Canonical model

A canonical model nem egy harmadik protokoll teljes másolata, hanem minimális, edition-semleges doménfogalmak halmaza:

- `CanonicalKey(namespace, value)`, `CanonicalDimension`, `CanonicalPosition/Rotation`;
- `CanonicalBlockState(key, properties)`, `CanonicalItemStack(key, count, components)`;
- `CanonicalEntity(typeKey, identity, pose, attributes, metadata)`;
- `CanonicalBiome`, `CanonicalChunkSection`, `CanonicalChunk`;
- `PlayerIntent`, `InventoryIntent`, `ChatIntent` és szerveroldali authoritative update-ek;
- `CapabilitySet` jelöli a veszteséges/hiányzó platformképességeket.

Minden modell immutable, egységei dokumentáltak, numerikus konverziója overflow/NaN-safe. Edition-specifikus runtime ID nem szivároghat át a modellen; azt session-scoped edge mapping tartja. Lossy fordítás explicit `TranslationNotice`-t és mérőszámot ad, nem csendes találgatást.

### 5.2 Packet pipeline

```text
decoded packet → state/direction guard → normalize → domain command/event
→ session state mutation → map/capability policy → encode target packet(s)
→ priority/backpressure queue → target channel
```

A `TranslationResult` kimenő packeteket, state mutationt, ack/control actiont és notice/error elemet tartalmaz. A pipeline egy packet feldolgozását session szinten atomi döntésként kezeli: validációs hiba esetén nincs részleges state commit. Direkt packet→packet adapter csak bizonyítottan szemantikaazonos, állapotmentes esetben engedett. Reentrancy tilos; async worker eredmény sequence tokennel tér vissza.

### 5.3 Entity pipeline

1. Java spawn/type/UUID validálása és canonical entity létrehozása.
2. `EntityTracker` kiosztja a sessionlokális Bedrock unique/runtime ID-t, ütközés- és overflow-védelemmel.
3. Type mapping, metadata schema, attribute/effect/pose konverzió.
4. Spawn packet(ek) csak a szükséges world/chunk és player előfeltételek után.
5. Update-ek coalescingje megengedett, de spawn/remove, mount, teleport és critical metadata sorrendje nem.
6. Remove atomikusan felszabadítja a mappinget; késői update generation/epoch ellenőrzéssel eldobandó.

Ismeretlen entityhez verziózott fallback policy (helyettesítő vizuális típus vagy elrejtés) szükséges; interakció nem irányítható téves Java entityre.

### 5.4 Inventory pipeline

A `InventoryState` ablakonként authoritative Java revisiont, canonical slotokat, cursor state-et és függő Bedrock requesteket tart. Folyamat: Bedrock request strukturális validáció → item/network ID mapping → precondition/revision check → `InventoryIntent` → Java click/action packet → szerver update/ack → commit vagy teljes resync.

- Kliens által küldött stack count/NBT/component nem írhatja felül a tracked authoritative stacket.
- Minden request ID idempotency cache-be kerül; ismétlés ugyanazt az eredményt adja, nem duplikál műveletet.
- Timeout, reject vagy revision mismatch lezárja a pending tranzakciót és célzott/teljes resyncet kér.
- Creative action, crafting, anvil, merchant és speciális container külön capability/policy adapter; támogatás hiányában biztonságos reject.
- Ablakbezárás, halál, dimension change és disconnect minden pending state-et determinisztikusan takarít.

### 5.5 Chunk pipeline

1. Java registry snapshot és dimension schema kiválasztása.
2. Chunk section palette/block state → canonical key/property → Bedrock runtime ID mapping.
3. Biome palette, height/light és block entity normalizálás; NBT csak allowlist mezőkkel és mélységlimittel.
4. Bedrock subchunk/palette formátum és verziófüggő world packetek előállítása.
5. Immutable eredmény cache opcionálisan `(source hash, mapping version, target protocol, dimension)` kulccsal; player-specifikus adat nem cache-elhető.
6. Worker eredmény csak akkor küldhető, ha session generation, dimension és chunk request epoch változatlan.

Chunk prioritás távolság/nézet alapján működik, bounded pending workkal. Unload megelőzheti és érvénytelenítheti a késői buildet. Benchmark kötelező worst-case palette, üres és block-entity-heavy chunkra.

## 6. Saját mapping rendszer

### 6.1 Forrás és provenance

Mapping kizárólag saját, dokumentált adatgyűjtésből készül: jogszerű vanilla kliens/szerver megfigyelés, általunk írt probe output, nyilvánosan engedélyezett specifikáció és kézzel review-zott szabály. Minden source artifact SHA-256, eszközverzió, játékverzió, dátum, licenc/eredet és generátor commit metaadatot kap. Külső bridge/proxy mappingjének konvertálása vagy összehasonlításból átemelése tilos.

### 6.2 Formátum

Saját, review-barát YAML a szerkesztési forrás, verziózott JSON Schema-val; buildkor determinisztikus bináris index készül. Logikai példa:

```yaml
schemaVersion: 1
mappingVersion: 1.21.2+1
sourceVersion: { edition: java, protocol: 768 }
targetVersion: { edition: bedrock, protocol: 748 }
provenance: { manifest: provenance/1.21.2.json }
blocks:
  minecraft:oak_log:
    properties:
      axis: { x: pillar_axis_x, y: pillar_axis_y, z: pillar_axis_z }
    fallback: minecraft:oak_planks
items:
  minecraft:example:
    target: minecraft:example
    componentPolicy: [damage, custom_name, enchantments]
```

Valós fájlok doménenként shardoltak (`blocks`, `items`, `biomes`, `entities`) és stable canonical namespaced keyt használnak; runtime ID sosem a kézi mapping elsődleges kulcsa. A séma támogatja: exact alias, property transform, enum/numerikus transform, component/metadata szabály, fallback, `unsupported` indok és érvényességi verziótartomány. Turing-complete script a mappingben tilos.

### 6.3 Validáció és build artifact

- schema, unique key, target-létezés, property domain/range, fallback ciklus és verziótartomány ellenőrzés;
- teljességi report: mapped/fallback/unsupported/ambiguous, release budgettel;
- round-trip invariáns ott, ahol a mapping losslessnek jelölt;
- canonical serialization és rendezés garantálja a reprodukálható hash-t;
- bináris artifact header: magic, schema version, source/target protocol, payload hash és generator version;
- runtime csak teljesen validált immutable `MappingSet`-et publikál; hot reload első kiadásban nincs.

## 7. Registry rendszer és saját generátor

### 7.1 Pipeline

```text
owned raw observations + version manifest
→ parser adapters → normalized registry IR
→ structural/semantic validators → deterministic sort
→ Java source/resource + compatibility report + checksum manifest
```

A `registry-generator` külön CLI/build tool. Hálózatról nem tölt build közben; minden input rögzített és checksumolt. Azonos inputnak byte-azonos outputot kell adnia. Generált fájl fejlécében input hash és generator verzió szerepel; kézi szerkesztés CI hibát okoz.

### 7.2 Registry kontraktusok

- **`BlockRegistry`:** canonical key, edition runtime/state ID, property schema és valid kombinációk, default state, hardness/interaction szempontból szükséges flag-ek. Ellenőrzi az egyedi runtime ID-t és minden state teljes property-kombinációját.
- **`ItemRegistry`:** canonical key, runtime ID, max stack, durability és támogatott data component capability. Block-item kapcsolat opcionális, de referenciálisan valid.
- **`BiomeRegistry`:** canonical key, runtime ID és a fordításhoz szükséges dimension/environment attribútumok. ID és key egyedi.
- **`EntityRegistry`:** canonical key, type ID, metadata schema index/típus/default, attribute capability és spawn kategória. Metadata index verziófüggő, nem canonical identitás.

Publikus olvasási API mindegyiknél: `byKey`, `byRuntimeId`, `require...`, `entries`, `version`, `fingerprint`; lookup O(1), registry immutable. A generator `RegistryDiff` reportja added/removed/changed/ID-remap/schema-change kategóriát ad, amely adapter- és mapping review-t indít.

### 7.3 Generator tesztkapu

Golden input/output, determinism két külön workspace-ben, schema mutation, duplikáció, dangling reference, diff classification és nagy registry performance teszt kötelező. A teljes generálás nem módosíthat tracked fájlt tiszta checkoutban (`generate` + Git diff gate).

## 8. 1.21.x verziókezelés

### 8.1 Modell

Nem marketing verziósztring, hanem editionönkénti protocol ID az elsődleges kulcs. A `VersionCatalog` hozzárendeli a `1.21.0`, `1.21.1`, `1.21.2`, … címkéket a pontos ID-hoz, registry fingerprinthez és feature flag-ekhez. Egy `TranslationProfile` konkrét Bedrock–Java párt, mapping artifactot és adapterkészletet köt össze.

Három változási réteg:

1. **adatváltozás:** új ID/block/item → új registry/mapping data, kódváltozás nélkül;
2. **strukturális packet-változás:** verziózott codec/packet adapter, közös doméntranslator változatlan;
3. **szemantikai változás:** capability és célzott domain strategy, ADR-rel és cross-version teszttel.

Adapterlánc: pontos verzióadapter → azonos packet-layoutú deklarált családadapter → közös canonical translator. Automatikus „nearest version” fallback tilos. Minor/patch delta osztály csak változásokat deklarál, de compositiont használunk mély öröklés helyett.

### 8.2 Új 1.21.x verzió felvétele

1. Saját probe/capture és provenance manifest rögzítése.
2. Registry generator és `RegistryDiff` futtatása.
3. Packet catalog/codec diff és state transition impact review.
4. Mapping delta létrehozása; teljességi és fallback report.
5. Új `VersionDescriptor` és csak szükséges delta adapterek.
6. Golden codec, replay, minden támogatott pár compatibility matrix és legalább egy E2E scenario.
7. Dokumentált feature gap, benchmark regresszió és security review.

Egy verzió támogatási státusza: `experimental`, `supported`, `deprecated`, `removed`. Startup csak `supported` profilt enged alapból; experimental explicit flag. A CI legalább minden támogatott Bedrock verziót az engedett Java párokkal tesztel, pairwise optimalizálás csak közös fingerprint bizonyítása után.

## 9. Benchmark- és kapacitáscélok

### 9.1 Referenciakörnyezet

Minden szám egyetlen bridge processzre értendő: Linux x86-64, Java 21, 4 dedikált modern CPU mag, 4 GiB heap, 1 Gbit/s LAN, 20 ms szimulált RTT, vanilla upstream, 10 chunk view distance. Külön közöljük idle, movement és chunk-stream workloadot; p50/p95/p99 és confidence interval szerepel. A Minecraft szerver TPS-e nem a bridge által előállított érték, ezért upstream kontrollméréshez viszonyítunk.

### 9.2 Release gate célok

| Mutató | Cél |
|---|---|
| Process alap memória | ≤ 256 MiB RSS warm idle állapotban |
| Idle session többlet | ≤ 2 MiB/session p95 |
| Aktív session többlet | ≤ 8 MiB/session p95, 10 chunk view workload mellett |
| Heap leak | 24 órás soak után, azonos loadra normalizálva ≤ 2% retained növekedés |
| CPU idle | 500 kapcsolt idle sessionnél ≤ 0.5 dedikált core átlag |
| CPU aktív | 100 aktív sessionnél ≤ 3.2/4 core átlag, rövid peak ≤ 90% total |
| Fordítási latency | nem-chunk packet bridge p95 ≤ 2 ms, p99 ≤ 5 ms |
| Chunk pipeline latency | chunk p95 ≤ 20 ms worker idő, queue nélkül |
| Hálózati overhead | bridge hozzáadott RTT p95 ≤ 5 ms LAN-on, chunk burst nélkül |
| TPS-hatás | upstream 20 TPS fenntartva; p95 tick time romlás ≤ 5% az azonos Java bot baseline-hoz képest |
| Session limit | 500 idle és 100 aktív session stabil; hard cap konfigurált, alapérték 100 |
| Loss recovery | 1% random UDP loss mellett p99 interaktív hozzáadott latency ≤ 150 ms, session leak nélkül |

A TPS és CPU cél nem keverhető: upstream server és bridge külön processz/metrika. Benchmark JMH micro (VarInt, codec, mapping, palette), deterministic pipeline macro, loopback integration, network impairment és 24–72 órás soak rétegekből áll. CI PR gate max 10% regressziót, nightly referencia max 5%-ot enged az elfogadott baseline-hoz képest; zajos mérés warning, három egymást követő reprodukált eltérés blocker.

## 10. Security specification

### 10.1 Trust boundary és alapállás

A Bedrock UDP internet felől teljesen megbízhatatlan; a Java upstream és server plugin csak konfigurált címen/hitelesített csatornán megbízható; mapping/build input supply-chain boundary. Minden parser fail-closed, least allocation first és explicit state guard elvet követ. Klienshiba általános, belső log strukturált és secretmentes.

### 10.2 DoS és rate limiting

- Többlépcsős token bucket: globális datagram/byte, source-prefix, IP és authenticated session. IPv6 prefix aggregáció konfigurálható; bucketek száma bounded/expiring.
- Pre-auth limit szigorúbb: concurrent handshake/IP, packet/s, byte/s, JWT munka/s és teljes pre-auth memória. Drága crypto csak olcsó méret/state/cookie ellenőrzés után.
- Kapacitásnál új session elutasítás; meglévő session queue nem nőhet. UDP válasz byte-ja validált cím előtt nem haladhatja meg lényegesen a kérését (anti-amplification).
- Per-domain budget: split assembly, reorder buffer, recovery queue, compressed/decompressed batch, NBT, chunk job, pending inventory és resource-pack bandwidth.
- Abuse score fokozatos: drop → throttling → sanitizált disconnect → rövid TTL deny; tartós IP ban nem automatikus alapfunkció.

### 10.3 Packet validation és malformed kezelés

Validációs sorrend: datagram size/source → frame flags/length → sequence window → fragment bounds → crypto integrity → compression bounds → packet frame → state/direction/ID → field semantic rules → domain authorization. Integer overflow előtt checked arithmetic; allocation csak validált hosszból. UTF-8, enum, koordináta, finite float, collection, NBT depth/node és string limitek kötelezők.

Hibakategória: `DROP_SILENT` (hamis/árva UDP), `DROP_AND_SCORE`, `DISCONNECT_PROTOCOL`, `DISCONNECT_SECURITY`, `INTERNAL_FAILURE`. Parser exception nem juthat event loop tetejére és nem okozhat process crash-t; nyers payload alapból nem logolható. Metrika reason code alacsony kardinalitású, stack trace csak belső hibánál. Fuzz crash vagy unbounded allocation release blocker.

### 10.4 Replay protection

- Login token fingerprint/nonce + identity + lejárat bounded TTL cache-ben, atomikus put-if-absent művelettel.
- RakNet reliable/sequence receive window kiszűri a packet replayt és wrap-aware; ablakon kívüli régi frame nem nyithat új assemblyt.
- Encryption counter/nonce monoton és sessionhöz kötött; session key újracsatlakozáskor új.
- Server-plugin identity envelope tartalmaz session ID-t, audience-t, issued/expiry időt és nonce-t, aláírt/MAC-elt; backend egyszer fogadja el.
- Inventory request ID és Java keepalive/teleport ack session epochhoz kötött idempotency cache-t használ.

### 10.5 Security verification

Threat model és abuse-case review minden új trust boundaryhez; SAST, dependency/secret/license scan, SBOM és artifact provenance minden release-ben. Fuzz corpus regresszió, auth negative suite, rate-limit load, compression bomb, fragment exhaustion, replay és log-redaction teszt kötelező. Kritikus crypto/auth változást két reviewer és külső security review nélkül nem adunk ki.

## 11. Observability és hibakereshetőség

Kötelező metrikák: session state/count, datagram/packet/byte irányonként, ACK/NACK/retransmit/RTT/loss, queue depth/drop, split assembly, codec/mapping error, translation latency, chunk jobs/cache, auth result és rate-limit action. Labelként protocol family/version és reason code használható, player ID/IP/packet ID korlátlanul nem.

Minden session véletlen korrelációs ID-t kap. Debug packet trace csak explicit, időkorlátos admin engedéllyel, payload-redakcióval és bounded ring bufferben működhet. Readiness false, ha mapping/registry fingerprint hibás, listener nem él vagy kötelező upstream dependency tartósan elérhetetlen; egyetlen játékos hibája nem teszi automatikusan unreadyvé a processt.

## 12. Phase 0.5 elfogadási és stop kapu

Phase 0.5 csak akkor fogadható el, ha review során jóváhagyták:

- a RakNet reliability, ACK/NACK, fragment, ordering, MTU és recovery invariánsokat;
- a Bedrock/Java auth, encryption, compression és state machine trust boundaryket;
- a canonical model és az entity/inventory/chunk authoritative viselkedést;
- a saját mappingformátum provenance szabályát és registry generator reprodukálhatóságát;
- az 1.21.x adapter/delta modellt és compatibility CI-mátrixot;
- a számszerű benchmark baseline-t, mérőkörnyezetet és release gate-eket;
- a DoS, validáció, malformed és replay policy-t;
- a kapcsolódó ADR-eket, valamint minden `Nyitott kérdés` lezárását vagy tulajdonos/határidő hozzárendelését.

**STOP:** Phase 1 projekt-scaffold, production kód, packet codec, mapping adatgyűjtő vagy generator implementáció csak a Phase 0 és Phase 0.5 dokumentált elfogadása után kezdhető. Az elfogadásig kizárólag terv, ADR, threat model és mérési protokoll pontosítható.

## 13. Nyitott kérdések az elfogadás előtt

1. A támogatási mátrix első pontos Bedrock és Java protocol ID párjai saját discovery spike után rögzítendők.
2. A konkrét Java network library választása benchmark és licenc-review tárgya; a saját RakNet logika nem delegálható kész RakNet implementációra.
3. Bedrock cipher/KDF/compression verzióparamétereit saját, provenance-elt protokollkutatás után kell adapteradatként jóváhagyni.
4. A 100 aktív/500 idle kapacitáscél termékigénnyel egyeztetendő; eltérés ADR-0008 frissítést igényel.
5. Offline upstream production engedélyezhetősége és a server-plugin trust deployment modellje security owner döntésére vár.

## 14. Kapcsolódó ADR-ek

- [ADR-0001: Saját RakNet transport](adr/0001-own-raknet-transport.md)
- [ADR-0002: Canonical translation model](adr/0002-canonical-translation-model.md)
- [ADR-0003: Saját mapping formátum és provenance](adr/0003-owned-mapping-format.md)
- [ADR-0004: Determinisztikus registry generator](adr/0004-deterministic-registry-generator.md)
- [ADR-0005: Delta-alapú protocol versioning](adr/0005-delta-protocol-versioning.md)
- [ADR-0006: Session mailbox concurrency](adr/0006-session-mailbox-concurrency.md)
- [ADR-0007: Fail-closed security és bounded erőforrások](adr/0007-fail-closed-bounded-security.md)
- [ADR-0008: Mérhető performance budget](adr/0008-performance-budget.md)
- [ADR-0009: Authoritative inventory reconciliation](adr/0009-authoritative-inventory.md)
- [ADR-0010: Stabil publikus API, belső modulhatárok](adr/0010-api-boundary.md)
