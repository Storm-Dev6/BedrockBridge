# ADR-0004: Determinisztikus registry generator

- **Státusz:** Proposed
- **Dátum:** 2026-07-16

## Kontextus

A block/item/biome/entity registry nagy, verziófüggő és kézi karbantartással hibára hajlamos.

## Döntés

Offline, saját inputokból dolgozó generátort készítünk normalizált registry IR-rel, szemantikus validációval, stabil rendezéssel és checksum manifesttel. `BlockRegistry`, `ItemRegistry`, `BiomeRegistry`, `EntityRegistry` immutable O(1) lookup API-t ad. Build közben nincs hálózati letöltés.

## Következmények

Reprodukálható artifact és gépi diff készül, de a parser adaptereket verziónként karban kell tartani. Generált output kézi módosítása CI-hiba.

## Elvetett alternatívák

- kézzel írt Java táblák: rosszul review-zhatók;
- induláskori online lekérés: nem reprodukálható és availability kockázat;
- reflection/dinamikus registry: gyengébb startup validáció és teljesítmény.
