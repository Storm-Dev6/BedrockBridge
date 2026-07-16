# ADR-0003: Saját mapping formátum és provenance

- **Státusz:** Proposed
- **Dátum:** 2026-07-16

## Kontextus

Runtime ID-k, propertyk és metadata sémák edition/verzió szerint eltérnek. Más projektek mappingje nem használható.

## Döntés

Saját YAML + verziózott JSON Schema authoring formátumot és determinisztikus bináris runtime artifactot használunk. Elsődleges kulcs a canonical namespaced key. Minden inputhoz checksum, eredet, licenc, eszköz- és játékverzió tartozik. Külső bridge mapping importja tilos.

## Következmények

Auditálható clean-room adatvonal és review-zható diff jön létre, viszont saját adatgyűjtés és validátor szükséges. Hiány explicit `unsupported` vagy review-zott fallback; implicit találgatás nincs.

## Elvetett alternatívák

- harmadik fél mapping konvertálása: provenance/licenc és függetlenségi kockázat;
- runtime ID mint stabil kulcs: verziók között instabil;
- programozható mapping script: nem determinisztikus és nehezen auditálható.
