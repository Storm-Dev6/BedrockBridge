# ADR-0005: Delta-alapú protocol versioning

- **Státusz:** Proposed
- **Dátum:** 2026-07-16

## Kontextus

Az 1.21.x kiadások adat-, packet-layout- és szemantikai változásai eltérő mértékűek. Teljes fork minden patchre fenntarthatatlan.

## Döntés

Protocol ID alapú `VersionDescriptor`, immutable katalógus és konkrét Bedrock–Java `TranslationProfile` választódik. Közös family codec/translator composition mellett a verzió csak deltát deklarál. Adatváltozás mapping/registry artifact, layout-változás codec adapter, szemantikai változás capability strategy. Ismeretlen verzióra nincs nearest fallback.

## Következmények

Kisebb változás kevés kódot érint, de pontos diff/provenance és compatibility matrix szükséges. Az adapterlánc induláskor validált.

## Elvetett alternatívák

- egyetlen codec verzió-`if` ágakkal: gyorsan átláthatatlan;
- teljes modulmásolat patchenként: duplikáció;
- automatikus legközelebbi verzió: csendes protokollkorrupció.
