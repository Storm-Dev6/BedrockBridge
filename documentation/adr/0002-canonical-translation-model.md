# ADR-0002: Canonical translation model

- **Státusz:** Proposed
- **Dátum:** 2026-07-16

## Kontextus

A két edition packetje és játékmodellje nem feleltethető meg egy az egyben, és patch verziónként változik.

## Döntés

Minimális, immutable, edition-semleges canonical doménmodellt használunk entity, item, block, biome, chunk és intent fogalmakkal. Közvetlen packet→packet út csak bizonyítottan állapotmentes, szemantikaazonos esetben engedett. Minden veszteséges konverzió explicit policy/notice.

## Következmények

A doménlogika újrahasznosítható verziók között, jól tesztelhető, de extra objektum/allocation és modellkarbantartás keletkezik. Hot path optimalizáció csak benchmark után, a szemantika megőrzésével.

## Elvetett alternatívák

- minden verziópárhoz direkt fordító: kombinatorikus növekedés;
- Java modellt canonicalként használni: Bedrock szándékok és képességek torzulnának;
- dinamikus map-alapú modell: gyenge típus- és invariánsvédelem.
