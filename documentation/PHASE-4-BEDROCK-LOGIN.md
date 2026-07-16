# Phase 4 — Bedrock Login

## Státusz és hatókör

A Phase 4 review-ra vár. A rendszer a Phase 3 transportkapcsolat után hitelesíti a Bedrock login chain-t és client-data JWT-t, létrehozza az encryption handshake-et, majd kizárólag kliens acknowledgement után enged titkosított forgalmat. Nem emulál Microsoft/Xbox Live szolgáltatást, és nem kerül meg hitelesítést.

## Modulok

| Modul | Felelősség |
|---|---|
| `bedrock-auth` | strict JSON, JWT, chain trust, claim policy, identity és replay guard |
| `bedrock-crypto` | ECDSA formátum, P-384 ECDH, handshake JWT és packet cipher |
| `bedrock-codec` | negotiated bounded compression |
| `bedrock-login` | authentication session state és crypto aktiválás |

## Trust és chain ellenőrzés

A `BedrockChainVerifier` kizárólag konstruktorban átadott P-384 trust rootot fogad el. Nincs hálózati kulcsletöltés és nincs implicit/offline fallback. A compact JWT pontosan három Base64URL komponens, maximum 256 KiB/token és kizárólag `ES384`. A chain legfeljebb nyolc token és összesen legfeljebb 1 MiB.

Az első token `x5u` kulcsának egyeznie kell egy pinned roottal. Minden token aláírását az aktuális kulcs ellenőrzi, majd a hitelesített `identityPublicKey` lesz a következő láncszem kulcsa. Minden chain token kötelező `nbf`/`exp` claimet kap konfigurált clock skew-val. A client-data JWT-t a végső identity key írja alá; csak ezután válik immutable auth eredménnyé.

A minimalizált identity mezők: UUID, display name, XUID, title ID és identity public key. A raw token nem része az eredménynek. A teljes proof SHA-256 fingerprintje bounded TTL replay cache-be kerül atomikus `putIfAbsent` művelettel.

## JSON és claim biztonság

A saját strict parser maximum 1 MiB inputot és 32 nesting szintet enged, elutasítja a trailing adatot, duplikált object keyt, hibás escape-et, kontrollkaraktert és szintaktikailag hibás számot. Az auth réteg explicit típust követel minden security-releváns claimhez; stringgé alakítás vagy laza coercion nincs.

## Encryption handshake

1. A chain sikeres ellenőrzése után friss `secp384r1` ephemeral server key készül CSPRNG-ből.
2. A server private key és a hitelesített client identity public key ECDH shared secretet ad.
3. Friss 16 byte-os salt készül; `SHA-256(salt || sharedSecret)` adja az AES-256 keyt.
4. A server ES384 JWT headerében a server public key, payloadjában a salt szerepel.
5. A cipher elkészül, de `AWAITING_CLIENT_HANDSHAKE` állapotban nem használható.
6. A kliens handshake acknowledgement után az állapot `AUTHENTICATED`, ekkor engedélyezett encrypt/decrypt.

A packet cipher `AES/CFB8/NoPadding`, az IV a session key első 16 byte-ja. Minden irány külön monotonic 64 bites countert tart. A clear payload után nyolc byte kerül: `SHA-256(counterLE || payload || key)` első nyolc byte-ja. Decrypt `MessageDigest.isEqual` ellenőrzést használ; hiba, truncation vagy counter exhaustion fail-closed.

## Compression

A `CompressionSettings` immutable algorithm, threshold, compressed/decompressed maximum és ratio limit. A támogatott algoritmus `NONE` vagy JDK zlib. Az encoder threshold alatt defensive copyt ad. A decoder streaming módon ellenőrzi az abszolút outputot és a tömörítési arányt, továbbá elutasítja az incomplete streamet, dictionary igényt és trailing compressed adatot.

## Authentication state machine

```text
AWAITING_LOGIN
 → AWAITING_CLIENT_HANDSHAKE   chain/client-data verified, challenge sent
 → AUTHENTICATED              client handshake acknowledged
 → CLOSED                     session cleanup

bármely verification/crypto hiba → FAILED
```

Az API tiltja az ismételt login próbát, a korai cipher használatot és az acknowledgement ismétlését. `close()` eldobja a cipher és identity referenciákat.

## Tesztek

Az unit/integration tesztek lefedik a duplicate-key JSON elutasítást, replay egyszeri admissiont, P-384 ECDH szimmetriát, AES/CFB8 authenticated round-tripet, handshake JWT alakját, zlib round-tripet, valamint egy saját tesztkulcsokkal felépített teljes pinned-root chain → challenge → client ECDH → encrypted packet folyamatot.

## Kizárások és stop pont

Nincs resource pack, play, inventory, entity, chunk, command vagy text feldolgozás; nincs Java protokoll és nincs translation. **Phase 5 külön, explicit jóváhagyás nélkül nem kezdhető.**
