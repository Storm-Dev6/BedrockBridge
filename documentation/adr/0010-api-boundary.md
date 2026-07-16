# ADR-0010: Stabil publikus API és belső modulhatárok

- **Státusz:** Proposed
- **Dátum:** 2026-07-16

## Kontextus

Extensionök szükségesek lehetnek, de a protokoll belső típusai és adapterei gyorsan változnak 1.21.x verziókkal.

## Döntés

Csak a `bridge-api` modul dokumentált immutable event/SPI típusai publikusak és SemVer-védettek. Network, codec, mapping és session implementáció internal; extension nem tarthat buffer ownershipot és nem blokkolhat mailboxot. Binary compatibility CI-kapu.

## Következmények

Kisebb, stabil surface és biztonságosabb refaktorálás, de kevesebb alacsony szintű pluginlehetőség. Új hook csak use case, lifecycle/threading és security review után kerül API-ba.

## Elvetett alternatívák

- minden modul exportálása: kompatibilitási csapda;
- reflection/mixin extension: nem auditálható;
- API teljes elhagyása: backend integrációk forkot igényelnének.
